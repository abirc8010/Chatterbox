package com.chatterbox.chat.service;

import com.chatterbox.chat.dto.AuthDtos;
import com.chatterbox.chat.entity.User;
import com.chatterbox.chat.repository.UserRepository;
import com.chatterbox.chat.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return response(users.save(user));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return response(user);
    }

    private AuthDtos.AuthResponse response(User user) {
        return new AuthDtos.AuthResponse(jwtService.issue(user.getEmail()), new AuthDtos.UserResponse(user.getEmail(), user.getDisplayName(), user.getAvatarUrl()));
    }
}
