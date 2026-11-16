package io.github.elr.blog.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    /**
     * Inserts the like unless the user already likes the post, and returns how many rows it
     * inserted: 1, or 0 if the like was already there.
     * <p>
     * {@code ON CONFLICT DO NOTHING} makes this safe against a double click: when two inserts
     * of the same like run at once, the unique constraint makes the second wait for the first,
     * and then insert nothing. Catching the duplicate-key error instead would not work, because
     * in PostgreSQL an error aborts the whole transaction.
     */
    @Modifying
    @Query(value = """
            INSERT INTO post_likes (id, user_id, post_id, created_at)
            VALUES (:id, :userId, :postId, :now)
            ON CONFLICT (user_id, post_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, UUID postId, Instant now);

    /** Deletes the like, and returns how many rows it deleted: 1, or 0 if there was none. */
    @Modifying
    @Query(value = "DELETE FROM post_likes WHERE user_id = :userId AND post_id = :postId", nativeQuery = true)
    int deleteLike(UUID userId, UUID postId);

    /** Which of these posts the user likes. Answered from the unique index on (user_id, post_id). */
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    Set<UUID> findLikedPostIds(UUID userId, Collection<UUID> postIds);
}
