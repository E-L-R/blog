package io.github.elr.blog.comment;

import io.github.elr.blog.common.error.InvalidRequestException;

import java.util.UUID;

/**
 * A reply names a parent comment that does not exist on the post it is written on.
 * The API answers 400 VALIDATION_FAILED for the field {@code parentId}.
 */
public class InvalidParentException extends InvalidRequestException {

    public InvalidParentException(UUID parentId) {
        super("parentId", "Comment " + parentId + " is not on this post.");
    }
}
