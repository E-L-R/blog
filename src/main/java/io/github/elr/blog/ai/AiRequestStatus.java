package io.github.elr.blog.ai;

/**
 * Where a request is in its life. {@code COMPLETED}, {@code FAILED} and {@code CANCELLED} are
 * final. The transitions are in docs/DOMAIN.md, "AiRequest".
 */
public enum AiRequestStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED;

    boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
