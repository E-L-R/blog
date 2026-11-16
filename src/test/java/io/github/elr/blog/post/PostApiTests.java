package io.github.elr.blog.post;

import com.jayway.jsonpath.JsonPath;
import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Tests the {@code /posts} endpoints through HTTP.
 * <p>
 * {@code jwt()} from Spring Security's test support logs a request in as the given user
 * without a real token, so these tests don't depend on login. Real tokens are tested in
 * {@code AuthModuleTests}.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PostApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserService userService;

    @Autowired
    PostService postService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAPostAndReadsItBack() throws Exception {
        var authorId = newUser();

        var created = send(mvc.post().uri("/posts"), authorId, """
                {"title": "Hello", "content": "My first post"}
                """);

        assertThat(created).hasStatus(201);
        var id = JsonPath.<String>read(created.getResponse().getContentAsString(), "$.id");
        assertThat(created).headers().hasValue(HttpHeaders.LOCATION, "http://localhost/posts/" + id);
        var read = mvc.get().uri("/posts/{id}", id).exchange();
        assertThat(read).hasStatusOk();
        assertThat(read).bodyJson().extractingPath("$.title").isEqualTo("Hello");
        assertThat(read).bodyJson().extractingPath("$.content").isEqualTo("My first post");
        assertThat(read).bodyJson().extractingPath("$.author.id").isEqualTo(authorId.toString());
        assertThat(read).bodyJson().extractingPath("$.author.bot").isEqualTo(false);
    }

    @Test
    void creatingNeedsAToken() {
        var result = mvc.post().uri("/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Hello\", \"content\": \"Text\"}").exchange();

        assertProblem(result, 401, "UNAUTHENTICATED");
    }

    @Test
    void rejectsInvalidPosts() {
        var result = send(mvc.post().uri("/posts"), newUser(), """
                {"title": "%s", "content": "  "}
                """.formatted("x".repeat(201)));

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("content", "title");
    }

    @Test
    void aMissingPostIsNotFound() {
        assertProblem(mvc.get().uri("/posts/{id}", UUID.randomUUID()).exchange(), 404, "NOT_FOUND");
    }

    @Test
    void anIdThatIsNotAUuidIsABadRequest() {
        assertProblem(mvc.get().uri("/posts/not-a-uuid").exchange(), 400, "BAD_REQUEST");
    }

    @Test
    void theAuthorEditsAndDeletes() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();

        var edited = send(mvc.put().uri("/posts/{id}", postId), authorId, """
                {"title": "New title", "content": "New content"}
                """);
        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.title").isEqualTo("New title");

        var deleted = mvc.delete().uri("/posts/{id}", postId).with(jwt().jwt(j -> j.subject(authorId.toString())))
                .exchange();
        assertThat(deleted).hasStatus(204);
        assertProblem(mvc.get().uri("/posts/{id}", postId).exchange(), 404, "NOT_FOUND");
    }

    @Test
    void someoneElseCannotEditOrDelete() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var otherUserId = newUser();

        var edit = send(mvc.put().uri("/posts/{id}", postId), otherUserId, """
                {"title": "Changed", "content": "Changed"}
                """);
        var delete = mvc.delete().uri("/posts/{id}", postId).with(jwt().jwt(j -> j.subject(otherUserId.toString())))
                .exchange();

        assertProblem(edit, 403, "NOT_AUTHOR");
        assertProblem(delete, 403, "NOT_AUTHOR");
    }

    @Test
    void listsNewestFirstWithPageMetadata() {
        var authorId = newUser();
        var first = postService.create(authorId, "First", "Content").id();
        var second = postService.create(authorId, "Second", "Content").id();

        var result = mvc.get().uri("/posts?authorId={id}&size=1", authorId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(second.toString());
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(result).bodyJson().extractingPath("$.page.size").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
        var nextPage = mvc.get().uri("/posts?authorId={id}&size=1&page=1", authorId).exchange();
        assertThat(nextPage).bodyJson().extractingPath("$.content[0].id").isEqualTo(first.toString());
    }

    @Test
    void rejectsAPageSizeOverTheMaximum() {
        var result = mvc.get().uri("/posts?size=51").exchange();

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void aDeletedAuthorIsShownAsNull() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();
        jdbc.update("UPDATE users SET deleted_at = now(), email = NULL, password_hash = NULL WHERE id = ?", authorId);

        var result = mvc.get().uri("/posts/{id}", postId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().hasPathSatisfying("$.author", author -> assertThat(author).isNull());
        assertThat(mvc.get().uri("/posts?authorId={id}", authorId).exchange())
                .bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
    }

    private MvcTestResult send(MockMvcRequestBuilder request,
                               UUID userId, String body) {
        return request.with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private UUID newUser() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return userService.create(name + "@example.com", name, "hash").id();
    }

    private static void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
