package com.jobpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test_secret_test_secret_test_secret_test_secret", 15, 7));

    @Test
    void generatesTokenThatParsesBackToSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "user@example.com");

        assertThat(jwtService.parseUserId(token)).contains(userId);
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.parseUserId("not-a-real-token")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService(
                new JwtProperties("different_secret_different_secret_different_secret", 15, 7));
        String token = otherService.generateAccessToken(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.parseUserId(token)).isEmpty();
    }
}
