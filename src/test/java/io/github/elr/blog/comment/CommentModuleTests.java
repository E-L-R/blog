package io.github.elr.blog.comment;

import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@code comment} module's service and its database constraints, with the
 * {@code post} and {@code user} modules it depends on.
 * <p>
 * {@code auth} is included for the security configuration that {@link CommentApiTests} needs.
 * Both classes have the same annotations, so Spring builds one application context for both.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CommentModuleTests {

    @Autowired
    CommentService commentService;

    @Autowired
    CommentRepository comments;

    @Autowired
    PostService postService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void buildsAThreadOldestFirst() {
        var postId = newPost();
        var first = commentService.create(postId, newUser(), "First", null);
        var reply = commentService.create(postId, newUser(), "Reply", first.id());
        var second = commentService.create(postId, newUser(), "Second", null);

        var thread = commentService.findThread(postId);

        assertThat(thread).extracting(CommentView::id).containsExactly(first.id(), reply.id(), second.id());
        assertThat(thread.get(1).parentId()).isEqualTo(first.id());
        assertThat(thread.get(0).parentId()).isNull();
    }

    @Test
    void aThreadOfAMissingPostIsNotFound() {
        assertThatThrownBy(() -> commentService.findThread(UUID.randomUUID()))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void cannotCommentOnAMissingPost() {
        assertThatThrownBy(() -> commentService.create(UUID.randomUUID(), newUser(), "Hello", null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void theParentMustBeOnTheSamePost() {
        var parent = commentService.create(newPost(), newUser(), "Parent", null);
        var otherPostId = newPost();

        assertThatThrownBy(() -> commentService.create(otherPostId, newUser(), "Reply", parent.id()))
                .isInstanceOf(InvalidParentException.class);
        assertThatThrownBy(() -> commentService.create(otherPostId, newUser(), "Reply", UUID.randomUUID()))
                .isInstanceOf(InvalidParentException.class);
    }

    @Test
    void softDeleteKeepsTheRepliesAndTheRow() {
        var postId = newPost();
        var authorId = newUser();
        var parent = commentService.create(postId, authorId, "Parent", null);
        var reply = commentService.create(postId, newUser(), "Reply", parent.id());

        commentService.delete(parent.id(), authorId);
        commentService.delete(parent.id(), authorId); // a second time changes nothing

        assertThat(commentService.findById(parent.id())).hasValueSatisfying(deleted -> {
            assertThat(deleted.deleted()).isTrue();
            assertThat(deleted.content()).isNull();
        });
        assertThat(commentService.findById(reply.id())).hasValueSatisfying(kept ->
                assertThat(kept.parentId()).isEqualTo(parent.id()));
    }

    @Test
    void aDeletedCommentCannotBeEditedOrRepliedTo() {
        var postId = newPost();
        var authorId = newUser();
        var comment = commentService.create(postId, authorId, "Text", null);
        commentService.delete(comment.id(), authorId);

        assertThatThrownBy(() -> commentService.edit(comment.id(), authorId, "New text"))
                .isInstanceOf(CommentDeletedException.class);
        assertThatThrownBy(() -> commentService.create(postId, newUser(), "Reply", comment.id()))
                .isInstanceOf(CommentDeletedException.class);
    }

    @Test
    void onlyTheAuthorCanEditOrDelete() {
        var comment = commentService.create(newPost(), newUser(), "Text", null);
        var otherUserId = newUser();

        assertThatThrownBy(() -> commentService.edit(comment.id(), otherUserId, "Changed"))
                .isInstanceOf(NotCommentAuthorException.class);
        assertThatThrownBy(() -> commentService.delete(comment.id(), otherUserId))
                .isInstanceOf(NotCommentAuthorException.class);
    }

    @Test
    void theBotRepliesToACommentOrToThePost() {
        var postId = newPost();
        var comment = commentService.create(postId, newUser(), "Question", null);

        var reply = commentService.createBotReply(postId, comment.id(), "Answer");
        var topLevel = commentService.createBotReply(postId, null, "Answer to the post");

        assertThat(reply.authorId()).isEqualTo(UserService.BOT_USER_ID);
        assertThat(reply.parentId()).isEqualTo(comment.id());
        assertThat(topLevel.parentId()).isNull();
    }

    @Test
    void deletingAPostDeletesItsComments() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();
        var parent = commentService.create(postId, newUser(), "Parent", null);
        commentService.create(postId, newUser(), "Reply", parent.id());

        postService.delete(postId, authorId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE post_id = ?", Integer.class, postId))
                .isZero();
    }

    // As for posts: the like count is written only by SQL, so saving an older copy keeps it.
    @Test
    void savingACommentLoadedBeforeALikeKeepsTheCount() {
        var authorId = newUser();
        var commentId = commentService.create(newPost(), authorId, "Text", null).id();
        var loadedBeforeTheLike = comments.findById(commentId).orElseThrow();

        commentService.incrementLikeCount(commentId);
        loadedBeforeTheLike.edit("New text", Instant.now());
        comments.saveAndFlush(loadedBeforeTheLike);

        assertThat(commentService.findById(commentId)).hasValueSatisfying(comment -> {
            assertThat(comment.content()).isEqualTo("New text");
            assertThat(comment.likeCount()).isEqualTo(1);
        });
    }

    @Test
    void databaseRejectsAReplyOnAnotherPostThanItsParent() {
        var parent = commentService.create(newPost(), newUser(), "Parent", null);
        var otherPostId = newPost();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO comments (id, post_id, parent_id, author_id, content, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Reply', now(), now())",
                UUID.randomUUID(), otherPostId, parent.id(), newUser()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsADeletedCommentWithContent() {
        var comment = commentService.create(newPost(), newUser(), "Text", null);

        assertThatThrownBy(() -> jdbc.update("UPDATE comments SET deleted = true WHERE id = ?", comment.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID newPost() {
        return postService.create(newUser(), "Title", "Content").id();
    }

    private UUID newUser() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return userService.create(name + "@example.com", name, "hash").id();
    }
}
