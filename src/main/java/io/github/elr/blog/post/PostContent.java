package io.github.elr.blog.post;

import java.util.UUID;

/**
 * What the {@code ai} module needs to write about a post: its text, and the hash that
 * identifies this version of the content.
 */
public record PostContent(UUID postId, String title, String content, String contentHash) {

    static PostContent of(Post post) {
        return new PostContent(post.getId(), post.getTitle(), post.getContent(), post.getContentHash());
    }
}
