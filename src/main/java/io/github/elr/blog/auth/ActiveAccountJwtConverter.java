package io.github.elr.blog.auth;

import io.github.elr.blog.user.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * Turns a token whose signature and claims are valid into the request's {@code Authentication},
 * after one more check: its account must still exist and not be deleted.
 * <p>
 * Without this check, the token of a deleted account would keep working until it expired.
 * It costs one primary-key lookup per request with a token (see docs/DECISIONS.md, D10).
 * The authentication's name is the user ID, which {@code @CurrentUser} reads.
 */
class ActiveAccountJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    ActiveAccountJwtConverter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var userId = parseSubject(jwt);
        if (userService.findById(userId).isEmpty()) {
            // An AuthenticationException here makes Spring Security answer 401, like a bad signature.
            throw new InvalidBearerTokenException("The token's account does not exist or is deleted");
        }
        return new JwtAuthenticationToken(jwt, List.of(), userId.toString());
    }

    private static UUID parseSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidBearerTokenException("The token's subject is not a user ID", exception);
        }
    }
}
