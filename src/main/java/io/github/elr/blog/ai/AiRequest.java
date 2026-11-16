package io.github.elr.blog.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One LLM call: a job in the queue, and afterwards its record.
 * <p>
 * A worker <em>claims</em> a job by taking a lease on it: until {@code lockedUntil}, no other
 * worker takes it. If the worker crashes or stalls, the lease expires and another worker claims
 * the job again. Each claim increments {@code attempts}, which doubles as the claim's number:
 * a worker may only write its result while the job is still in progress under its own number
 * ({@link #isHeldBy}). So a worker whose lease expired can never overwrite the work of the
 * worker that took over.
 */
@Entity
@Table(name = "ai_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AiRequest {

    /** The longest error message kept; errors are for operators, and some are long. */
    private static final int MAX_ERROR_LENGTH = 2_000;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private AiRequestType type;

    @Enumerated(EnumType.STRING)
    private AiRequestStatus status;

    @Column(name = "requested_by")
    private UUID requestedById;

    // Set when the request is created, and set to null by the database (ON DELETE SET NULL)
    // when the post or comment is deleted. updatable = false, so saving a request loaded
    // before such a delete can't write the deleted ID back.
    @Column(updatable = false)
    private UUID postId;

    @Column(updatable = false)
    private UUID commentId;

    private UUID resultCommentId;

    private String model;

    private int attempts;

    private Instant nextAttemptAt;

    private Instant lockedUntil;

    private String error;

    private Instant createdAt;

    private Instant completedAt;

    private AiRequest(AiRequestType type, UUID requestedById, UUID postId, UUID commentId, Instant now) {
        this.type = type;
        this.status = AiRequestStatus.PENDING;
        this.requestedById = requestedById;
        this.postId = postId;
        this.commentId = commentId;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    /** A TL;DR of the post's current content, due at once. */
    static AiRequest tldr(UUID postId, Instant now) {
        return new AiRequest(AiRequestType.TLDR, null, postId, null, now);
    }

    /**
     * A reply by the bot, due at once.
     *
     * @param commentId the comment to reply to, or {@code null} to reply to the post itself
     */
    static AiRequest reply(UUID requestedById, UUID postId, UUID commentId, Instant now) {
        return new AiRequest(AiRequestType.REPLY, requestedById, postId, commentId, now);
    }

    /** Takes the job for one attempt, until the lease ends. */
    void claim(Instant now, Duration lease) {
        requireStatus(AiRequestStatus.PENDING, AiRequestStatus.IN_PROGRESS);
        status = AiRequestStatus.IN_PROGRESS;
        attempts++;
        lockedUntil = now.plus(lease);
    }

    /** Whether the worker that made claim number {@code attempt} still holds the job. */
    boolean isHeldBy(int attempt) {
        return status == AiRequestStatus.IN_PROGRESS && attempts == attempt;
    }

    /** Whether another attempt is allowed after the attempts made so far. */
    boolean canRetry(int maxAttempts) {
        return attempts < maxAttempts;
    }

    /**
     * @param resultCommentId the bot's reply; {@code null} for a TL;DR
     * @param model           the model that produced the result
     */
    void complete(UUID resultCommentId, String model, Instant now) {
        requireStatus(AiRequestStatus.IN_PROGRESS);
        this.resultCommentId = resultCommentId;
        this.model = model;
        finish(AiRequestStatus.COMPLETED, now);
    }

    /** Puts the job back in the queue after an error that may go away, such as a timeout. */
    void retryLater(String error, Instant nextAttemptAt) {
        requireStatus(AiRequestStatus.IN_PROGRESS);
        this.status = AiRequestStatus.PENDING;
        this.lockedUntil = null;
        this.error = shorten(error);
        this.nextAttemptAt = nextAttemptAt;
    }

    void fail(String error, Instant now) {
        this.error = shorten(error);
        finish(AiRequestStatus.FAILED, now);
    }

    /** Gives the job up without a result: its target is gone, or a newer job replaces it. */
    void cancel(String reason, Instant now) {
        this.error = shorten(reason);
        finish(AiRequestStatus.CANCELLED, now);
    }

    private void finish(AiRequestStatus finalStatus, Instant now) {
        requireStatus(AiRequestStatus.PENDING, AiRequestStatus.IN_PROGRESS);
        status = finalStatus;
        lockedUntil = null;
        completedAt = now;
    }

    private void requireStatus(AiRequestStatus... allowed) {
        for (var candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Request " + id + " is " + status);
    }

    private static String shorten(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }
}
