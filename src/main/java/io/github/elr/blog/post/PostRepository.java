package io.github.elr.blog.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Post> findAllByAuthorIdOrderByCreatedAtDescIdDesc(UUID authorId, Pageable pageable);
}
