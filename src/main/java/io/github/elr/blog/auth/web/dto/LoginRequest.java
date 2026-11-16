package io.github.elr.blog.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /auth/login}. Only presence is checked here: whether the email
 * and password are right is the service's job, and both failures get the same answer.
 */
public record LoginRequest(@NotBlank String email, @NotNull String password) {
}
