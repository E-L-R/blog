package io.github.elr.blog.post;

import java.time.Instant;
import java.util.UUID;

public record PostView(
        UUID id,
        UUID authorId,
        String title,
        String content,
        Instant createdAt,
        Instant updatedAt
) {

    static PostView of(Post post) {
        return new PostView(
                post.getId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
