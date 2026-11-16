package io.github.elr.blog.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
}
