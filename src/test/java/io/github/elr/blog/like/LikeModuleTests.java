package io.github.elr.blog.like;

import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.comment.CommentDeletedException;
import io.github.elr.blog.comment.CommentNotFoundException;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@code like} module's service. Its dependencies, {@code post} and {@code comment},
 * need {@code user} in turn, so the test starts all of them ({@code ALL_DEPENDENCIES}).
 * <p>
 * {@code auth} is included for the security configuration that {@link LikeApiTests} needs. Both
 * classes have the same annotations, so Spring builds one application context for both.
 */
@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LikeModuleTests {

    @Autowired
    LikeService likeService;

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void likingAndUnlikingAreIdempotent() {
        var postId = newPost();
        var userId = newUser();

        likeService.likePost(userId, postId);
        likeService.likePost(userId, postId);
        assertThat(likeCount("posts", postId)).isEqualTo(1);
        assertThat(rows("post_likes", "post_id", postId)).isEqualTo(1);

        likeService.unlikePost(userId, postId);
        likeService.unlikePost(userId, postId);
        assertThat(likeCount("posts", postId)).isZero();
        assertThat(rows("post_likes", "post_id", postId)).isZero();
    }

    @Test
    void countsLikesFromDifferentUsers() {
        var commentId = newComment();

        likeService.likeComment(newUser(), commentId);
        likeService.likeComment(newUser(), commentId);

        assertThat(likeCount("comments", commentId)).isEqualTo(2);
    }

    // Both threads wait at the barrier, then like (or unlike) at the same moment. The unique
    // constraint makes the second insert wait for the first and then insert nothing.
    @Test
    void twoParallelLikesBySameUserCountOnce() throws Exception {
        var postId = newPost();
        var userId = newUser();

        runTwiceInParallel(() -> {
            likeService.likePost(userId, postId);
            return null;
        });
        assertThat(rows("post_likes", "post_id", postId)).isEqualTo(1);
        assertThat(likeCount("posts", postId)).isEqualTo(1);

        runTwiceInParallel(() -> {
            likeService.unlikePost(userId, postId);
            return null;
        });
        assertThat(rows("post_likes", "post_id", postId)).isZero();
        assertThat(likeCount("posts", postId)).isZero();
    }

    @Test
    void twoParallelCommentLikesBySameUserCountOnce() throws Exception {
        var commentId = newComment();
        var userId = newUser();

        runTwiceInParallel(() -> {
            likeService.likeComment(userId, commentId);
            return null;
        });

        assertThat(rows("comment_likes", "comment_id", commentId)).isEqualTo(1);
        assertThat(likeCount("comments", commentId)).isEqualTo(1);
    }

    @Test
    void aDeletedCommentCannotBeLikedButCanBeUnliked() {
        var postId = newPost();
        var authorId = newUser();
        var commentId = commentService.create(postId, authorId, "Text", null).id();
        var userId = newUser();
        likeService.likeComment(userId, commentId);
        commentService.delete(commentId, authorId);

        assertThatThrownBy(() -> likeService.likeComment(newUser(), commentId))
                .isInstanceOf(CommentDeletedException.class);
        likeService.unlikeComment(userId, commentId);
        assertThat(likeCount("comments", commentId)).isZero();
    }

    @Test
    void missingTargetsAreNotFound() {
        var userId = newUser();

        assertThatThrownBy(() -> likeService.likePost(userId, UUID.randomUUID()))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> likeService.unlikePost(userId, UUID.randomUUID()))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> likeService.likeComment(userId, UUID.randomUUID()))
                .isInstanceOf(CommentNotFoundException.class);
        assertThatThrownBy(() -> likeService.unlikeComment(userId, UUID.randomUUID()))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void findsWhichTargetsTheUserLikes() {
        var userId = newUser();
        var liked = newPost();
        var notLiked = newPost();
        likeService.likePost(userId, liked);
        likeService.likePost(newUser(), notLiked);

        assertThat(likeService.likedPostIds(userId, List.of(liked, notLiked, UUID.randomUUID())))
                .containsExactly(liked);
    }

    @Test
    void anAccountsLikesStayWhenItIsDeleted() {
        var postId = newPost();
        var userId = newUser();
        likeService.likePost(userId, postId);

        userService.delete(userId);

        assertThat(likeCount("posts", postId)).isEqualTo(1);
    }

    @Test
    void deletingAPostDeletesItsLikesAndItsCommentsLikes() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();
        var commentId = commentService.create(postId, newUser(), "Text", null).id();
        likeService.likePost(newUser(), postId);
        likeService.likeComment(newUser(), commentId);

        postService.delete(postId, authorId);

        assertThat(rows("post_likes", "post_id", postId)).isZero();
        assertThat(rows("comment_likes", "comment_id", commentId)).isZero();
    }

    private static void runTwiceInParallel(Callable<Void> action) throws Exception {
        var bothReady = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                bothReady.await();
                return action.call();
            });
            var second = executor.submit(() -> {
                bothReady.await();
                return action.call();
            });
            first.get();
            second.get();
        }
    }

    private int likeCount(String table, UUID id) {
        return jdbc.queryForObject("SELECT like_count FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private int rows(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private UUID newComment() {
        return commentService.create(newPost(), newUser(), "Text", null).id();
    }

    private UUID newPost() {
        return postService.create(newUser(), "Title", "Content").id();
    }

    private UUID newUser() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return userService.create(name + "@example.com", name, "hash").id();
    }
}
