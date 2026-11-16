package io.github.elr.blog.post;

import io.github.elr.blog.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PostService(PostRepository posts, UserService userService, ApplicationEventPublisher events, Clock clock) {
        this.posts = posts;
        this.userService = userService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates a post, and publishes {@link PostCreatedEvent} in the same transaction.
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
        // Listeners run now, inside this transaction: what they write is saved with the post,
        // or rolled back with it (see docs/DECISIONS.md, D4).
        events.publishEvent(new PostCreatedEvent(post.getId()));
        return PostView.of(post);
    }

    public Optional<PostView> findById(UUID postId) {
        return posts.findById(postId).map(PostView::of);
    }

    public boolean exists(UUID postId) {
        return posts.existsById(postId);
    }

    /** A post's text and content hash, for the {@code ai} module's prompts. */
    public Optional<PostContent> findContent(UUID postId) {
        return posts.findById(postId).map(PostContent::of);
    }

    /**
     * Writes a TL;DR generated from the content with {@code sourceHash}, unless the content has
     * changed since. Called by the {@code ai} module.
     *
     * @return whether the TL;DR was written
     */
    @Transactional
    public boolean applyTldr(UUID postId, String tldr, String sourceHash) {
        return posts.applyTldr(postId, tldr, sourceHash, clock.instant()) == 1;
    }

    /**
     * Records that generating the TL;DR for the content with {@code sourceHash} failed for good,
     * unless the content has changed since. Called by the {@code ai} module.
     *
     * @return whether it was recorded
     */
    @Transactional
    public boolean markTldrFailed(UUID postId, String sourceHash) {
        return posts.markTldrFailed(postId, sourceHash) == 1;
    }

    /**
     * Counts one more like. Called by the {@code like} module in the same transaction as the
     * like row it inserted, so the row and the count are saved together or not at all.
     */
    @Transactional
    public void incrementLikeCount(UUID postId) {
        posts.addToLikeCount(postId, 1);
    }

    /** Counts one like less. Called by the {@code like} module, like {@link #incrementLikeCount}. */
    @Transactional
    public void decrementLikeCount(UUID postId) {
        posts.addToLikeCount(postId, -1);
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
     * Changes a post's title and content. Only the author may do this. If the content changed,
     * {@link PostContentChangedEvent} is published in the same transaction.
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
        if (post.edit(title, content, clock.instant())) {
            events.publishEvent(new PostContentChangedEvent(postId));
        }
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
            throw new NotAuthorException(postId);
        }
        return post;
    }
}
