package io.github.elr.blog.post;

import java.util.UUID;

/**
 * Published when a post is created, inside the creating transaction. The {@code ai} module
 * listens to queue the post's TL;DR. Events carry only IDs: a listener reads the current state
 * itself, so an event can never hold stale data.
 */
public record PostCreatedEvent(UUID postId) {
}
