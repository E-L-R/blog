package io.github.elr.blog.post;

import io.github.elr.blog.common.error.ForbiddenException;

import java.util.UUID;

/** Someone other than the author tried to edit or delete a post. The API answers 403 NOT_AUTHOR. */
public class NotAuthorException extends ForbiddenException {

    public NotAuthorException(UUID postId) {
        super("NOT_AUTHOR", "Only the author can change post " + postId + ".");
    }
}
