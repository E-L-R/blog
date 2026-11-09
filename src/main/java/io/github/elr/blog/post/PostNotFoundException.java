package io.github.elr.blog.post;

import java.util.UUID;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(UUID postId) {
        super("Post " + postId + " does not exist");
    }
}
