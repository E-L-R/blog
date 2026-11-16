package io.github.elr.blog.like;

import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Tests the like endpoints through HTTP. */
@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LikeApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    UserService userService;

    @Test
    void likesAPostAndShowsTheCount() {
        var postId = newPost();
        var userId = newUser();

        assertThat(as(userId, mvc.put().uri("/posts/{id}/like", postId))).hasStatus(204);
        assertThat(as(userId, mvc.put().uri("/posts/{id}/like", postId))).hasStatus(204);

        assertThat(mvc.get().uri("/posts/{id}", postId).exchange()).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        assertThat(as(userId, mvc.delete().uri("/posts/{id}/like", postId))).hasStatus(204);
        assertThat(mvc.get().uri("/posts/{id}", postId).exchange()).bodyJson().extractingPath("$.likeCount").isEqualTo(0);
    }

    @Test
    void likesAComment() {
        var commentId = commentService.create(newPost(), newUser(), "Text", null).id();

        assertThat(as(newUser(), mvc.put().uri("/comments/{id}/like", commentId))).hasStatus(204);

        assertThat(mvc.get().uri("/comments/{id}", commentId).exchange())
                .bodyJson().extractingPath("$.likeCount").isEqualTo(1);
    }

    @Test
    void likingNeedsAToken() {
        assertProblem(mvc.put().uri("/posts/{id}/like", newPost()).exchange(), 401, "UNAUTHENTICATED");
        assertProblem(mvc.get().uri("/me/likes/posts?ids={id}", newPost()).exchange(), 401, "UNAUTHENTICATED");
    }

    @Test
    void likingAMissingPostIsNotFoundAndADeletedCommentAConflict() {
        var authorId = newUser();
        var commentId = commentService.create(newPost(), authorId, "Text", null).id();
        commentService.delete(commentId, authorId);

        assertProblem(as(newUser(), mvc.put().uri("/posts/{id}/like", UUID.randomUUID())), 404, "NOT_FOUND");
        assertProblem(as(newUser(), mvc.put().uri("/comments/{id}/like", commentId)), 409, "TARGET_DELETED");
    }

    @Test
    void looksUpWhichPostsTheCallerLikes() {
        var userId = newUser();
        var liked = newPost();
        var notLiked = newPost();
        as(userId, mvc.put().uri("/posts/{id}/like", liked));

        var result = as(userId, mvc.get().uri("/me/likes/posts?ids={a},{b}", liked, notLiked));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.likedIds").asArray().containsExactly(liked.toString());
    }

    @Test
    void aLookupNeedsOneToAHundredIds() {
        var userId = newUser();
        var tooMany = String.join(",", Stream.generate(() -> UUID.randomUUID().toString())
                .limit(101).toList());

        assertProblem(as(userId, mvc.get().uri("/me/likes/comments")), 400, "VALIDATION_FAILED");
        assertProblem(as(userId, mvc.get().uri("/me/likes/comments?ids=" + tooMany)), 400, "VALIDATION_FAILED");
    }

    private MvcTestResult as(UUID userId, MockMvcRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(userId.toString()))).exchange();
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
