package io.github.elr.blog.post;

import java.util.UUID;

/**
 * Published when a post's content changes, inside the editing transaction, so the {@code ai}
 * module can queue a new TL;DR. A title-only edit publishes nothing.
 */
public record PostContentChangedEvent(UUID postId) {
}
