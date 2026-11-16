package io.github.elr.blog.comment;

import com.jayway.jsonpath.JsonPath;
import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.post.PostService;
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

/** Tests the comment endpoints through HTTP. */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CommentApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CommentService commentService;

    @Autowired
    PostService postService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void writesACommentAndAReply() throws Exception {
        var postId = newPost();
        var userId = newUser();

        var comment = send(mvc.post().uri("/posts/{id}/comments", postId), userId, """
                {"content": "Nice post"}
                """);
        assertThat(comment).hasStatus(201);
        var commentId = JsonPath.<String>read(comment.getResponse().getContentAsString(), "$.id");
        assertThat(comment).headers().hasValue(HttpHeaders.LOCATION, "http://localhost/comments/" + commentId);
        assertThat(comment).bodyJson().extractingPath("$.author.id").isEqualTo(userId.toString());

        var reply = send(mvc.post().uri("/posts/{id}/comments", postId), newUser(), """
                {"content": "Thanks", "parentId": "%s"}
                """.formatted(commentId));
        assertThat(reply).hasStatus(201);
        assertThat(reply).bodyJson().extractingPath("$.parentId").isEqualTo(commentId);

        var thread = mvc.get().uri("/posts/{id}/comments", postId).exchange();
        assertThat(thread).hasStatusOk();
        assertThat(thread).bodyJson().extractingPath("$[*].content").asArray().containsExactly("Nice post", "Thanks");
    }

    @Test
    void writingNeedsAToken() {
        var result = mvc.post().uri("/posts/{id}/comments", newPost())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"Hi\"}").exchange();

        assertProblem(result, 401, "UNAUTHENTICATED");
    }

    @Test
    void rejectsBlankOrTooLongContent() {
        var postId = newPost();

        assertProblem(send(mvc.post().uri("/posts/{id}/comments", postId), newUser(), "{\"content\": \" \"}"),
                400, "VALIDATION_FAILED");
        assertProblem(send(mvc.post().uri("/posts/{id}/comments", postId), newUser(),
                "{\"content\": \"%s\"}".formatted("x".repeat(10_001))), 400, "VALIDATION_FAILED");
    }

    @Test
    void aParentOnAnotherPostIsAValidationError() {
        var parent = commentService.create(newPost(), newUser(), "Parent", null);

        var result = send(mvc.post().uri("/posts/{id}/comments", newPost()), newUser(), """
                {"content": "Reply", "parentId": "%s"}
                """.formatted(parent.id()));

        assertProblem(result, 400, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("parentId");
    }

    @Test
    void replyingToADeletedCommentIsAConflict() {
        var postId = newPost();
        var authorId = newUser();
        var parent = commentService.create(postId, authorId, "Parent", null);
        commentService.delete(parent.id(), authorId);

        var result = send(mvc.post().uri("/posts/{id}/comments", postId), newUser(), """
                {"content": "Reply", "parentId": "%s"}
                """.formatted(parent.id()));

        assertProblem(result, 409, "TARGET_DELETED");
    }

    @Test
    void commentingOnAMissingPostIsNotFound() {
        assertProblem(send(mvc.post().uri("/posts/{id}/comments", UUID.randomUUID()), newUser(), "{\"content\": \"Hi\"}"),
                404, "NOT_FOUND");
        assertProblem(mvc.get().uri("/posts/{id}/comments", UUID.randomUUID()).exchange(), 404, "NOT_FOUND");
    }

    @Test
    void theAuthorEditsAndDeletes() {
        var authorId = newUser();
        var comment = commentService.create(newPost(), authorId, "Text", null);

        var edited = send(mvc.put().uri("/comments/{id}", comment.id()), authorId, "{\"content\": \"New text\"}");
        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.content").isEqualTo("New text");

        assertThat(delete(comment.id(), authorId)).hasStatus(204);
        assertThat(delete(comment.id(), authorId)).hasStatus(204);

        var read = mvc.get().uri("/comments/{id}", comment.id()).exchange();
        assertThat(read).bodyJson().extractingPath("$.deleted").isEqualTo(true);
        assertThat(read).bodyJson().hasPathSatisfying("$.content", content -> assertThat(content).isNull());
        assertThat(read).bodyJson().hasPathSatisfying("$.author", author -> assertThat(author).isNull());
        assertProblem(send(mvc.put().uri("/comments/{id}", comment.id()), authorId, "{\"content\": \"Again\"}"),
                409, "TARGET_DELETED");
    }

    @Test
    void someoneElseCannotEditOrDelete() {
        var comment = commentService.create(newPost(), newUser(), "Text", null);
        var otherUserId = newUser();

        assertProblem(send(mvc.put().uri("/comments/{id}", comment.id()), otherUserId, "{\"content\": \"Changed\"}"),
                403, "NOT_AUTHOR");
        assertProblem(delete(comment.id(), otherUserId), 403, "NOT_AUTHOR");
    }

    @Test
    void nobodyCanEditTheBotsComments() {
        var postId = newPost();
        var botReply = commentService.createBotReply(postId, null, "I am the bot");

        var result = send(mvc.put().uri("/comments/{id}", botReply.id()), newUser(), "{\"content\": \"Changed\"}");

        assertProblem(result, 403, "NOT_AUTHOR");
        assertThat(mvc.get().uri("/comments/{id}", botReply.id()).exchange())
                .bodyJson().extractingPath("$.author.bot").isEqualTo(true);
    }

    @Test
    void aDeletedAccountsCommentKeepsItsContentWithoutAnAuthor() {
        var authorId = newUser();
        var comment = commentService.create(newPost(), authorId, "Still here", null);
        jdbc.update("UPDATE users SET deleted_at = now(), email = NULL, password_hash = NULL WHERE id = ?", authorId);

        var result = mvc.get().uri("/comments/{id}", comment.id()).exchange();

        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("Still here");
        assertThat(result).bodyJson().hasPathSatisfying("$.author", author -> assertThat(author).isNull());
    }

    private MvcTestResult delete(UUID commentId, UUID userId) {
        return mvc.delete().uri("/comments/{id}", commentId).with(jwt().jwt(j -> j.subject(userId.toString())))
                .exchange();
    }

    private MvcTestResult send(MockMvcRequestBuilder request, UUID userId, String body) {
        return request.with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private UUID newPost() {
        return postService.create(newUser(), "Title", "Content").id();
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
