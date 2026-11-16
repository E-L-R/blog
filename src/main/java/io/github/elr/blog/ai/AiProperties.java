package io.github.elr.blog.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * The {@code blog.ai.*} settings. The checks run while the record is created at startup, so a
 * bad combination stops the application instead of misbehaving later.
 *
 * @param fake   use {@link FakeLlmClient} instead of calling a real model
 * @param worker how the background worker polls and retries
 * @param llm    how the model is called
 * @param prompt how prompts are built
 */
@ConfigurationProperties("blog.ai")
record AiProperties(
        @DefaultValue("false") boolean fake,
        @DefaultValue Worker worker,
        @DefaultValue Llm llm,
        @DefaultValue Prompt prompt
) {

    AiProperties {
        // A lease shorter than a call would let a second worker claim a job whose first call is
        // still running, and both would call the model.
        if (worker.lease().compareTo(llm.timeout()) <= 0) {
            throw new IllegalArgumentException("blog.ai.worker.lease (" + worker.lease()
                    + ") must be longer than blog.ai.llm.timeout (" + llm.timeout() + ")");
        }
    }

    /**
     * @param enabled        run the worker on this instance
     * @param pollInterval   the pause between two polls for due jobs
     * @param concurrency    the most jobs claimed per poll, all processed in parallel
     * @param lease          how long a claim lasts before another worker may take the job
     * @param maxAttempts    attempts before a job fails for good
     * @param initialBackoff the wait before the first retry; it doubles after each attempt
     */
    record Worker(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2s") Duration pollInterval,
            @DefaultValue("4") int concurrency,
            @DefaultValue("2m") Duration lease,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("30s") Duration initialBackoff
    ) {

        Worker {
            if (concurrency < 1 || maxAttempts < 1) {
                throw new IllegalArgumentException("blog.ai.worker.concurrency and max-attempts must be at least 1");
            }
        }

        /** The wait after a failed attempt: the initial backoff, doubled for each earlier attempt. */
        Duration backoffAfter(int attempt) {
            return initialBackoff.multipliedBy(1L << Math.min(attempt - 1, 20));
        }
    }

    /**
     * @param model           the Claude model that writes TL;DRs and replies
     * @param effort          how much the model thinks before answering: low, medium, high, xhigh or max
     * @param timeout         the longest one call may take
     * @param maxOutputTokens the most tokens the model may produce in one answer, thinking included
     */
    record Llm(
            @DefaultValue("claude-opus-5-5") String model,
            @DefaultValue("medium") String effort,
            @DefaultValue("60s") Duration timeout,
            @DefaultValue("4096") long maxOutputTokens
    ) {
    }

    /**
     * @param maxInputChars the size budget for the user content in one prompt
     * @param maxAnswerChars the longest answer kept: longer answers are cut. The same as the
     *                       longest comment a user may write.
     */
    record Prompt(
            @DefaultValue("20000") int maxInputChars,
            @DefaultValue("10000") int maxAnswerChars
    ) {
    }
}
