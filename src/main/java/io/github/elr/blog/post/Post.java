package io.github.elr.blog.post;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

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
        this.createdAt = now;
        this.updatedAt = now;
    }

    void edit(String title, String content, Instant now) {
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.updatedAt = now;
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
