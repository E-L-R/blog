package io.github.elr.blog.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application must refuse to start with a JWT secret that would make tokens easy to forge.
 * These checks run while the configuration is read, so plain unit tests cover them.
 */
class JwtSecretTests {

    @Test
    void refusesAMissingSecret() {
        assertThatThrownBy(() -> new AuthProperties(new AuthProperties.Jwt(null), Duration.ofHours(1)))
                .hasMessageContaining("must be set");
    }

    @Test
    void refusesAShortSecret() {
        var sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AuthProperties.Jwt(sixteenBytes))
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void refusesASecretThatIsNotBase64() {
        assertThatThrownBy(() -> new AuthProperties.Jwt("not base64 at all!"))
                .hasMessageContaining("Base64");
    }

    @Test
    void acceptsTheDevSecretOnlyWithTheDevProfile() {
        var properties = new AuthProperties(new AuthProperties.Jwt(JwtConfiguration.DEV_SECRET), Duration.ofHours(1));
        var configuration = new JwtConfiguration();

        assertThatThrownBy(() -> configuration.jwtSigningKey(properties, new MockEnvironment()))
                .hasMessageContaining("dev profile");
        var dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThat(configuration.jwtSigningKey(properties, dev)).isNotNull();
    }
}
