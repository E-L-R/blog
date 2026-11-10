package io.github.elr.blog.user;

import io.github.elr.blog.common.error.ConflictException;

public class EmailTakenException extends ConflictException {

    public EmailTakenException() {
        super("EMAIL_TAKEN", "This email address is already registered.");
    }
}
