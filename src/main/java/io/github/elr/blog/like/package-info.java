/**
 * Likes on posts and comments: one per user and target, enforced by the database, with the
 * targets' like counts kept in step.
 * <p>
 * The module may use {@code post} and {@code comment}, whose services check the targets and
 * update their counts. It needs nothing from {@code user}: the liking user's ID comes from the token.
 */
@ApplicationModule(displayName = "Likes", allowedDependencies = {"post", "comment"})
package io.github.elr.blog.like;

import org.springframework.modulith.ApplicationModule;
