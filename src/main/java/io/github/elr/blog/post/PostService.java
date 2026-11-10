package io.github.elr.blog.post;

import io.github.elr.blog.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * The public API of the {@code post} module. It returns {@link PostView} read models, never
 * the {@code Post} entity.
 * <p>
 * Methods are read-only transactions by default; a method that writes overrides that
 * with its own {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository posts;
    private final UserService userService;
    private final Clock clock;

    PostService(PostRepository posts, UserService userService, Clock clock) {
        this.posts = posts;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * Creates a post.
     *
     * @throws AuthorNotFoundException  if the author does not exist or their account is deleted
     * @throws IllegalArgumentException if the title or content is blank
     */
    @Transactional
    public PostView create(UUID authorId, String title, String content) {
        // The foreign key alone is not enough: a deleted account's row still exists, so the
        // database would accept a post by it. UserService treats deleted accounts as missing.
        if (userService.findById(authorId).isEmpty()) {
            throw new AuthorNotFoundException(authorId);
        }
        var post = posts.save(new Post(authorId, title, content, clock.instant()));
        return PostView.of(post);
    }

    public Optional<PostView> findById(UUID postId) {
        return posts.findById(postId).map(PostView::of);
    }

    /** All posts, newest first. */
    public Page<PostView> list(Pageable pageable) {
        return posts.findAllByOrderByCreatedAtDescIdDesc(pageable).map(PostView::of);
    }

    /**
     * One author's posts, newest first. A deleted account lists nothing, although each of
     * its posts can still be read by its ID.
     */
    public Page<PostView> listByAuthor(UUID authorId, Pageable pageable) {
        if (userService.findById(authorId).isEmpty()) {
            return Page.empty(pageable);
        }
        return posts.findAllByAuthorIdOrderByCreatedAtDescIdDesc(authorId, pageable).map(PostView::of);
    }

    /**
     * Changes a post's title and content. Only the author may do this.
     *
     * @throws PostNotFoundException    if the post does not exist
     * @throws NotAuthorException       if the user is not the author
     * @throws IllegalArgumentException if the title or content is blank
     */
    @Transactional
    public PostView edit(UUID postId, UUID userId, String title, String content) {
        var post = findOwnPost(postId, userId);
        // No save() needed: the post was loaded in this transaction, so JPA notices the change
        // ("dirty checking") and writes it when the transaction commits.
        post.edit(title, content, clock.instant());
        return PostView.of(post);
    }

    /**
     * Deletes a post. Only the author may do this.
     *
     * @throws PostNotFoundException if the post does not exist
     * @throws NotAuthorException    if the user is not the author
     */
    @Transactional
    public void delete(UUID postId, UUID userId) {
        posts.delete(findOwnPost(postId, userId));
    }

    private Post findOwnPost(UUID postId, UUID userId) {
        var post = posts.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.isAuthoredBy(userId)) {
            throw new NotAuthorException(postId, userId);
        }
        return post;
    }
}
