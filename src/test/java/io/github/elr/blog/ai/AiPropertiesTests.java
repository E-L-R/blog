package io.github.elr.blog.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application refuses to start when the lease is not longer than the LLM timeout: a second
 * worker could then claim a job whose first call is still running. The check runs while the
 * settings are read, so a plain unit test covers it.
 */
class AiPropertiesTests {

    @Test
    void refusesALeaseNotLongerThanTheTimeout() {
        assertThatThrownBy(() -> properties(Duration.ofSeconds(60), Duration.ofSeconds(60)))
                .hasMessageContaining("must be longer than blog.ai.llm.timeout");
    }

    @Test
    void acceptsALongerLease() {
        assertThat(properties(Duration.ofMinutes(2), Duration.ofSeconds(60)).worker().lease())
                .isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void theBackoffDoublesAfterEachAttempt() {
        var worker = properties(Duration.ofMinutes(2), Duration.ofSeconds(60)).worker();

        assertThat(worker.backoffAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(worker.backoffAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(worker.backoffAfter(3)).isEqualTo(Duration.ofSeconds(120));
    }

    private static AiProperties properties(Duration lease, Duration timeout) {
        return new AiProperties(
                false,
                new AiProperties.Worker(true, Duration.ofSeconds(2), 4, lease, 5, Duration.ofSeconds(30)),
                new AiProperties.Llm("claude-opus-5-5", "medium", timeout, 4096),
                new AiProperties.Prompt(20_000, 10_000));
    }
}
