package io.github.elr.blog.ai;

import java.util.UUID;

/**
 * What a worker needs to know about a job it claimed, copied out of the claiming transaction.
 *
 * @param attempt the claim's number; the worker may only finish the job while it still holds
 *                this claim (see {@code AiRequest.isHeldBy})
 */
record ClaimedJob(UUID id, AiRequestType type, int attempt, UUID postId, UUID commentId) {

    static ClaimedJob of(AiRequest request) {
        return new ClaimedJob(request.getId(), request.getType(), request.getAttempts(),
                request.getPostId(), request.getCommentId());
    }
}
