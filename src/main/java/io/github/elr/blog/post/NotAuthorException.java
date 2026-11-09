package io.github.elr.blog.post;

import java.util.UUID;

public class NotAuthorException extends RuntimeException {

    public NotAuthorException(UUID postId, UUID userId) {
        super("User " + userId + " is not the author of post " + postId);
    }
}
