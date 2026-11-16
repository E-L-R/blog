package io.github.elr.blog.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** The same statements as {@link PostLikeRepository}, for comments. */
interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO comment_likes (id, user_id, comment_id, created_at)
            VALUES (:id, :userId, :commentId, :now)
            ON CONFLICT (user_id, comment_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, UUID commentId, Instant now);

    @Modifying
    @Query(value = "DELETE FROM comment_likes WHERE user_id = :userId AND comment_id = :commentId", nativeQuery = true)
    int deleteLike(UUID userId, UUID commentId);

    @Query("select l.commentId from CommentLike l where l.userId = :userId and l.commentId in :commentIds")
    Set<UUID> findLikedCommentIds(UUID userId, Collection<UUID> commentIds);
}
