package io.github.elr.blog.ai;

/**
 * A prompt: the instructions ({@code system}) and the content to work on ({@code user}).
 * Keeping them apart lets the model tell our instructions from user-written text.
 */
record LlmPrompt(String system, String user) {
}
