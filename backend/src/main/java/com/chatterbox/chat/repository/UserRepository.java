package com.chatterbox.chat.repository;

import com.chatterbox.chat.entity.User;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findTop8ByEmailContainingIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
