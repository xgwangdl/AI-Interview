package com.guangge.Interview.practice.media;

import com.guangge.Interview.practice.dto.MediaSignedUrlView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and validates time-limited media URLs so a {@code <video>} element can play a
 * recording without carrying a bearer token. Signatures are HMAC-SHA256 over answer id + expiry.
 */
@Service
public class MediaSignedUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long ttlSeconds;

    public MediaSignedUrlService(
            @Value("${auth.token.secret}") String secret,
            @Value("${practice.media.signed-url-ttl-seconds:300}") long ttlSeconds) {
        this.secret = secret;
        this.ttlSeconds = Math.max(1, ttlSeconds);
    }

    public MediaSignedUrlView issue(UUID answerId) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String url = "/api/v1/media/" + answerId
                + "?expires=" + expiresAt
                + "&signature=" + signature(answerId, expiresAt);
        return new MediaSignedUrlView(url, Instant.ofEpochSecond(expiresAt), ttlSeconds);
    }

    public void verify(UUID answerId, Long expiresAt, String signature) {
        if (expiresAt == null || signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A signed media URL is required");
        }
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The signed media URL has expired");
        }
        if (!MessageDigest.isEqual(
                signature(answerId, expiresAt).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The signed media URL is not valid");
        }
    }

    String signature(UUID answerId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((answerId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign the media URL", exception);
        }
    }
}
