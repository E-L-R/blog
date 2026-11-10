package io.github.elr.blog.user;

import io.github.elr.blog.common.error.ConflictException;

/**
 * Registration with a username that is taken, ignoring case. The usernames of the bot and
 * of deleted accounts count as taken. The API answers 409 USERNAME_TAKEN.
 */
public class UsernameTakenException extends ConflictException {

    public UsernameTakenException() {
        super("USERNAME_TAKEN", "This username is already taken.");
    }
}
