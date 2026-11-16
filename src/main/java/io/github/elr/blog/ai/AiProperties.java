package io.github.elr.blog.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * The {@code blog.ai.*} settings. The checks run while the record is created at startup, so a
 * bad combination stops the application instead of misbehaving later.
 *
 * @param fake     use {@link FakeLlmClient} instead of calling a real model; it wins over {@code provider}
 * @param provider who runs the model: Anthropic (Claude) or a local Ollama server
 * @param worker   how the background worker polls and retries
 * @param llm      how the model is called
 * @param ollama   where the Ollama server is, when {@code provider} is {@code ollama}
 * @param prompt   how prompts are built
 */
@ConfigurationProperties("blog.ai")
record AiProperties(
        @DefaultValue("false") boolean fake,
        @DefaultValue("anthropic") Provider provider,
        @DefaultValue Worker worker,
        @DefaultValue Llm llm,
        @DefaultValue Ollama ollama,
        @DefaultValue Prompt prompt
) {

    /** Who runs the model. Spring binds {@code anthropic} and {@code ollama}, in any case. */
    enum Provider {
        ANTHROPIC,
        OLLAMA
    }

    AiProperties {
        // The default model is a Claude model, which Ollama doesn't have. Without this check,
        // every call would fail with "model not found", and only the logs would say why.
        if (!fake && provider == Provider.OLLAMA && llm.model().startsWith("claude-")) {
            throw new IllegalArgumentException("blog.ai.provider is ollama, but blog.ai.llm.model is "
                    + llm.model() + ": set it to a model pulled into Ollama, such as llama3.2");
        }
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
     * @param model           the model that writes TL;DRs and replies: a Claude model, or with
     *                        Ollama, the name of a pulled model such as {@code llama3.2}
     * @param effort          how much Claude thinks before answering: low, medium, high, xhigh or
     *                        max. Ollama ignores it.
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
     * @param baseUrl the Ollama server's address
     */
    record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl
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
