package io.github.elr.blog.post.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /posts} and {@code PUT /posts/{id}}. */
public record PostRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 50_000)
        String content
) {
}
