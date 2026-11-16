package io.github.elr.blog.ai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A stand-in for the real model, used by the tests and by the {@code dev} profile, so neither
 * needs an API key or network access ({@code blog.ai.fake=true}).
 * <p>
 * By default it answers with a fixed text. Tests can change what it does with
 * {@link #answerWith}: return a given text, throw an {@link LlmException}, or wait for a
 * signal. Every prompt it receives is recorded, so tests can check what was sent.
 */
public class FakeLlmClient implements LlmClient {

    static final String MODEL = "fake-model";

    private static final Function<LlmPrompt, LlmReply> DEFAULT =
            prompt -> new LlmReply("This is an answer from the fake LLM.", MODEL);

    private final List<LlmPrompt> prompts = new CopyOnWriteArrayList<>();
    private volatile Function<LlmPrompt, LlmReply> behaviour = DEFAULT;

    @Override
    public LlmReply generate(LlmPrompt prompt) {
        prompts.add(prompt);
        return behaviour.apply(prompt);
    }

    /** Decides what the next calls do. The function may return an answer, throw, or wait. */
    void answerWith(Function<LlmPrompt, LlmReply> behaviour) {
        this.behaviour = behaviour;
    }

    /** Every prompt received since the last {@link #reset}, oldest first. */
    List<LlmPrompt> prompts() {
        return List.copyOf(prompts);
    }

    /** Forgets the recorded prompts and goes back to the default answer. */
    void reset() {
        prompts.clear();
        behaviour = DEFAULT;
    }
}
