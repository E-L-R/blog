package io.github.elr.blog.auth;

/**
 * A signed access token and how many seconds it stays valid.
 *
 * @param value     the JWT, as sent in the {@code Authorization: Bearer} header
 * @param expiresIn the token's lifetime in seconds
 */
public record AccessToken(String value, long expiresIn) {
}
