package io.github.elr.blog.ai;

import io.github.elr.blog.post.PostContentChangedEvent;
import io.github.elr.blog.post.PostCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Queues a TL;DR job whenever a post is created or its content changes.
 * <p>
 * The listeners are synchronous ({@code @EventListener}), so they run inside the transaction
 * that saves the post. The post and its job are saved together or not at all, and no event can
 * be lost between a commit and a listener. They only insert a row: the slow LLM call happens
 * later, in the worker, and can never roll back a post (see docs/DECISIONS.md, D4).
 */
@Component
class TldrRequestListener {

    private final AiRequestRepository requests;
    private final Clock clock;

    TldrRequestListener(AiRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @EventListener
    void onPostCreated(PostCreatedEvent event) {
        requests.save(AiRequest.tldr(event.postId(), clock.instant()));
    }

    @EventListener
    void onContentChanged(PostContentChangedEvent event) {
        requests.save(AiRequest.tldr(event.postId(), clock.instant()));
    }
}
