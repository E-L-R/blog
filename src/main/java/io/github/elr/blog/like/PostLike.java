package io.github.elr.blog.like;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's like of one post. Rows are only written by the SQL statements in
 * {@link PostLikeRepository}, so this entity is used for reading; it has no constructor
 * that creates a like.
 */
@Entity
@Table(name = "post_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PostLike {

    @Id
    private UUID id;

    private UUID userId;

    private UUID postId;

    private Instant createdAt;
}
