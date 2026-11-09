package io.github.elr.blog.post;

import java.util.UUID;

public class AuthorNotFoundException extends RuntimeException {

    public AuthorNotFoundException(UUID authorId) {
        super("Author " + authorId + " does not exist or their account is deleted");
    }
}
