package io.github.elr.blog.ai;

/**
 * Calls a language model. The rest of the module depends only on this interface, so the
 * provider can be swapped, and tests use {@link FakeLlmClient} instead of a real model.
 */
interface LlmClient {

    /**
     * Sends one prompt and waits for the answer. No database transaction may be open while
     * this runs: a call can take many seconds.
     *
     * @throws LlmException if the call fails; {@link LlmException#isRetryable()} says whether
     *                      trying again later may help
     */
    LlmReply generate(LlmPrompt prompt);
}
