package io.github.elr.blog.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test support for the AI tests: replaces the application's clock with {@link MutableClock}
 * ({@code @Primary} makes every bean that asks for a {@code Clock} get this one), and provides
 * {@link Reset}.
 */
@TestConfiguration(proxyBeanMethods = false)
class AiTestConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    Reset reset(FakeLlmClient fake, JdbcTemplate jdbc) {
        return new Reset(fake, jdbc);
    }

    /**
     * Gives a test a clean start. The AI tests share one database, so a test would otherwise see
     * the unfinished jobs that earlier tests left. This cancels them, and resets the fake LLM.
     */
    record Reset(FakeLlmClient fake, JdbcTemplate jdbc) {

        void run() {
            fake.reset();
            jdbc.update("UPDATE ai_requests SET status = 'CANCELLED', locked_until = NULL, completed_at = now() "
                    + "WHERE status IN ('PENDING', 'IN_PROGRESS')");
        }
    }
}
