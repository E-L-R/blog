package io.github.elr.blog.like;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One user's like of one comment. Written only by {@link CommentLikeRepository}'s SQL, like {@link PostLike}. */
@Entity
@Table(name = "comment_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CommentLike {

    @Id
    private UUID id;

    private UUID userId;

    private UUID commentId;

    private Instant createdAt;
}
