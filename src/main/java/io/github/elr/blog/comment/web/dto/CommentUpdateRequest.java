package io.github.elr.blog.comment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code PUT /comments/{id}}. A comment can't move to another parent. */
public record CommentUpdateRequest(@NotBlank @Size(max = 10_000) String content) {
}
