package io.github.elr.blog.comment.web.dto;

import io.github.elr.blog.user.UserView;

import java.util.UUID;

/** The author shown with a comment, such as the bot: {@code {"username": "blog-ai", "bot": true}}. */
public record AuthorResponse(UUID id, String username, boolean bot) {

    public static AuthorResponse of(UserView user) {
        return new AuthorResponse(user.id(), user.username(), user.bot());
    }
}
