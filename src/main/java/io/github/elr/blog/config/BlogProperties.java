package io.github.elr.blog.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(
        @NotNull @Valid JwtProperties jwt,
        @NotNull @Valid AiProperties ai,
        @NotNull @Valid CorsProperties cors,
        @NotNull @Valid RateLimitProperties rateLimit) {

    public record JwtProperties(
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {}

    public record AiProperties(
            boolean enabled,
            @NotBlank String model,
            @NotNull Duration timeout) {}

    public record CorsProperties(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotNull Duration maxAge) {}

    public record RateLimitProperties(
            boolean enabled,
            @Positive int capacity,
            @Positive int refillTokens,
            @NotNull Duration refillDuration) {}
}
