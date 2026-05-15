package com.chatterbox.chat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatterbox.chat.entity.Conversation;
import com.chatterbox.chat.entity.User;
import com.chatterbox.chat.repository.ConversationRepository;
import com.chatterbox.chat.repository.MessageRepository;
import com.chatterbox.chat.repository.UserRepository;
import com.chatterbox.chat.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:chatterbox-stomp-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "CHATTERBOX_JWT_SECRET=integration-test-signing-secret-that-is-long-enough"
})
class StompSecurityIntegrationTest {
    private static final String SECRET = "integration-test-signing-secret-that-is-long-enough";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository users;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    private WebSocketStompClient stompClient;
    private User alice;
    private User bob;
    private User carol;
    private Conversation aliceBob;
    private Conversation bobCarol;
    private Conversation aliceCarol;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        alice = createUser("alice-" + UUID.randomUUID() + "@example.test");
        bob = createUser("bob-" + UUID.randomUUID() + "@example.test");
        carol = createUser("carol-" + UUID.randomUUID() + "@example.test");
        aliceBob = createConversation(alice, bob);
        bobCarol = createConversation(bob, carol);
        aliceCarol = createConversation(alice, carol);
    }

    @Test
    void acceptsValidJwtAndRejectsMissingInvalidAndExpiredTokens() throws Exception {
        StompSession session = connect(jwtService.issue(alice.getEmail()));
        assertTrue(session.isConnected());
        disconnect(session);

        assertThrows(Exception.class, () -> connect(null));
        assertThrows(Exception.class, () -> connect("not-a-jwt"));
        String expired = Jwts.builder()
                .subject(alice.getEmail())
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThrows(Exception.class, () -> connect(expired));
    }

    @Test
    void messageSenderComesFromPrincipalNotSpoofedPayload() throws Exception {
        StompSession session = connect(jwtService.issue(alice.getEmail()));
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        subscribeAndAwait(session, alice.getEmail(), messageTopic(aliceBob), stringFrame(received));
        send(session, "/app/chat.send", Map.of(
                "conversationId", aliceBob.getId().toString(),
                "senderEmail", bob.getEmail(),
                "body", "identity is taken from the token",
                "messageType", "TEXT"));

        String event = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(alice.getEmail(), objectMapper.readTree(event).get("senderEmail").asText());
        var saved = messages.findByConversationIdOrderByCreatedAtAsc(aliceBob.getId());
        assertEquals(1, saved.size());
        assertEquals(alice.getId(), saved.get(0).getSender().getId());
        disconnect(session);
    }

    @Test
    void memberCanSendAndNonMemberCannotSendToPrivateConversation() throws Exception {
        StompSession session = connect(jwtService.issue(alice.getEmail()));
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        subscribeAndAwait(session, alice.getEmail(), messageTopic(aliceBob), stringFrame(received));
        send(session, "/app/chat.send", Map.of(
                "conversationId", aliceBob.getId().toString(), "body", "member message"));
        assertNotNull(received.poll(5, TimeUnit.SECONDS));
        assertEquals(1, messages.findByConversationIdOrderByCreatedAtAsc(aliceBob.getId()).size());

        StompSession outsider = connect(jwtService.issue(alice.getEmail()));
        send(outsider, "/app/chat.send", Map.of(
            "conversationId", bobCarol.getId().toString(), "body", "should be rejected"));
        assertFalse(conversations.existsByIdAndParticipantEmail(bobCarol.getId(), alice.getEmail()));
        StompSession bobSession = connect(jwtService.issue(bob.getEmail()));
        BlockingQueue<String> bobConversationEvents = new LinkedBlockingQueue<>();
        subscribeAndAwait(bobSession, bob.getEmail(), messageTopic(bobCarol), stringFrame(bobConversationEvents));
        send(bobSession, "/app/chat.send", Map.of(
            "conversationId", bobCarol.getId().toString(), "body", "authorized barrier"));
        assertNotNull(bobConversationEvents.poll(5, TimeUnit.SECONDS));
        var savedToBobConversation = messages.findByConversationIdOrderByCreatedAtAsc(bobCarol.getId());
        assertEquals(1, savedToBobConversation.size());
        assertEquals(bob.getId(), savedToBobConversation.get(0).getSender().getId());
        disconnect(session);
        disconnect(outsider);
        disconnect(bobSession);
    }

    @Test
    void conversationSubscriptionsArePrivateAndPresenceUsesAuthenticatedIdentity() throws Exception {
        StompSession aliceSession = connect(jwtService.issue(alice.getEmail()));
        BlockingQueue<String> ownConversationEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> foreignConversationEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> presenceEvents = new LinkedBlockingQueue<>();
        subscribeAndAwait(aliceSession, alice.getEmail(), messageTopic(aliceBob), stringFrame(ownConversationEvents));
        subscribeAndAwait(aliceSession, alice.getEmail(), "/topic/presence", stringFrame(presenceEvents));

        StompSession bobSession = connect(jwtService.issue(bob.getEmail()));
        send(bobSession, "/app/chat.send", Map.of(
                "conversationId", aliceBob.getId().toString(), "body", "visible to member"));
        assertNotNull(ownConversationEvents.poll(5, TimeUnit.SECONDS));

        StompSession carolSession = connect(jwtService.issue(carol.getEmail()));
        send(bobSession, "/app/chat.send", Map.of(
            "conversationId", bobCarol.getId().toString(), "body", "not for alice"));
        assertNull(foreignConversationEvents.poll(500, TimeUnit.MILLISECONDS));

        send(aliceSession, "/app/presence", Map.of("email", bob.getEmail(), "online", true));
        String presence = presenceEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(presence);
        assertEquals(alice.getEmail(), objectMapper.readTree(presence).get("email").asText());

        StompSession intruder = connect(jwtService.issue(alice.getEmail()));
        intruder.subscribe(messageTopic(bobCarol), stringFrame(foreignConversationEvents));
        long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (intruder.isConnected() && System.nanoTime() < closeDeadline) Thread.sleep(20);
        assertFalse(intruder.isConnected(), "A rejected private subscription must close its STOMP session");
        send(bobSession, "/app/chat.send", Map.of(
            "conversationId", bobCarol.getId().toString(), "body", "private topic remains private"));
        assertNull(foreignConversationEvents.poll(500, TimeUnit.MILLISECONDS));

        disconnect(aliceSession);
        disconnect(bobSession);
        disconnect(carolSession);
        disconnect(intruder);
    }

    @Test
    void rejectsGlobalPrivateTopicsButAllowsAuthenticatedPresenceSubscription() throws Exception {
        StompSession session = connect(jwtService.issue(alice.getEmail()));
        BlockingQueue<String> presenceEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> globalEvents = new LinkedBlockingQueue<>();
        subscribeAndAwait(session, alice.getEmail(), "/topic/presence", stringFrame(presenceEvents));
        send(session, "/app/presence", Map.of("online", true));
        assertNotNull(presenceEvents.poll(5, TimeUnit.SECONDS));

        session.subscribe("/topic/messages", stringFrame(globalEvents));
        StompSession bobSession = connect(jwtService.issue(bob.getEmail()));
        send(bobSession, "/app/chat.send", Map.of(
            "conversationId", aliceBob.getId().toString(), "body", "not globally broadcast"));
        assertNull(globalEvents.poll(500, TimeUnit.MILLISECONDS));
        disconnect(session);
        disconnect(bobSession);
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-chat",
            new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler stringFrame(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    queue.offer(objectMapper.writeValueAsString(payload));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    private void subscribeAndAwait(
            StompSession session, String email, String destination, StompFrameHandler handler)
            throws InterruptedException {
        session.subscribe(destination, handler);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SimpUser user = simpUserRegistry.getUser(email);
            if (user != null && user.getSessions().stream()
                    .flatMap(stompSession -> stompSession.getSubscriptions().stream())
                    .anyMatch(subscription -> destination.equals(subscription.getDestination()))) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(false, "SUBSCRIBE was not registered for " + destination);
    }

    private void send(StompSession session, String destination, Map<String, ?> payload) throws Exception {
        session.send(destination, payload);
    }

    private void disconnect(StompSession session) {
        if (session.isConnected()) session.disconnect();
    }

    private String messageTopic(Conversation conversation) {
        return "/topic/conversations/" + conversation.getId() + "/messages";
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.substring(0, email.indexOf('@')));
        user.setPasswordHash("not-used-in-this-test");
        return users.save(user);
    }

    private Conversation createConversation(User first, User second) {
        Conversation conversation = new Conversation();
        conversation.setUserOne(first);
        conversation.setUserTwo(second);
        return conversations.save(conversation);
    }
}
