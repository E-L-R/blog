package io.github.elr.blog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BlogPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(BlogProperties.class)
    static class TestConfig {}

    @Test
    void should_bind_valid_properties() {
        runner.withPropertyValues(
                        "blog.jwt.issuer=test-issuer",
                        "blog.jwt.access-token-ttl=10m",
                        "blog.jwt.refresh-token-ttl=14d",
                        "blog.ai.enabled=true",
                        "blog.ai.model=gpt-4o",
                        "blog.ai.timeout=15s",
                        "blog.cors.allowed-origins[0]=https://example.com",
                        "blog.cors.allowed-methods[0]=GET",
                        "blog.cors.allowed-methods[1]=POST",
                        "blog.cors.max-age=30m",
                        "blog.rate-limit.enabled=true",
                        "blog.rate-limit.capacity=100",
                        "blog.rate-limit.refill-tokens=50",
                        "blog.rate-limit.refill-duration=1m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BlogProperties properties = context.getBean(BlogProperties.class);
                    assertThat(properties.jwt().issuer()).isEqualTo("test-issuer");
                    assertThat(properties.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.jwt().refreshTokenTtl()).isEqualTo(Duration.ofDays(14));
                    assertThat(properties.ai().enabled()).isTrue();
                    assertThat(properties.ai().model()).isEqualTo("gpt-4o");
                    assertThat(properties.ai().timeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.cors().allowedOrigins()).containsExactly("https://example.com");
                    assertThat(properties.cors().allowedMethods()).containsExactly("GET", "POST");
                    assertThat(properties.cors().maxAge()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.rateLimit().enabled()).isTrue();
                    assertThat(properties.rateLimit().capacity()).isEqualTo(100);
                    assertThat(properties.rateLimit().refillTokens()).isEqualTo(50);
                    assertThat(properties.rateLimit().refillDuration()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    void should_fail_validation_when_required_property_is_missing() {
        runner.withPropertyValues(
                        // Missing blog.jwt.issuer
                        "blog.jwt.access-token-ttl=10m",
                        "blog.jwt.refresh-token-ttl=14d",
                        "blog.ai.enabled=true",
                        "blog.ai.model=gpt-4o",
                        "blog.ai.timeout=15s",
                        "blog.cors.allowed-origins[0]=https://example.com",
                        "blog.cors.allowed-methods[0]=GET",
                        "blog.cors.max-age=30m",
                        "blog.rate-limit.enabled=true",
                        "blog.rate-limit.capacity=100",
                        "blog.rate-limit.refill-tokens=50",
                        "blog.rate-limit.refill-duration=1m")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void should_fail_validation_when_numeric_property_is_invalid() {
        runner.withPropertyValues(
                        "blog.jwt.issuer=test-issuer",
                        "blog.jwt.access-token-ttl=10m",
                        "blog.jwt.refresh-token-ttl=14d",
                        "blog.ai.enabled=true",
                        "blog.ai.model=gpt-4o",
                        "blog.ai.timeout=15s",
                        "blog.cors.allowed-origins[0]=https://example.com",
                        "blog.cors.allowed-methods[0]=GET",
                        "blog.cors.max-age=30m",
                        "blog.rate-limit.enabled=true",
                        "blog.rate-limit.capacity=-10", // Invalid: capacity must be positive
                        "blog.rate-limit.refill-tokens=50",
                        "blog.rate-limit.refill-duration=1m")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }
}
