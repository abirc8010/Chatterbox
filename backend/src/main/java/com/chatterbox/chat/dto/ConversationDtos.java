package com.chatterbox.chat.dto;

import java.time.Instant;
import java.util.UUID;

public final class ConversationDtos {
    private ConversationDtos() {}
    public record ConversationResponse(UUID id, AuthDtos.UserResponse contact, int unreadCount) {}
    public record MessageResponse(UUID id, String body, String senderEmail, Instant createdAt) {}
    public record SendMessageRequest(String body) {}
}
