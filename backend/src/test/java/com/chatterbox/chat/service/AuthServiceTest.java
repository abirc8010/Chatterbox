package com.chatterbox.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatterbox.chat.dto.AuthDtos;
import com.chatterbox.chat.entity.User;
import com.chatterbox.chat.repository.UserRepository;
import com.chatterbox.chat.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("unit-test-secret-that-is-long-enough-123456");
        authService = new AuthService(users, passwordEncoder, jwtService);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndIssuesToken() {
        var request = new AuthDtos.RegisterRequest("  Person@Example.com ", "  Person  ", "password123");
        var savedUser = new User();
        savedUser.setEmail("person@example.com");
        savedUser.setDisplayName("Person");
        savedUser.setPasswordHash("bcrypt-hash");

        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenReturn(savedUser);
        var response = authService.register(request);

        assertEquals("person@example.com", jwtService.parse(response.token()).getSubject());
        assertEquals("person@example.com", response.user().email());
        assertEquals("Person", response.user().displayName());
        verify(passwordEncoder).encode("password123");
        verify(users).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(
                        new AuthDtos.RegisterRequest("person@example.com", "Person", "password123")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void loginReturnsTokenForMatchingPassword() {
        var user = new User();
        user.setEmail("person@example.com");
        user.setDisplayName("Person");
        user.setPasswordHash("bcrypt-hash");
        when(users.findByEmailIgnoreCase("Person@Example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        var response = authService.login(new AuthDtos.LoginRequest(" Person@Example.com ", "password123"));

        assertEquals("person@example.com", jwtService.parse(response.token()).getSubject());
        assertNotNull(response.user());
    }

    @Test
    void loginRejectsIncorrectPassword() {
        var user = new User();
        user.setEmail("person@example.com");
        user.setPasswordHash("bcrypt-hash");
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "bcrypt-hash")).thenReturn(false);

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new AuthDtos.LoginRequest("person@example.com", "wrongpass")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }
}
