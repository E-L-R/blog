package io.github.elr.blog.comment.web.dto;

import io.github.elr.blog.comment.CommentView;
import io.github.elr.blog.user.UserView;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment as the API returns it.
 * <p>
 * For a deleted comment, {@code content} and {@code author} are both {@code null}: the author
 * of a deleted comment is not shown. If only the author's account is deleted, the comment keeps
 * its content and only {@code author} is {@code null}. Clients show "[deleted]" in both cases.
 */
public record CommentResponse(
        UUID id,
        UUID postId,
        UUID parentId,
        AuthorResponse author,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param author the author, or {@code null} when their account is deleted */
    public static CommentResponse of(CommentView comment, UserView author) {
        var shownAuthor = comment.deleted() || author == null ? null : AuthorResponse.of(author);
        return new CommentResponse(
                comment.id(),
                comment.postId(),
                comment.parentId(),
                shownAuthor,
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                comment.updatedAt());
    }
}
