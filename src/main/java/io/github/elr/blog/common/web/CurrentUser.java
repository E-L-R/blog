package io.github.elr.blog.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller parameter of type {@link java.util.UUID} that receives the ID of the
 * logged-in user, for example {@code createPost(@CurrentUser UUID userId, ...)}.
 * <p>
 * The ID comes from Spring Security's {@code Authentication}, whose name is the user ID
 * (the token's subject). So controllers in every module get the current user without
 * depending on the {@code auth} module. Use it only on endpoints that require a token.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
