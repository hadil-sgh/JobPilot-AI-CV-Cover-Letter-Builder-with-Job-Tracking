package com.jobpilot.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.common.error.ApiException;

/**
 * Opaque, rotating refresh tokens. Only the SHA-256 hash is stored. Presenting an already-revoked
 * token is treated as theft: every token of that user is revoked.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** Creates a token for the user and returns the raw value (shown to the client once). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(raw), Instant.now().plus(props.refreshTokenTtl())));
        return raw;
    }

    /** Validates and revokes the token, returning its user. The caller issues a new pair. */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(RefreshTokenService::invalid);
        if (token.isRevoked()) {
            repository.revokeAllForUser(token.getUser().getId());
            throw invalid();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.revoke();
            throw invalid();
        }
        token.revoke();
        return token.getUser();
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(RefreshToken::revoke);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
