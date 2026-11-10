package io.github.elr.blog.user;

/**
 * {@code USER} for people; {@code BOT} for the single account that posts AI replies.
 * The values are stored by name, and the {@code users_role_check} constraint lists them.
 */
enum UserRole {
    USER, BOT
}
