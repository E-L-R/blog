package io.github.elr.blog.ai;

import io.github.elr.blog.comment.CommentDeletedException;
import io.github.elr.blog.comment.CommentNotFoundException;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Processes AI jobs: claim, call the model, finish (see docs/ARCHITECTURE.md, "Async AI
 * processing").
 * <ol>
 *   <li>Claim due jobs in one short transaction ({@link AiJobClaimer}).</li>
 *   <li>For each job, on its own virtual thread: read the inputs, build the prompt and call
 *       the model, with a timeout. No transaction or lock is held during the call.</li>
 *   <li>Record each outcome in its own transaction ({@link AiJobFinisher}).</li>
 * </ol>
 * {@link #pollOnce} runs one round. {@code AiWorkerScheduler} calls it on a fixed delay;
 * tests call it directly.
 */
@Component
class AiWorker {

    private static final Logger log = LoggerFactory.getLogger(AiWorker.class);

    private final AiJobClaimer claimer;
    private final AiJobFinisher finisher;
    private final AiRequestRepository requests;
    private final PostService postService;
    private final CommentService commentService;
    private final LlmClient llm;
    private final PromptBuilder prompts;
    private final AiProperties properties;
    private final AiMetrics metrics;

    /** Runs the LLM calls, so a call that doesn't return in time can be abandoned. */
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    AiWorker(AiJobClaimer claimer, AiJobFinisher finisher, AiRequestRepository requests, PostService postService,
             CommentService commentService, LlmClient llm, PromptBuilder prompts, AiProperties properties,
             AiMetrics metrics) {
        this.claimer = claimer;
        this.finisher = finisher;
        this.requests = requests;
        this.postService = postService;
        this.commentService = commentService;
        this.llm = llm;
        this.prompts = prompts;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Claims due jobs and processes them all in parallel, returning when every one is finished. */
    void pollOnce() {
        metrics.updateQueue(requests.countUnfinished());
        var jobs = claimer.claimDueJobs();
        // Closing the executor waits for all its tasks. Each job gets its own virtual thread, so
        // every job starts at once and its lease only has to cover one call.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            jobs.forEach(job -> executor.submit(() -> process(job)));
        }
    }

    private void process(ClaimedJob job) {
        try {
            var outcome = run(job);
            try {
                finisher.finish(job, outcome);
            } catch (CommentDeletedException | CommentNotFoundException | PostNotFoundException
                     | DataIntegrityViolationException exception) {
                // The target was deleted while the result was being written; that transaction
                // rolled back, so record the cancellation in a new one.
                finisher.cancel(job, "The target was deleted");
            }
        } catch (RuntimeException exception) {
            // The job stays IN_PROGRESS; when its lease expires, a worker claims it again.
            log.error("Processing AI request {} failed", job.id(), exception);
        }
    }

    private JobOutcome run(ClaimedJob job) {
        var post = postService.findContent(job.postId()).orElse(null);
        if (post == null) {
            return new JobOutcome.TargetGone("The post was deleted");
        }
        LlmPrompt prompt;
        String sourceHash = null;
        if (job.type() == AiRequestType.TLDR) {
            prompt = prompts.tldr(post.content());
            sourceHash = post.contentHash();
        } else if (job.commentId() == null) {
            prompt = prompts.reply(post, List.of(), null);
        } else {
            var target = commentService.findById(job.commentId()).orElse(null);
            if (target == null || target.deleted()) {
                return new JobOutcome.TargetGone("The comment was deleted");
            }
            prompt = prompts.reply(post, commentService.findAncestors(job.commentId()), target);
        }
        try {
            var reply = metrics.timeCall(job.type(), () -> call(prompt));
            return new JobOutcome.Success(cut(reply.text()), reply.model(), sourceHash);
        } catch (LlmException exception) {
            return new JobOutcome.Failure(exception, sourceHash);
        }
    }

    /** Calls the model, giving up after {@code blog.ai.llm.timeout}. */
    private LlmReply call(LlmPrompt prompt) {
        var timeout = properties.llm().timeout();
        var future = CompletableFuture.supplyAsync(() -> llm.generate(prompt), calls);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new LlmException("The call timed out after " + timeout, true, exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof LlmException llmException) {
                throw llmException;
            }
            throw new LlmException("The call failed: " + exception.getCause(), true, exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException("The call was interrupted", true, exception);
        }
    }

    /** Answers are stored like any comment, so they keep to the longest comment length. */
    private String cut(String text) {
        var max = properties.prompt().maxAnswerChars();
        return text.length() <= max ? text : text.substring(0, max);
    }

    @PreDestroy
    void stop() {
        calls.shutdownNow();
    }
}
