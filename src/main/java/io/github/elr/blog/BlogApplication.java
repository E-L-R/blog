package io.github.elr.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the blog backend.
 * <p>
 * The application is a modular monolith: every direct sub-package of this package
 * ({@code user}, {@code post}, {@code auth}, ...) is a Spring Modulith module, and
 * {@code ModularityTests} checks that modules only use each other in the allowed ways.
 * <p>
 * {@code common} is declared a <em>shared</em> module. Every module may use it without
 * listing it in its {@code allowedDependencies}, and every module test starts it, which
 * is where module tests get beans such as the {@link java.time.Clock}.
 */
@SpringBootApplication
@Modulithic(sharedModules = "common")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }

}
