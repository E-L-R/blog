package io.github.elr.blog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole application against a real PostgreSQL. This proves that every bean can be
 * created, that the Flyway migrations run, and that Hibernate accepts the schema they create.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BlogApplicationTests {

    @Test
    void contextLoads() {
    }

}
