package io.github.elr.blog.post.web.dto;

import io.github.elr.blog.user.UserView;

import java.util.UUID;

/**
 * The author shown with a post. A post whose author deleted their account has no author
 * object at all ({@code null}), and clients show "[deleted]".
 */
public record AuthorResponse(UUID id, String username, boolean bot) {

    public static AuthorResponse of(UserView user) {
        return new AuthorResponse(user.id(), user.username(), user.bot());
    }
}
