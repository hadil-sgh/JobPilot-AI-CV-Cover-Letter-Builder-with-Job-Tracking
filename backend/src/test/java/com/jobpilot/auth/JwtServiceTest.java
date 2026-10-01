package com.jobpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final JwtProperties PROPS = new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));

    private static User user() {
        User user = new User("ada@example.com", "hash", "Ada");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void roundTripsUserId() {
        JwtService service = new JwtService(PROPS);
        User user = user();

        assertThat(service.parseUserId(service.createAccessToken(user))).contains(user.getId());
    }

    @Test
    void rejectsExpiredToken() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = new JwtService(PROPS, Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(user());
        JwtService later = new JwtService(PROPS, Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThat(later.parseUserId(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithOtherKey() {
        JwtProperties other = new JwtProperties("another-secret-another-secret-0123456789", Duration.ofMinutes(15),
                Duration.ofDays(7));
        String token = new JwtService(other).createAccessToken(user());

        assertThat(new JwtService(PROPS).parseUserId(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(new JwtService(PROPS).parseUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofMinutes(1), Duration.ofDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
