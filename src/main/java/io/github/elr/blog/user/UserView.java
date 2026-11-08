package io.github.elr.blog.user;

import java.time.Instant;
import java.util.UUID;

public record UserView(UUID id, String username, boolean bot, Instant createdAt) {

    static UserView of(User user) {
        return new UserView(user.getId(), user.getUsername(), user.isBot(), user.getCreatedAt());
    }
}