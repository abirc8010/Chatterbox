package com.chatterbox.chat.repository;

import com.chatterbox.chat.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @Query("select c from Conversation c where (c.userOne.id = :first and c.userTwo.id = :second) or (c.userOne.id = :second and c.userTwo.id = :first)")
    Optional<Conversation> findBetween(@Param("first") UUID first, @Param("second") UUID second);

    @EntityGraph(attributePaths = {"userOne", "userTwo"})
    @Query("select c from Conversation c where c.userOne.id = :userId or c.userTwo.id = :userId")
    List<Conversation> findForUser(@Param("userId") UUID userId);

        @EntityGraph(attributePaths = {"userOne", "userTwo"})
        @Query("select c from Conversation c where c.id = :conversationId")
        Optional<Conversation> findByIdWithParticipants(@Param("conversationId") UUID conversationId);

        @Query("select case when count(c) > 0 then true else false end from Conversation c "
            + "where c.id = :conversationId and "
            + "(lower(c.userOne.email) = lower(:email) or lower(c.userTwo.email) = lower(:email))")
        boolean existsByIdAndParticipantEmail(
            @Param("conversationId") UUID conversationId,
            @Param("email") String email);
}
