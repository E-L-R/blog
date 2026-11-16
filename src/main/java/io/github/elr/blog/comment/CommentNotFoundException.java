package io.github.elr.blog.comment;

import io.github.elr.blog.common.error.NotFoundException;

import java.util.UUID;

/** No comment has the given ID. The API answers 404 NOT_FOUND. */
public class CommentNotFoundException extends NotFoundException {

    public CommentNotFoundException(UUID commentId) {
        super("Comment " + commentId + " does not exist.");
    }
}
