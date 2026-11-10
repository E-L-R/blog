package io.github.elr.blog.user;

import io.github.elr.blog.common.error.ConflictException;

public class UsernameTakenException extends ConflictException {

    public UsernameTakenException() {
        super("USERNAME_TAKEN", "This username is already taken.");
    }
}
