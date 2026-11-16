package io.github.elr.blog.comment;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment as other modules and the web layer see it.
 *
 * @param parentId {@code null} for a top-level comment
 * @param content  {@code null} if the comment is deleted
 */
public record CommentView(
        UUID id,
        UUID postId,
        UUID parentId,
        UUID authorId,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {

    static CommentView of(Comment comment) {
        return new CommentView(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
