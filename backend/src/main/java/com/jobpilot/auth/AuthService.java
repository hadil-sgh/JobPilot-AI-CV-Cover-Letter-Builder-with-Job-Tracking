package com.jobpilot.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.auth.dto.AuthDtos.AuthResponse;
import com.jobpilot.auth.dto.AuthDtos.LoginRequest;
import com.jobpilot.auth.dto.AuthDtos.RegisterRequest;
import com.jobpilot.auth.dto.AuthDtos.UserDto;
import com.jobpilot.common.error.ApiException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        String name = req.fullName() == null || req.fullName().isBlank() ? null : req.fullName().trim();
        User user = users.save(new User(email, passwordEncoder.encode(req.password()), name));
        return tokensFor(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(normalize(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return tokensFor(user);
    }

    // Not @Transactional: consume() must commit its revocations even when it throws.
    public AuthResponse refresh(String refreshToken) {
        return tokensFor(refreshTokens.consume(refreshToken));
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        return users.findById(userId).map(AuthService::toDto)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private AuthResponse tokensFor(User user) {
        return new AuthResponse(jwtService.createAccessToken(user), refreshTokens.issue(user),
                jwtService.accessTokenTtlSeconds(), toDto(user));
    }

    private static UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
