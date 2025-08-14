package com.chatterbox.chat.controller;

import com.chatterbox.chat.entity.Message;
import com.chatterbox.chat.repository.ConversationRepository;
import com.chatterbox.chat.repository.MessageRepository;
import com.chatterbox.chat.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatWebSocketController {
    public record ChatMessage(UUID conversationId, String senderEmail, String body, String messageType) {}
    public record MessageEvent(UUID id, UUID conversationId, String senderEmail, String body, Instant createdAt) {}
    public record TypingEvent(UUID conversationId, String senderEmail, boolean isTyping, Instant sentAt) {}
    public record PresenceRequest(String email, boolean online) {}
    public record PresenceEvent(String email, String displayName, String avatarUrl, boolean online, Instant sentAt) {}

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;

    public ChatWebSocketController(ConversationRepository conversations, MessageRepository messages, UserRepository users, SimpMessagingTemplate messaging) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.messaging = messaging;
    }

    @MessageMapping("/chat.send")
    public void send(ChatMessage request) {
        var conversation = conversations.findById(request.conversationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        var sender = users.findByEmailIgnoreCase(request.senderEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sender not found"));
        if (!conversation.getUserOne().getId().equals(sender.getId()) && !conversation.getUserTwo().getId().equals(sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        }
        if (request.body() == null || request.body().isBlank()) return;
        var message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.body().trim());
        var saved = messages.save(message);
        var recipientId = conversation.getUserOne().getId().equals(sender.getId()) ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();
        conversation.incrementUnreadFor(recipientId);
        conversations.save(conversation);
        messaging.convertAndSend("/topic/conversations/" + conversation.getId() + "/messages",
                new MessageEvent(saved.getId(), conversation.getId(), sender.getEmail(), saved.getContent(), saved.getCreatedAt()));
        messaging.convertAndSend("/topic/messages",
            new MessageEvent(saved.getId(), conversation.getId(), sender.getEmail(), saved.getContent(), saved.getCreatedAt()));
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingEvent event) {
        messaging.convertAndSend("/topic/conversations/" + event.conversationId() + "/typing",
                new TypingEvent(event.conversationId(), event.senderEmail(), event.isTyping(), Instant.now()));
        messaging.convertAndSend("/topic/typing", event);
    }

    @MessageMapping("/presence")
    public void presence(PresenceRequest request) {
        var user = users.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null) return;
        messaging.convertAndSend("/topic/presence", new PresenceEvent(user.getEmail(), user.getDisplayName(), user.getAvatarUrl(), request.online(), Instant.now()));
    }
}
