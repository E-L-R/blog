package io.github.elr.blog.user.web.dto;

import io.github.elr.blog.user.UserView;

import java.time.Instant;
import java.util.UUID;

/** A user's public profile: no email. */
public record UserResponse(UUID id, String username, boolean bot, Instant createdAt) {

    public static UserResponse of(UserView user) {
        return new UserResponse(user.id(), user.username(), user.bot(), user.createdAt());
    }
}
