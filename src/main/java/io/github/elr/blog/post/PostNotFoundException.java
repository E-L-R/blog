package io.github.elr.blog.post;

import io.github.elr.blog.common.error.NotFoundException;

import java.util.UUID;

/** No post has the given ID. The API answers 404 NOT_FOUND. */
public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(UUID postId) {
        super("Post " + postId + " does not exist.");
    }
}
