package com.chatterbox.chat.controller;

import com.chatterbox.chat.dto.AuthDtos;
import com.chatterbox.chat.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/search")
    public List<AuthDtos.UserResponse> search(@RequestParam String email, Authentication authentication) {
        return users.findTop8ByEmailContainingIgnoreCase(email.trim()).stream()
            .filter(user -> !user.getEmail().equalsIgnoreCase(authentication.getName()))
                .map(user -> new AuthDtos.UserResponse(user.getEmail(), user.getDisplayName(), user.getAvatarUrl()))
            .toList();
    }

    public record UpdateProfileRequest(String displayName, String avatarUrl) {}

    @PatchMapping("/me")
    public AuthDtos.UserResponse updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        if (request.displayName() != null && !request.displayName().isBlank()) user.setDisplayName(request.displayName().trim());
        user.setAvatarUrl(request.avatarUrl());
        var saved = users.save(user);
        return new AuthDtos.UserResponse(saved.getEmail(), saved.getDisplayName(), saved.getAvatarUrl());
    }
}
