package io.github.elr.blog.user;

import io.github.elr.blog.common.error.NotFoundException;

import java.util.UUID;

/** No visible user has the given ID: it does not exist, or the account is deleted. The API answers 404 NOT_FOUND. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID userId) {
        super("User " + userId + " does not exist.");
    }
}
