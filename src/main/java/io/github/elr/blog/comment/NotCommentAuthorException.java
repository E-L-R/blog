package io.github.elr.blog.comment;

import io.github.elr.blog.common.error.ForbiddenException;

import java.util.UUID;

/**
 * Someone other than the author tried to edit or delete a comment. Nobody can change the bot's
 * comments this way, because the bot can never be the caller. The API answers 403 NOT_AUTHOR.
 */
public class NotCommentAuthorException extends ForbiddenException {

    public NotCommentAuthorException(UUID commentId) {
        super("NOT_AUTHOR", "Only the author can change comment " + commentId + ".");
    }
}
