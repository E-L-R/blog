package io.github.elr.blog.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_-]{3,30}", message = "must be 3 to 30 characters: letters, digits, _ and -")
        String username,

        @NotNull
        @Size(min = 8, message = "must be at least 8 characters")
        @MaxBytes(72)
        String password
) {
}
