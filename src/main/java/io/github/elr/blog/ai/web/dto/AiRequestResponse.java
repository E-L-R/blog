package io.github.elr.blog.ai.web.dto;

import io.github.elr.blog.ai.AiRequestStatus;
import io.github.elr.blog.ai.AiRequestView;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI request as the API returns it. Clients poll it until the status is final, then load
 * the bot's reply with {@code GET /comments/{resultCommentId}}. The reason for a failure is
 * not exposed.
 */
public record AiRequestResponse(
        UUID id,
        AiRequestStatus status,
        UUID postId,
        UUID commentId,
        UUID resultCommentId,
        Instant createdAt,
        Instant completedAt
) {

    public static AiRequestResponse of(AiRequestView request) {
        return new AiRequestResponse(
                request.id(),
                request.status(),
                request.postId(),
                request.commentId(),
                request.resultCommentId(),
                request.createdAt(),
                request.completedAt());
    }
}
