package io.github.elr.blog.ai;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI request as the web layer sees it.
 *
 * @param postId          {@code null} once the post is deleted
 * @param commentId       the comment the AI was asked about, or {@code null} for the post itself
 * @param resultCommentId the bot's reply, once the request is completed
 */
public record AiRequestView(
        UUID id,
        AiRequestType type,
        AiRequestStatus status,
        UUID postId,
        UUID commentId,
        UUID resultCommentId,
        Instant createdAt,
        Instant completedAt
) {

    static AiRequestView of(AiRequest request) {
        return new AiRequestView(
                request.getId(),
                request.getType(),
                request.getStatus(),
                request.getPostId(),
                request.getCommentId(),
                request.getResultCommentId(),
                request.getCreatedAt(),
                request.getCompletedAt());
    }
}
