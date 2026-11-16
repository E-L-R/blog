/**
 * The AI features: a TL;DR for every post, and replies to posts and comments written by the
 * bot user.
 * <p>
 * Every LLM call is an {@code AiRequest} row. The table is the job queue: requests are inserted
 * in the same transaction as the change that caused them, and {@code AiWorker} processes them
 * in the background, calling the model outside any transaction (see docs/ARCHITECTURE.md,
 * "Async AI processing").
 * <p>
 * The module may use {@code post} and {@code comment}: it reads their content for prompts and
 * writes results back through their services. Neither of them knows this module exists; it
 * learns about new and edited posts from their events.
 */
@ApplicationModule(displayName = "AI", allowedDependencies = {"post", "comment"})
package io.github.elr.blog.ai;

import org.springframework.modulith.ApplicationModule;
