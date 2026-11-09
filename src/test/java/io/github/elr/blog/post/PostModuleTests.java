package io.github.elr.blog.post;

import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
class PostModuleTests {

    @Autowired
    PostService postService;

    @Autowired
    PostRepository posts;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsPostAndFindsIt() {
        var authorId = newUser();

        var created = postService.create(authorId, "Hello", "My first post");

        assertThat(postService.findById(created.id())).hasValueSatisfying(post -> {
            assertThat(post.authorId()).isEqualTo(authorId);
            assertThat(post.title()).isEqualTo("Hello");
            assertThat(post.content()).isEqualTo("My first post");
        });
    }

    @Test
    void rejectsPostByUnknownAuthor() {
        assertThatThrownBy(() -> postService.create(UUID.randomUUID(), "Title", "Content"))
                .isInstanceOf(AuthorNotFoundException.class);
    }

    @Test
    void rejectsPostByDeletedAuthor() {
        var authorId = newUser();
        markDeleted(authorId);

        assertThatThrownBy(() -> postService.create(authorId, "Title", "Content"))
                .isInstanceOf(AuthorNotFoundException.class);
    }

    @Test
    void rejectsBlankTitle() {
        var authorId = newUser();

        assertThatThrownBy(() -> postService.create(authorId, "   ", "Content"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authorCanEditTheirPost() {
        var authorId = newUser();
        var post = postService.create(authorId, "Title", "Content");

        postService.edit(post.id(), authorId, "New title", "New content");

        assertThat(postService.findById(post.id())).hasValueSatisfying(saved -> {
            assertThat(saved.title()).isEqualTo("New title");
            assertThat(saved.content()).isEqualTo("New content");
        });
    }

    @Test
    void otherUserCannotEditThePost() {
        var post = postService.create(newUser(), "Title", "Content");
        var otherUserId = newUser();

        assertThatThrownBy(() -> postService.edit(post.id(), otherUserId, "Changed", "Changed"))
                .isInstanceOf(NotAuthorException.class);
        assertThat(postService.findById(post.id()))
                .hasValueSatisfying(saved -> assertThat(saved.title()).isEqualTo("Title"));
    }

    @Test
    void editingAMissingPostFails() {
        var userId = newUser();

        assertThatThrownBy(() -> postService.edit(UUID.randomUUID(), userId, "Title", "Content"))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void authorCanDeleteTheirPost() {
        var authorId = newUser();
        var post = postService.create(authorId, "Title", "Content");

        postService.delete(post.id(), authorId);

        assertThat(postService.findById(post.id())).isEmpty();
    }

    @Test
    void listsAnAuthorsPostsNewestFirst() {
        var authorId = newUser();
        var first = postService.create(authorId, "First", "Content").id();
        var second = postService.create(authorId, "Second", "Content").id();
        var third = postService.create(authorId, "Third", "Content").id();
        postService.create(newUser(), "Someone else's post", "Content");

        var page = postService.listByAuthor(authorId, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PostView::id).containsExactly(third, second, first);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void listsNothingForADeletedAuthor() {
        var authorId = newUser();
        postService.create(authorId, "Title", "Content");
        markDeleted(authorId);

        assertThat(postService.listByAuthor(authorId, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void detectsTwoEditsAtTheSameTime() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var firstCopy = posts.findById(postId).orElseThrow();
        var secondCopy = posts.findById(postId).orElseThrow();

        firstCopy.edit("First edit", "Content", Instant.now());
        posts.save(firstCopy);

        secondCopy.edit("Second edit", "Content", Instant.now());
        assertThatThrownBy(() -> posts.save(secondCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void databaseRejectsPostWithUnknownAuthor() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO posts (id, author_id, title, content, created_at, updated_at) "
                        + "VALUES (?, ?, 'Title', 'Content', now(), now())",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID newUser() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return userService.create(name + "@example.com", name, "hash").id();
    }

    private void markDeleted(UUID userId) {
        jdbc.update("UPDATE users SET deleted_at = now(), email = NULL, password_hash = NULL WHERE id = ?", userId);
    }
}
