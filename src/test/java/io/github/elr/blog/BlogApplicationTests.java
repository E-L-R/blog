package io.github.elr.blog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the whole application against a real PostgreSQL. This proves that every bean can be
 * created, that the Flyway migrations run, and that Hibernate accepts the schema they create.
 * <p>
 * {@code @AutoConfigureMockMvc} is not needed here, but {@link ApiDocumentationTests} needs it, and
 * with the same annotations both classes share one application context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BlogApplicationTests {

    @Test
    void contextLoads() {
    }

}
