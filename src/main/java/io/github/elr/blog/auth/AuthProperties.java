package io.github.elr.blog.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Base64;

/**
 * The {@code blog.auth.*} settings, bound from application.properties or environment
 * variables ({@code BLOG_AUTH_JWT_SECRET}, {@code BLOG_AUTH_TOKEN_TTL}).
 * <p>
 * The checks run while the record is created at startup, so the application refuses to
 * start with a missing or weak secret instead of failing on the first login.
 *
 * @param jwt      the key that signs and checks tokens
 * @param tokenTtl how long an access token stays valid
 */
@ConfigurationProperties("blog.auth")
record AuthProperties(Jwt jwt, @DefaultValue("1h") Duration tokenTtl) {

    AuthProperties {
        if (jwt == null || jwt.secret() == null || jwt.secret().isBlank()) {
            throw new IllegalArgumentException("blog.auth.jwt.secret must be set");
        }
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("blog.auth.token-ttl must be positive");
        }
    }

    /**
     * @param secret a Base64-encoded HS256 key of at least 32 random bytes
     */
    record Jwt(String secret) {

        /** HS256 needs a key of at least 256 bits; a shorter one would make tokens easier to forge. */
        static final int MIN_KEY_BYTES = 32;

        Jwt {
            if (secret != null && !secret.isBlank()) {
                byte[] key;
                try {
                    key = Base64.getDecoder().decode(secret);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("blog.auth.jwt.secret must be Base64-encoded", exception);
                }
                if (key.length < MIN_KEY_BYTES) {
                    throw new IllegalArgumentException(
                            "blog.auth.jwt.secret must decode to at least " + MIN_KEY_BYTES + " bytes");
                }
            }
        }

        byte[] keyBytes() {
            return Base64.getDecoder().decode(secret);
        }
    }
}
