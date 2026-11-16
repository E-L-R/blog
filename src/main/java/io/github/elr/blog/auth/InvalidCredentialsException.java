package io.github.elr.blog.auth;

import io.github.elr.blog.common.error.UnauthorizedException;

/**
 * A login with an unknown email or a wrong password. Both get this same exception, so the
 * response does not reveal which emails are registered. The API answers 401 INVALID_CREDENTIALS.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "The email or password is wrong.");
    }
}
