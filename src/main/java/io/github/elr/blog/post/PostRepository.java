package io.github.elr.blog.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Both lists are sorted newest first. Two posts can have the same {@code createdAt}, so the
 * ID breaks ties: without a unique sort order, a post could appear on two pages, or on none.
 */
interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Post> findAllByAuthorIdOrderByCreatedAtDescIdDesc(UUID authorId, Pageable pageable);
}
