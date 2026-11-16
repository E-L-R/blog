package io.github.elr.blog.ai;

import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The worker's last step: recording a job's outcome, in one transaction per job.
 * <p>
 * It first locks the job's row and checks that the worker still holds the job. If the lease
 * expired and another worker claimed the job in the meantime, this result is discarded, so a
 * job never gets two results, and a reply never two bot comments.
 */
@Component
class AiJobFinisher {

    private static final Logger log = LoggerFactory.getLogger(AiJobFinisher.class);

    private final AiRequestRepository requests;
    private final PostService postService;
    private final CommentService commentService;
    private final AiProperties properties;
    private final AiMetrics metrics;
    private final Clock clock;

    AiJobFinisher(AiRequestRepository requests, PostService postService, CommentService commentService,
                  AiProperties properties, AiMetrics metrics, Clock clock) {
        this.requests = requests;
        this.postService = postService;
        this.commentService = commentService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Writes the outcome. If writing the result fails because the target was deleted at that
     * very moment, the exception rolls this transaction back, and the worker calls
     * {@link #cancel} in a new one.
     */
    @Transactional
    void finish(ClaimedJob job, JobOutcome outcome) {
        var request = requests.findByIdForUpdate(job.id()).orElseThrow();
        if (!request.isHeldBy(job.attempt())) {
            log.info("Discarding the result of AI request {} attempt {}: another worker took it over", job.id(), job.attempt());
            return;
        }
        var now = clock.instant();
        if (request.getPostId() == null) {
            // The database cleared post_id: the post was deleted while the model was working.
            request.cancel("The post was deleted", now);
        } else {
            switch (outcome) {
                case JobOutcome.TargetGone gone -> request.cancel(gone.reason(), now);
                case JobOutcome.Success success -> writeResult(request, success);
                case JobOutcome.Failure failure -> recordFailure(request, failure);
            }
        }
        metrics.countFinishedAfterCommit(request.getType(), request.getStatus());
    }

    /** Cancels a job whose target vanished while its result was being written. */
    @Transactional
    void cancel(ClaimedJob job, String reason) {
        var request = requests.findByIdForUpdate(job.id()).orElseThrow();
        if (request.isHeldBy(job.attempt())) {
            request.cancel(reason, clock.instant());
            metrics.countFinishedAfterCommit(request.getType(), request.getStatus());
        }
    }

    private void writeResult(AiRequest request, JobOutcome.Success success) {
        var now = clock.instant();
        if (request.getType() == AiRequestType.TLDR) {
            // Writes nothing if the post was edited since the model read it. The job is still
            // complete: the edit queued a newer job, which will produce the right TL;DR.
            postService.applyTldr(request.getPostId(), success.text(), success.sourceHash());
            request.complete(null, success.model(), now);
        } else {
            var reply = commentService.createBotReply(request.getPostId(), request.getCommentId(), success.text());
            request.complete(reply.id(), success.model(), now);
        }
    }

    private void recordFailure(AiRequest request, JobOutcome.Failure failure) {
        var now = clock.instant();
        var worker = properties.worker();
        var error = failure.error();
        if (error.isRetryable() && request.canRetry(worker.maxAttempts())) {
            request.retryLater(error.getMessage(), now.plus(worker.backoffAfter(request.getAttempts())));
            return;
        }
        request.fail(error.getMessage(), now);
        if (request.getType() == AiRequestType.TLDR && failure.sourceHash() != null) {
            postService.markTldrFailed(request.getPostId(), failure.sourceHash());
        }
    }
}
