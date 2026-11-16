package io.github.elr.blog.post.web.dto;

import io.github.elr.blog.post.PostView;
import io.github.elr.blog.post.TldrStatus;
import io.github.elr.blog.user.UserView;

import java.time.Instant;
import java.util.UUID;

/**
 * A post in a list: the same as {@link PostResponse} without the content, so a page of
 * posts stays small. The TL;DR serves as the summary; clients load the full post with
 * {@code GET /posts/{id}}.
 */
public record PostSummaryResponse(
        UUID id,
        String title,
        AuthorResponse author,
        String tldr,
        TldrStatus tldrStatus,
        int likeCount,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param author the author, or {@code null} when their account is deleted */
    public static PostSummaryResponse of(PostView post, UserView author) {
        return new PostSummaryResponse(
                post.id(),
                post.title(),
                author == null ? null : AuthorResponse.of(author),
                post.tldr(),
                post.tldrStatus(),
                post.likeCount(),
                post.createdAt(),
                post.updatedAt());
    }
}
