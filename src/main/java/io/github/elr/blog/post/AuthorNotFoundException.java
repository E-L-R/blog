package io.github.elr.blog.post;

import io.github.elr.blog.common.error.UnauthorizedException;

import java.util.UUID;

/**
 * A post was written for an author who does not exist or whose account is deleted.
 * <p>
 * Through the API, the author is the caller, whose token was checked moments before.
 * So this only happens when the account is deleted during the request, and the API
 * answers like for any token of a deleted account: 401 UNAUTHENTICATED.
 */
public class AuthorNotFoundException extends UnauthorizedException {

    public AuthorNotFoundException(UUID authorId) {
        super("UNAUTHENTICATED", "Author " + authorId + " does not exist or their account is deleted.");
    }
}
