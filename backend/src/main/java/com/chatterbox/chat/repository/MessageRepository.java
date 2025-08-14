package com.chatterbox.chat.repository;

import com.chatterbox.chat.entity.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    @EntityGraph(attributePaths = "sender")
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
