package io.github.elr.blog.comment;

import io.github.elr.blog.common.error.ConflictException;

import java.util.UUID;

/**
 * Editing, replying to, liking or asking the AI about a deleted comment. The comment exists,
 * but its state forbids the change, so the API answers 409 TARGET_DELETED.
 */
public class CommentDeletedException extends ConflictException {

    public CommentDeletedException(UUID commentId) {
        super("TARGET_DELETED", "Comment " + commentId + " is deleted.");
    }
}
