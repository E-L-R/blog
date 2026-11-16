package io.github.elr.blog.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;

/**
 * The beans that sign and check access tokens.
 * <p>
 * Tokens are signed with HS256: one secret key both signs and checks them. That is enough
 * because this application is the only one that issues and reads them (see docs/DECISIONS.md,
 * D10). Every instance must use the same secret, so a token from one is valid on all.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class JwtConfiguration {

    /** The {@code iss} claim of every token, checked when a token comes back. */
    static final String ISSUER = "blog";

    /**
     * The secret in application-dev.properties. It is committed to the repository, so it must
     * never sign real tokens: the application refuses it unless the {@code dev} profile is active.
     */
    static final String DEV_SECRET = "ZGV2LW9ubHktc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbiE=";

    @Bean
    SecretKey jwtSigningKey(AuthProperties properties, Environment environment) {
        if (DEV_SECRET.equals(properties.jwt().secret()) && !environment.matchesProfiles("dev")) {
            throw new IllegalStateException("The development JWT secret is only accepted with the dev profile");
        }
        return new SecretKeySpec(properties.jwt().keyBytes(), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
    }

    /**
     * Checks a token's signature, then its claims: it must not be expired (allowing for
     * 30 seconds of clock difference between servers) and must have been issued by this app.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(ISSUER)));
        return decoder;
    }
}
