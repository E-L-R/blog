package io.github.elr.blog.comment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * The body of {@code POST /posts/{id}/comments}.
 *
 * @param parentId the comment to reply to, or {@code null} for a top-level comment
 */
public record CommentRequest(@NotBlank @Size(max = 10_000) String content, UUID parentId) {
}
