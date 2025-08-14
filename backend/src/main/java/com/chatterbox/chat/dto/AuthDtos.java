package com.chatterbox.chat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank @Size(min = 8) String password) {}
    public record RegisterRequest(@Email @NotBlank String email, @NotBlank @Size(min = 2, max = 100) String displayName, @NotBlank @Size(min = 8) String password) {}
    public record UserResponse(String email, String displayName, String avatarUrl) {}
    public record AuthResponse(String token, UserResponse user) {}
}
