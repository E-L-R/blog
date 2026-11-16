package io.github.elr.blog.auth.web.dto;

import io.github.elr.blog.auth.AccessToken;

/**
 * The answer to a successful login. The client sends {@code accessToken} back in the header
 * {@code Authorization: Bearer <accessToken>}, and logs in again after {@code expiresIn} seconds.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse of(AccessToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresIn());
    }
}
