package io.github.elr.blog.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment on a post, or a reply to another comment. Comments form a tree through
 * {@code parent}, and every comment also stores its {@code postId}, so a whole thread loads
 * with one query.
 * <p>
 * Deleting a comment is a soft delete: the row stays, without its content, so its replies
 * stay attached and clients show it as "[deleted]".
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Comment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    // The post belongs to another module, so it is referred to by ID (see docs/DECISIONS.md, D2).
    private UUID postId;

    // The parent is in this module, so it is a real JPA relationship. LAZY: the parent is only
    // loaded from the database when it is actually used.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    private UUID authorId;

    private String content;

    private boolean deleted;

    // Written only by SQL (CommentService.incrementLikeCount), as on Post.
    @Column(insertable = false, updatable = false)
    private int likeCount;

    // Optimistic locking, as on Post: two edits saved at the same moment can't overwrite each other.
    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    private Comment(UUID postId, Comment parent, UUID authorId, String content, Instant now) {
        this.postId = postId;
        this.parent = parent;
        this.authorId = authorId;
        this.content = requireText(content);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A comment directly on a post. */
    static Comment topLevel(UUID postId, UUID authorId, String content, Instant now) {
        return new Comment(postId, null, authorId, content, now);
    }

    /**
     * A reply to this comment, on the same post. A deleted comment can't get new replies.
     *
     * @throws CommentDeletedException if this comment is deleted
     */
    Comment reply(UUID authorId, String content, Instant now) {
        if (deleted) {
            throw new CommentDeletedException(id);
        }
        return new Comment(postId, this, authorId, content, now);
    }

    /** @throws CommentDeletedException if the comment is deleted */
    void edit(String content, Instant now) {
        if (deleted) {
            throw new CommentDeletedException(id);
        }
        this.content = requireText(content);
        this.updatedAt = now;
    }

    /** Blanks the comment, keeping the row for its replies. Deleting twice changes nothing. */
    void softDelete(Instant now) {
        if (deleted) {
            return;
        }
        deleted = true;
        content = null;
        updatedAt = now;
    }

    boolean isTopLevel() {
        return parent == null;
    }

    boolean isAuthoredBy(UUID userId) {
        return authorId.equals(userId);
    }

    /**
     * The parent's ID, or {@code null} for a top-level comment. Reading the ID of a lazy
     * parent does not load it from the database: Hibernate already knows the ID.
     */
    UUID getParentId() {
        return parent == null ? null : parent.getId();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return value;
    }
}
