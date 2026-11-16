package io.github.elr.blog.ai;

/**
 * How processing a claimed job went, outside any transaction. {@code AiJobFinisher} then
 * records it in one transaction.
 */
sealed interface JobOutcome {

    /**
     * The model answered.
     *
     * @param sourceHash for a TL;DR, the hash of the content the model saw
     */
    record Success(String text, String model, String sourceHash) implements JobOutcome {
    }

    /** The call failed. */
    record Failure(LlmException error, String sourceHash) implements JobOutcome {
    }

    /** The post or the comment to answer is gone or deleted, so there was nothing to call the model for. */
    record TargetGone(String reason) implements JobOutcome {
    }
}
