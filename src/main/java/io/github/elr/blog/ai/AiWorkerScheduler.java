package io.github.elr.blog.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the worker on a fixed delay: the next poll starts {@code blog.ai.worker.poll-interval}
 * after the previous one finished, so polls never overlap on one instance.
 * <p>
 * Only exists when {@code blog.ai.worker.enabled} is true (the default). Tests turn it off and
 * call {@link AiWorker#pollOnce()} themselves, one poll at a time.
 */
@Component
@ConditionalOnBooleanProperty(name = "blog.ai.worker.enabled", matchIfMissing = true)
class AiWorkerScheduler {

    private final AiWorker worker;

    AiWorkerScheduler(AiWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${blog.ai.worker.poll-interval:2s}")
    void poll() {
        worker.pollOnce();
    }
}
