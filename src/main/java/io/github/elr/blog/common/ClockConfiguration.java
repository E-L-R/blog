package io.github.elr.blog.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the {@link Clock} that all production code reads the current time from.
 * <p>
 * Calling {@code Instant.now()} directly would make time impossible to control in tests.
 * With an injected clock, a test can replace this bean with a fixed or adjustable one,
 * for example to make a lease expire without waiting for it.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
