package io.github.elr.blog.ai;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AiRequestRepository extends JpaRepository<AiRequest, UUID> {

    /**
     * Locks up to {@code limit} due jobs: pending ones whose time has come, and running ones
     * whose lease has expired.
     * <p>
     * {@code FOR UPDATE} locks the rows until the transaction ends. {@code SKIP LOCKED} makes
     * other workers skip rows that are locked instead of waiting for them, so workers on several
     * instances can claim at the same moment and never get the same job. The time comes from the
     * application's {@code Clock}, not the database's {@code now()}, so tests control it.
     */
    @Query(value = """
            SELECT * FROM ai_requests
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'IN_PROGRESS' AND locked_until <= :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AiRequest> lockDueJobs(Instant now, int limit);

    /** Loads a request and locks its row until the transaction ends, so two workers can't finish it at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AiRequest r where r.id = :id")
    Optional<AiRequest> findByIdForUpdate(UUID id);

    /**
     * Whether a newer TL;DR request exists for the post. Every content change creates one, so
     * if none exists, the content hasn't changed since this request was made. The row
     * comparison orders by creation time, then by ID for requests created at the same moment.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM ai_requests
                WHERE type = 'TLDR' AND post_id = :postId AND (created_at, id) > (:createdAt, :id)
            )
            """, nativeQuery = true)
    boolean existsNewerTldr(UUID postId, Instant createdAt, UUID id);

    /** How many jobs wait or run, by type and status, for the queue-size metric. */
    @Query("""
            select r.type as type, r.status as status, count(r) as count
            from AiRequest r
            where r.status in (io.github.elr.blog.ai.AiRequestStatus.PENDING, io.github.elr.blog.ai.AiRequestStatus.IN_PROGRESS)
            group by r.type, r.status
            """)
    List<QueueCount> countUnfinished();

    interface QueueCount {
        AiRequestType getType();

        AiRequestStatus getStatus();

        long getCount();
    }
}
