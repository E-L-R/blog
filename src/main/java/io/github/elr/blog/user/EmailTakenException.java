package io.github.elr.blog.user;

import io.github.elr.blog.common.error.ConflictException;

/** Registration with an email that another account already uses. The API answers 409 EMAIL_TAKEN. */
public class EmailTakenException extends ConflictException {

    public EmailTakenException() {
        super("EMAIL_TAKEN", "This email address is already registered.");
    }
}
