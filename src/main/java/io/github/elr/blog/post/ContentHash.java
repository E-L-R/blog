package io.github.elr.blog.post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The SHA-256 of a post's content, as 64 hex characters. The V5 migration computes the same
 * value in SQL for posts that existed before ({@code encode(sha256(convert_to(content, 'UTF8')), 'hex')}).
 */
final class ContentHash {

    private ContentHash() {
    }

    static String of(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // Every Java runtime must support SHA-256, so this cannot happen.
            throw new IllegalStateException(exception);
        }
    }
}
