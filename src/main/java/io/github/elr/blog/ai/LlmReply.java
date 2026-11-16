package io.github.elr.blog.ai;

/**
 * The model's answer.
 *
 * @param model the model that wrote it, which can differ from the one asked for when the
 *              provider falls back to another model
 */
record LlmReply(String text, String model) {
}
