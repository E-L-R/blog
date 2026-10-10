package io.github.elr.blog.testsupport;

import io.github.elr.blog.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class BaseIntegrationTest {}
