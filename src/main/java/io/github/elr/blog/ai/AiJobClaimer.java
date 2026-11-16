package io.github.elr.blog.ai;

import io.github.elr.blog.post.PostService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The worker's first step: claiming due jobs, in one short transaction. Jobs that need no LLM
 * call are settled here instead: their post is gone, a newer TL;DR request replaces them, or
 * their last attempt crashed or stalled and no attempts are left.
 */
@Component
class AiJobClaimer {

    private final AiRequestRepository requests;
    private final PostService postService;
    private final AiProperties properties;
    private final AiMetrics metrics;
    private final Clock clock;

    AiJobClaimer(AiRequestRepository requests, PostService postService, AiProperties properties,
                 AiMetrics metrics, Clock clock) {
        this.requests = requests;
        this.postService = postService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Claims up to {@code blog.ai.worker.concurrency} due jobs. The row locks taken by the
     * query are held until this method's transaction commits, so no other worker can claim the
     * same jobs; after that, the lease keeps them away.
     *
     * @return the jobs this worker now holds
     */
    @Transactional
    List<ClaimedJob> claimDueJobs() {
        var now = clock.instant();
        var worker = properties.worker();
        var claimed = new ArrayList<ClaimedJob>();
        for (var request : requests.lockDueJobs(now, worker.concurrency())) {
            if (!request.canRetry(worker.maxAttempts())) {
                // Only possible for a job whose lease expired: its worker crashed or stalled
                // on every attempt.
                failForGood(request, now);
            } else if (request.getPostId() == null) {
                request.cancel("The post was deleted", now);
            } else if (isSuperseded(request)) {
                request.cancel("A newer TL;DR request replaces this one", now);
            } else {
                request.claim(now, worker.lease());
                claimed.add(ClaimedJob.of(request));
            }
            metrics.countFinishedAfterCommit(request.getType(), request.getStatus());
        }
        return claimed;
    }

    private void failForGood(AiRequest request, Instant now) {
        request.fail("The lease expired and no attempts are left", now);
        if (request.getType() == AiRequestType.TLDR && request.getPostId() != null && !isSuperseded(request)) {
            // Not superseded means the content is still the one this job was for.
            postService.findContent(request.getPostId())
                    .ifPresent(content -> postService.markTldrFailed(content.postId(), content.contentHash()));
        }
    }

    /** A TL;DR request is superseded when a newer one exists for the same post: only the newest needs to run. */
    private boolean isSuperseded(AiRequest request) {
        return request.getType() == AiRequestType.TLDR
                && requests.existsNewerTldr(request.getPostId(), request.getCreatedAt(), request.getId());
    }
}
