package com.chatterbox.chat.security;

import com.chatterbox.chat.repository.ConversationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile(
            "^/topic/conversations/([0-9a-fA-F-]{36})/(messages|typing)$");

    private final JwtService jwtService;
    private final ConversationRepository conversations;
    private final ObjectMapper objectMapper;

    public StompJwtChannelInterceptor(
            JwtService jwtService,
            ConversationRepository conversations,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) throw denied("Invalid STOMP frame");
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticateConnect(accessor);
        } else if (command == StompCommand.SEND) {
            authorizeSend(message, accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        } else if (command != null && command != StompCommand.DISCONNECT
                && command != StompCommand.UNSUBSCRIBE) {
            requireAuthentication(accessor);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.size() != 1) {
            throw denied("A bearer token is required");
        }
        String header = headers.get(0);
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7
                || header.substring(7).isBlank()) {
            throw denied("A valid bearer token is required");
        }
        try {
            var claims = jwtService.parse(header.substring(7));
            String email = claims.getSubject();
            Date expiration = claims.getExpiration();
            if (email == null || email.isBlank() || expiration == null || !expiration.after(new Date())) {
                throw denied("The bearer token has no user identity");
            }
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    email, null, List.of());
            authentication.setDetails(expiration);
            accessor.setUser(authentication);
        } catch (RuntimeException exception) {
            throw denied("Invalid or expired bearer token");
        }
    }

    private void authorizeSend(Message<?> message, StompHeaderAccessor accessor) {
        Authentication authentication = requireAuthentication(accessor);
        String destination = accessor.getDestination();
        if ("/app/presence".equals(destination)) return;
        if (!"/app/chat.send".equals(destination) && !"/app/chat.typing".equals(destination)) {
            throw denied("Sending to this destination is not allowed");
        }

        UUID conversationId = conversationIdFromPayload(message.getPayload());
        if (!conversations.existsByIdAndParticipantEmail(conversationId, authentication.getName())) {
            throw denied("Conversation access denied");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Authentication authentication = requireAuthentication(accessor);
        String destination = accessor.getDestination();
        if ("/topic/presence".equals(destination)) return;

        Matcher matcher = destination == null ? null : CONVERSATION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw denied("Subscription to this destination is not allowed");
        }
        UUID conversationId;
        try {
            conversationId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw denied("Invalid conversation destination");
        }
        if (!conversations.existsByIdAndParticipantEmail(conversationId, authentication.getName())) {
            throw denied("Conversation access denied");
        }
    }

    private UUID conversationIdFromPayload(Object payload) {
        try {
            JsonNode root;
            if (payload instanceof byte[] bytes) {
                root = objectMapper.readTree(bytes);
            } else if (payload instanceof String text) {
                root = objectMapper.readTree(text);
            } else {
                throw denied("Invalid message payload");
            }
            JsonNode id = root == null ? null : root.get("conversationId");
            if (id == null || !id.isTextual()) throw denied("Conversation is required");
            return UUID.fromString(id.asText());
        } catch (MessageDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw denied("Invalid conversation identifier");
        }
    }

    private Authentication requireAuthentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication && authentication.isAuthenticated()) {
            if (!(authentication.getDetails() instanceof Date expiration) || !expiration.after(new Date())) {
                throw denied("The bearer token has expired");
            }
            return authentication;
        }
        throw denied("Authentication is required");
    }

    private MessageDeliveryException denied(String message) {
        return new MessageDeliveryException(message);
    }
}