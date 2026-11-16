package io.github.elr.blog.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The AI metrics (see docs/OPERATIONS.md, "Health and monitoring"):
 * <ul>
 *   <li>{@code blog.ai.queue.size}: jobs waiting or running, by type and status</li>
 *   <li>{@code blog.ai.requests.finished}: jobs that reached a final status</li>
 *   <li>{@code blog.ai.llm.duration}: how long LLM calls take, by type and outcome</li>
 * </ul>
 */
@Component
class AiMetrics {

    private final MeterRegistry registry;
    private final MultiGauge queueSize;

    AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queueSize = MultiGauge.builder("blog.ai.queue.size")
                .description("AI requests waiting or running")
                .register(registry);
    }

    /** Replaces the queue sizes with fresh counts; a combination with no jobs reports 0. */
    void updateQueue(List<AiRequestRepository.QueueCount> counts) {
        var rows = new ArrayList<MultiGauge.Row<?>>();
        for (var type : AiRequestType.values()) {
            for (var status : List.of(AiRequestStatus.PENDING, AiRequestStatus.IN_PROGRESS)) {
                var count = counts.stream()
                        .filter(c -> c.getType() == type && c.getStatus() == status)
                        .mapToLong(AiRequestRepository.QueueCount::getCount)
                        .sum();
                rows.add(MultiGauge.Row.of(Tags.of("type", type.name(), "status", status.name()), count));
            }
        }
        queueSize.register(rows, true);
    }

    /**
     * Counts a job that reached a final status, once the current transaction commits: if it
     * rolls back, the job didn't really finish.
     */
    void countFinishedAfterCommit(AiRequestType type, AiRequestStatus status) {
        if (!status.isFinal()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                registry.counter("blog.ai.requests.finished", "type", type.name(), "status", status.name()).increment();
            }
        });
    }

    /** Times one LLM call, tagged with whether it succeeded. */
    <T> T timeCall(AiRequestType type, Supplier<T> call) {
        var sample = Timer.start(registry);
        var outcome = "error";
        try {
            var result = call.get();
            outcome = "success";
            return result;
        } finally {
            sample.stop(registry.timer("blog.ai.llm.duration", "type", type.name(), "outcome", outcome));
        }
    }
}
