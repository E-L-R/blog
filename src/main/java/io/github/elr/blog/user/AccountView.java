package io.github.elr.blog.user;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as its owner sees it. Unlike UserView, it includes the email,
 * so it must never be shown to other users.
 */
public record AccountView(UUID id, String email, String username, Instant createdAt) {

    static AccountView of(User user) {
        return new AccountView(user.getId(), user.getEmail(), user.getUsername(), user.getCreatedAt());
    }
}
