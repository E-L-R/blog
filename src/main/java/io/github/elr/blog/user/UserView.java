package io.github.elr.blog.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as anyone may see them: no email, and only for accounts that are not deleted.
 * <p>
 * Read models are immutable records, so a caller cannot change the user through them.
 * The factory method is package-private, so only this module can build one from an entity.
 */
public record UserView(UUID id, String username, boolean bot, Instant createdAt) {

    static UserView of(User user) {
        return new UserView(user.getId(), user.getUsername(), user.isBot(), user.getCreatedAt());
    }
}
