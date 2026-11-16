/**
 * Comments: threads of comments and replies on a post, with soft delete so a thread keeps
 * its shape when a comment goes. The AI bot's replies are comments too, written by the bot user.
 * <p>
 * The module may use {@code post} (a comment belongs to a post) and {@code user} (the bot
 * user, and the authors shown with comments).
 */
@ApplicationModule(displayName = "Comments", allowedDependencies = {"post", "user"})
package io.github.elr.blog.comment;

import org.springframework.modulith.ApplicationModule;
