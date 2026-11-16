package io.github.elr.blog.user;

import io.github.elr.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the public profile endpoint, {@code GET /users/{id}}. */
@ApplicationModuleTest(extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserService userService;

    @Test
    void showsAPublicProfileWithoutTheEmail() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        var account = userService.create(name + "@example.com", name, "hash");

        var result = mvc.get().uri("/users/{id}", account.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.username").isEqualTo(name);
        assertThat(result).bodyJson().extractingPath("$.bot").isEqualTo(false);
        assertThat(result).bodyJson().doesNotHavePath("$.email");
    }

    @Test
    void showsTheBot() {
        var result = mvc.get().uri("/users/{id}", UserService.BOT_USER_ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.username").isEqualTo("blog-ai");
        assertThat(result).bodyJson().extractingPath("$.bot").isEqualTo(true);
    }

    @Test
    void anUnknownUserIsNotFound() {
        var result = mvc.get().uri("/users/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }
}
