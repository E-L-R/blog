/**
 * Posts: writing, editing, deleting and listing them.
 * <p>
 * The module may use {@code user}, to check that an author exists. Dependencies only go one
 * way, so {@code user} can never call back into this module.
 */
@ApplicationModule(displayName = "Posts", allowedDependencies = "user")
package io.github.elr.blog.post;

import org.springframework.modulith.ApplicationModule;
