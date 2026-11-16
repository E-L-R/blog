package io.github.elr.blog.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** A post's whole thread, oldest first. The ID breaks ties, so the order is always the same. */
    List<Comment> findAllByPostIdOrderByCreatedAtAscIdAsc(UUID postId);

    /** Adds {@code delta} to the like count in the database itself, as {@code PostRepository} does. */
    @Modifying
    @Query(value = "UPDATE comments SET like_count = like_count + :delta WHERE id = :commentId", nativeQuery = true)
    int addToLikeCount(UUID commentId, int delta);
}
