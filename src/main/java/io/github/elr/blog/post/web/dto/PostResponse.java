package io.github.elr.blog.post.web.dto;

import io.github.elr.blog.post.PostView;
import io.github.elr.blog.post.TldrStatus;
import io.github.elr.blog.user.UserView;

import java.time.Instant;
import java.util.UUID;

/**
 * A post, as {@code GET /posts/{id}} and the write endpoints return it.
 *
 * @param author     {@code null} if the author deleted their account
 * @param tldr       the most recent TL;DR, which may be of the previous content while
 *                   {@code tldrStatus} is PENDING; {@code null} for a new post
 * @param tldrStatus whether the TL;DR matches the current content
 */
public record PostResponse(
        UUID id,
        String title,
        String content,
        AuthorResponse author,
        String tldr,
        TldrStatus tldrStatus,
        int likeCount,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param author the author, or {@code null} when their account is deleted */
    public static PostResponse of(PostView post, UserView author) {
        return new PostResponse(
                post.id(),
                post.title(),
                post.content(),
                author == null ? null : AuthorResponse.of(author),
                post.tldr(),
                post.tldrStatus(),
                post.likeCount(),
                post.createdAt(),
                post.updatedAt());
    }
}
