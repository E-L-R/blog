package io.github.elr.blog.auth;

import io.github.elr.blog.common.error.ForbiddenException;

/**
 * The password given to confirm deleting an account is wrong. The caller is logged in, but
 * not allowed to do this without the right password, so the API answers 403 INVALID_PASSWORD.
 */
public class InvalidPasswordException extends ForbiddenException {

    public InvalidPasswordException() {
        super("INVALID_PASSWORD", "The password is wrong.");
    }
}
