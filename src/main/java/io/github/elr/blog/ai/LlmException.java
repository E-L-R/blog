package io.github.elr.blog.ai;

/**
 * A failed LLM call.
 * <p>
 * Retryable failures may go away by themselves: a timeout, a dropped connection, the provider
 * being overloaded (HTTP 429 or 5xx). The worker tries again later. Other failures will happen
 * again, such as a rejected request or an empty or refused answer, so the job fails at once.
 */
class LlmException extends RuntimeException {

    private final boolean retryable;

    LlmException(String message, boolean retryable) {
        this(message, retryable, null);
    }

    LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean isRetryable() {
        return retryable;
    }
}
