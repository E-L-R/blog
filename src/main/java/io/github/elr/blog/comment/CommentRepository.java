package io.github.elr.blog.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** A post's whole thread, oldest first. The ID breaks ties, so the order is always the same. */
    List<Comment> findAllByPostIdOrderByCreatedAtAscIdAsc(UUID postId);
}
