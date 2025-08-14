package com.chatterbox.chat.controller;

import com.chatterbox.chat.entity.Conversation;
import com.chatterbox.chat.entity.Message;
import com.chatterbox.chat.dto.AuthDtos;
import com.chatterbox.chat.dto.ConversationDtos;
import com.chatterbox.chat.repository.MessageRepository;
import java.util.List;
import com.chatterbox.chat.repository.ConversationRepository;
import com.chatterbox.chat.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationRepository conversations;
    private final UserRepository users;
    private final MessageRepository messages;

    public ConversationController(ConversationRepository conversations, UserRepository users, MessageRepository messages) {
        this.conversations = conversations;
        this.users = users;
        this.messages = messages;
    }

    public record CreateConversationRequest(String recipientEmail) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@RequestBody CreateConversationRequest request, Authentication authentication) {
        var sender = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found"));
        var recipient = users.findByEmailIgnoreCase(request.recipientEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));
        if (sender.getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself");
        }
        var conversation = conversations.findBetween(sender.getId(), recipient.getId()).orElseGet(() -> {
            var created = new Conversation();
            created.setUserOne(sender);
            created.setUserTwo(recipient);
            return conversations.save(created);
        });
        return Map.of("conversationId", conversation.getId());
    }

    @org.springframework.web.bind.annotation.GetMapping
    public List<ConversationDtos.ConversationResponse> list(Authentication authentication) {
        var sender = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        return conversations.findForUser(sender.getId()).stream().map(conversation -> {
            var contact = conversation.getUserOne().getId().equals(sender.getId()) ? conversation.getUserTwo() : conversation.getUserOne();
            return new ConversationDtos.ConversationResponse(conversation.getId(), new AuthDtos.UserResponse(contact.getEmail(), contact.getDisplayName(), contact.getAvatarUrl()), conversation.unreadFor(sender.getId()));
        }).toList();
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}/messages")
    public List<ConversationDtos.MessageResponse> messages(@org.springframework.web.bind.annotation.PathVariable UUID id, Authentication authentication) {
        requireMember(id, authentication);
        return messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(message -> new ConversationDtos.MessageResponse(message.getId(), message.getContent(), message.getSender().getEmail(), message.getCreatedAt())).toList();
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@org.springframework.web.bind.annotation.PathVariable UUID id, Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        var conversation = requireMember(id, authentication);
        conversation.clearUnreadFor(user.getId());
        conversations.save(conversation);
    }

    @PostMapping("/{id}/messages")
    public ConversationDtos.MessageResponse send(@org.springframework.web.bind.annotation.PathVariable UUID id, @RequestBody ConversationDtos.SendMessageRequest request, Authentication authentication) {
        var sender = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        var conversation = requireMember(id, authentication);
        if (request.body() == null || request.body().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        var message = new Message(); message.setConversation(conversation); message.setSender(sender); message.setContent(request.body().trim());
        var saved = messages.save(message);
        var recipientId = conversation.getUserOne().getId().equals(sender.getId()) ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();
        conversation.incrementUnreadFor(recipientId);
        conversations.save(conversation);
        return new ConversationDtos.MessageResponse(saved.getId(), saved.getContent(), sender.getEmail(), saved.getCreatedAt());
    }

    private Conversation requireMember(UUID id, Authentication authentication) {
        var sender = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        var conversation = conversations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getUserOne().getId().equals(sender.getId()) && !conversation.getUserTwo().getId().equals(sender.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        return conversation;
    }
}
