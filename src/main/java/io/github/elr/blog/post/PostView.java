package io.github.elr.blog.post;

import java.time.Instant;
import java.util.UUID;

/**
 * A post as other modules and the web layer see it. It is an immutable copy of the entity's
 * data, so callers cannot change a post by accident, and the entity never leaves the module.
 *
 * @param tldr the most recent TL;DR, possibly of an older version of the content (see
 *             {@code tldrStatus}), or {@code null} if none was generated yet
 */
public record PostView(
        UUID id,
        UUID authorId,
        String title,
        String content,
        String tldr,
        TldrStatus tldrStatus,
        int likeCount,
        Instant createdAt,
        Instant updatedAt
) {

    static PostView of(Post post) {
        return new PostView(
                post.getId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                post.getTldr(),
                post.tldrStatus(),
                post.getLikeCount(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
