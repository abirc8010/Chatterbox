package com.chatterbox.chat.controller;

import com.chatterbox.chat.entity.Message;
import com.chatterbox.chat.repository.ConversationRepository;
import com.chatterbox.chat.repository.MessageRepository;
import com.chatterbox.chat.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatWebSocketController {
    public record ChatMessage(UUID conversationId, String body, String messageType, String mediaUrl) {}
    public record MessageEvent(UUID id, UUID conversationId, String senderEmail, String body, Instant createdAt, String messageType, String mediaUrl) {}
    public record TypingRequest(UUID conversationId, boolean isTyping) {}
    public record TypingEvent(UUID conversationId, String senderEmail, boolean isTyping, Instant sentAt) {}
    public record PresenceRequest(boolean online) {}
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
        public void send(ChatMessage request, Principal principal) {
        var sender = users.findByEmailIgnoreCase(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sender not found"));
        var conversation = conversations.findByIdWithParticipants(request.conversationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getUserOne().getId().equals(sender.getId()) && !conversation.getUserTwo().getId().equals(sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        }
        if ((request.body() == null || request.body().isBlank()) && request.mediaUrl() == null) return;
        var message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.body() == null ? "" : request.body().trim());
        message.setMessageType(request.messageType() == null ? "TEXT" : request.messageType());
        message.setMediaUrl(request.mediaUrl());
        var saved = messages.save(message);
        var recipientId = conversation.getUserOne().getId().equals(sender.getId()) ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();
        conversation.incrementUnreadFor(recipientId);
        conversations.save(conversation);
        messaging.convertAndSend("/topic/conversations/" + conversation.getId() + "/messages",
                new MessageEvent(saved.getId(), conversation.getId(), sender.getEmail(), saved.getContent(), saved.getCreatedAt(), saved.getMessageType(), saved.getMediaUrl()));
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingRequest request, Principal principal) {
        if (!conversations.existsByIdAndParticipantEmail(request.conversationId(), principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        }
        var event = new TypingEvent(request.conversationId(), principal.getName(), request.isTyping(), Instant.now());
        messaging.convertAndSend("/topic/conversations/" + request.conversationId() + "/typing", event);
    }

    @MessageMapping("/presence")
    public void presence(PresenceRequest request, Principal principal) {
        var user = users.findByEmailIgnoreCase(principal.getName()).orElse(null);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        messaging.convertAndSend("/topic/presence", new PresenceEvent(user.getEmail(), user.getDisplayName(), user.getAvatarUrl(), request.online(), Instant.now()));
    }
}
