package io.github.elr.blog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** The OpenAPI description, generated from the whole application, and the health check. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiDocumentationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void describesEveryEndpointWithoutAToken() {
        var result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.info.title").isEqualTo("Blog API");
        assertThat(result).bodyJson().extractingPath("$.paths").asMap().containsKeys(
                "/auth/register", "/auth/login", "/me", "/me/delete", "/users/{id}",
                "/posts", "/posts/{id}", "/posts/{postId}/comments", "/comments/{id}",
                "/posts/{id}/like", "/me/likes/posts", "/posts/{id}/ai-reply", "/ai-requests/{id}");
    }

    @Test
    void marksOnlyTheEndpointsThatNeedATokenAndHidesTheCurrentUser() {
        var result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).bodyJson().extractingPath("$.paths['/posts'].post.security[0].bearerAuth").isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.paths['/posts'].get.security");
        assertThat(result).bodyJson().doesNotHavePath("$.paths['/posts'].post.parameters");
        assertThat(result).bodyJson().extractingPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer");
    }

    @Test
    void groupsAndSummarisesTheEndpoints() {
        var result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).bodyJson().extractingPath("$.paths['/posts/{id}/like'].put.summary").isEqualTo("Like a post");
        assertThat(result).bodyJson().extractingPath("$.paths['/posts/{id}/like'].put.tags[0]").isEqualTo("Likes");
    }

    @Test
    void servesSwaggerUiAndTheHealthCheck() {
        assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
