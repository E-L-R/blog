package io.github.elr.blog.post;

import java.util.UUID;

/** A post was written for an author who does not exist or whose account is deleted. */
public class AuthorNotFoundException extends RuntimeException {

    public AuthorNotFoundException(UUID authorId) {
        super("Author " + authorId + " does not exist or their account is deleted");
    }
}
