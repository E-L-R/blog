package io.github.elr.blog.post;

import java.util.UUID;

/** No post has the given ID. */
public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(UUID postId) {
        super("Post " + postId + " does not exist");
    }
}
