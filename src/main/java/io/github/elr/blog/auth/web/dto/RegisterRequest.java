package io.github.elr.blog.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /auth/register}.
 * <p>
 * Most Bean Validation constraints, such as {@code @Pattern} and {@code @Size}, treat
 * {@code null} as valid, so a field that is required also needs {@code @NotNull} or
 * {@code @NotBlank}. The limits match the columns of the {@code users} table, so a request
 * that passes validation always fits in the database.
 */
public record RegisterRequest(

        // 254 characters is the longest address that email servers accept.
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_-]{3,30}", message = "must be 3 to 30 characters: letters, digits, _ and -")
        String username,

        // bcrypt ignores everything after the first 72 bytes, so a longer password is refused
        // instead of being silently cut short.
        @NotNull
        @Size(min = 8, message = "must be at least 8 characters")
        @MaxBytes(72)
        String password
) {
}
