package io.github.elr.blog.ai;

import com.jayway.jsonpath.JsonPath;
import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Tests the AI endpoints, and the TL;DR in post responses, through HTTP. */
@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AiTestConfiguration.class})
@ActiveProfiles("test")
class AiApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AiWorker worker;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    UserService userService;

    @Autowired
    AiTestConfiguration.Reset reset;

    @BeforeEach
    void startWithAnEmptyQueue() {
        reset.run();
    }

    @Test
    void asksForAReplyAndFollowsTheRequest() throws Exception {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var userId = newUser();

        var asked = as(userId, mvc.post().uri("/posts/{id}/ai-reply", postId));

        assertThat(asked).hasStatus(202);
        var requestId = JsonPath.<String>read(asked.getResponse().getContentAsString(), "$.id");
        assertThat(asked).headers().hasValue(HttpHeaders.LOCATION, "http://localhost/ai-requests/" + requestId);
        assertThat(asked).bodyJson().extractingPath("$.status").isEqualTo("PENDING");

        worker.pollOnce();

        var done = as(userId, mvc.get().uri("/ai-requests/{id}", requestId));
        assertThat(done).hasStatusOk();
        assertThat(done).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
        var replyId = JsonPath.<String>read(done.getResponse().getContentAsString(), "$.resultCommentId");
        assertThat(mvc.get().uri("/comments/{id}", replyId).exchange())
                .bodyJson().extractingPath("$.author.username").isEqualTo("blog-ai");
    }

    @Test
    void onlyTheRequesterSeesARequest() throws Exception {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var asked = as(newUser(), mvc.post().uri("/posts/{id}/ai-reply", postId));
        var requestId = JsonPath.<String>read(asked.getResponse().getContentAsString(), "$.id");

        assertProblem(as(newUser(), mvc.get().uri("/ai-requests/{id}", requestId)), 404, "NOT_FOUND");
        assertProblem(mvc.get().uri("/ai-requests/{id}", requestId).exchange(), 401, "UNAUTHENTICATED");
    }

    @Test
    void askingNeedsAToken() {
        var postId = postService.create(newUser(), "Title", "Content").id();

        assertProblem(mvc.post().uri("/posts/{id}/ai-reply", postId).exchange(), 401, "UNAUTHENTICATED");
    }

    @Test
    void aMissingTargetIsNotFoundAndADeletedCommentAConflict() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var authorId = newUser();
        var commentId = commentService.create(postId, authorId, "Text", null).id();
        commentService.delete(commentId, authorId);

        assertProblem(as(newUser(), mvc.post().uri("/posts/{id}/ai-reply", UUID.randomUUID())), 404, "NOT_FOUND");
        assertProblem(as(newUser(), mvc.post().uri("/comments/{id}/ai-reply", UUID.randomUUID())), 404, "NOT_FOUND");
        assertProblem(as(newUser(), mvc.post().uri("/comments/{id}/ai-reply", commentId)), 409, "TARGET_DELETED");
    }

    @Test
    void aPostShowsItsTldrOnceGenerated() {
        var postId = postService.create(newUser(), "Title", "Content").id();

        var pending = mvc.get().uri("/posts/{id}", postId).exchange();
        assertThat(pending).bodyJson().extractingPath("$.tldrStatus").isEqualTo("PENDING");
        assertThat(pending).bodyJson().hasPathSatisfying("$.tldr", tldr -> assertThat(tldr).isNull());

        fake.answerWith(prompt -> new LlmReply("The summary", "fake-model"));
        worker.pollOnce();

        var generated = mvc.get().uri("/posts/{id}", postId).exchange();
        assertThat(generated).bodyJson().extractingPath("$.tldrStatus").isEqualTo("GENERATED");
        assertThat(generated).bodyJson().extractingPath("$.tldr").isEqualTo("The summary");
    }

    private MvcTestResult as(UUID userId, MockMvcRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(userId.toString()))).exchange();
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
