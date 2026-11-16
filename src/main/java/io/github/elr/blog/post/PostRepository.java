package io.github.elr.blog.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * Both lists are sorted newest first. Two posts can have the same {@code createdAt}, so the
 * ID breaks ties: without a unique sort order, a post could appear on two pages, or on none.
 */
interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Post> findAllByAuthorIdOrderByCreatedAtDescIdDesc(UUID authorId, Pageable pageable);

    /**
     * Adds {@code delta} to the like count in the database itself, so two likes at the same
     * moment both count: each one adds to the value the other one wrote. Reading the count,
     * adding one and saving it would lose one of them.
     */
    @Modifying
    @Query(value = "UPDATE posts SET like_count = like_count + :delta WHERE id = :postId", nativeQuery = true)
    int addToLikeCount(UUID postId, int delta);

    /**
     * Writes a TL;DR, but only if the post's content still has the hash the TL;DR was
     * generated from. The condition is part of the UPDATE, so it also holds against an edit
     * at the same moment: PostgreSQL makes the UPDATE wait for that edit to commit, and then
     * checks the condition again against the new content.
     *
     * @return 1 if the TL;DR was written, 0 if the content changed in the meantime
     */
    @Modifying
    @Query(value = """
            UPDATE posts SET tldr = :tldr, tldr_source_hash = :sourceHash, tldr_generated_at = :now
            WHERE id = :postId AND content_hash = :sourceHash
            """, nativeQuery = true)
    int applyTldr(UUID postId, String tldr, String sourceHash, Instant now);

    /** Marks the TL;DR of this version of the content as failed for good, with the same condition. */
    @Modifying
    @Query(value = """
            UPDATE posts SET tldr_failed_hash = :sourceHash
            WHERE id = :postId AND content_hash = :sourceHash
            """, nativeQuery = true)
    int markTldrFailed(UUID postId, String sourceHash);
}
