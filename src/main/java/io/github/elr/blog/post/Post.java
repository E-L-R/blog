package io.github.elr.blog.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A blog post. Like every entity, it has no setters: the constructor and {@link #edit}
 * are the only ways to set its fields, and both check the rules.
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Post {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    // The author is stored by ID, not as a @ManyToOne User: User belongs to another module,
    // and modules refer to each other's entities by ID only. The database still has a real
    // foreign key (posts_author_fkey).
    private UUID authorId;

    private String title;

    private String content;

    // The SHA-256 of the content, kept in step with it. A TL;DR is only applied if it was
    // generated from content with the current hash (see docs/DECISIONS.md, D5). The hashes are
    // char(64) columns, which JPA would otherwise expect to be varchar.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String contentHash;

    // The TL;DR columns are written only by SQL (PostService.applyTldr and markTldrFailed),
    // with the same reason as the like count below.
    @Column(insertable = false, updatable = false)
    private String tldr;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, insertable = false, updatable = false)
    private String tldrSourceHash;

    @Column(insertable = false, updatable = false)
    private Instant tldrGeneratedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64, insertable = false, updatable = false)
    private String tldrFailedHash;

    // Written only by SQL (PostService.incrementLikeCount), never by saving the entity: without
    // insertable/updatable = false, saving a post loaded before a like would write back the old count.
    @Column(insertable = false, updatable = false)
    private int likeCount;

    // Optimistic locking. Hibernate adds "WHERE version = ?" to every update and increments
    // the version. If someone else saved the post first, the row no longer matches, and the
    // save fails with OptimisticLockingFailureException instead of overwriting their edit.
    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    Post(UUID authorId, String title, String content, Instant now) {
        this.authorId = authorId;
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.contentHash = ContentHash.of(this.content);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Changes the title and content.
     *
     * @return whether the content changed. A title-only edit keeps the TL;DR, because the
     *         TL;DR summarises the content only.
     */
    boolean edit(String title, String content, Instant now) {
        this.title = requireText(title, "title");
        var newContent = requireText(content, "content");
        var contentChanged = !newContent.equals(this.content);
        this.content = newContent;
        this.contentHash = ContentHash.of(newContent);
        this.updatedAt = now;
        return contentChanged;
    }

    /**
     * Whether the TL;DR matches the current content. Derived from the hashes every time,
     * never stored, so it can't disagree with the content.
     */
    TldrStatus tldrStatus() {
        if (contentHash.equals(tldrSourceHash)) {
            return TldrStatus.GENERATED;
        }
        if (contentHash.equals(tldrFailedHash)) {
            return TldrStatus.FAILED;
        }
        return TldrStatus.PENDING;
    }

    boolean isAuthoredBy(UUID userId) {
        return authorId.equals(userId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
