package io.github.elr.blog.user;

import java.util.UUID;

/**
 * What the {@code auth} module needs to check a password: the user's ID and password hash.
 * Only active regular accounts have a password hash, so only they ever have credentials.
 * The hash must never leave the server.
 */
public record UserCredentials(UUID id, String passwordHash) {

    static UserCredentials of(User user) {
        return new UserCredentials(user.getId(), user.getPasswordHash());
    }
}
