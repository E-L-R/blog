package io.github.elr.blog.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OllamaLlmClient} against a stand-in for Ollama's {@code POST /api/chat}: a small HTTP
 * server from the JDK that answers as Ollama does. No Spring context, no Docker and no real model
 * are needed.
 */
class OllamaLlmClientTests {

    private static final AiProperties.Llm SETTINGS =
            new AiProperties.Llm("llama3.2", "medium", Duration.ofSeconds(2), 500);

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int status;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OllamaLlmClient client() {
        return new OllamaLlmClient("http://localhost:" + server.getAddress().getPort(), SETTINGS);
    }

    private void answer(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    @Test
    void sendsTheModelThePromptAndTheTokenLimitWithoutStreaming() {
        answer(200, """
                {"model":"llama3.2","message":{"role":"assistant","content":"A reply."},"done":true,"done_reason":"stop"}""");

        client().generate(new LlmPrompt("Be brief.", "Hello"));

        assertThat(requestBody.get())
                .contains("\"model\":\"llama3.2\"")
                .contains("\"stream\":false")
                .contains("{\"role\":\"system\",\"content\":\"Be brief.\"}")
                .contains("{\"role\":\"user\",\"content\":\"Hello\"}")
                .contains("\"num_predict\":500");
    }

    @Test
    void returnsTheAnswerAndTheModelThatWroteIt() {
        answer(200, """
                {"model":"llama3.2:latest","message":{"role":"assistant","content":"  A reply.\\n"},"done":true,"done_reason":"stop"}""");

        var reply = client().generate(new LlmPrompt("system", "user"));

        assertThat(reply.text()).isEqualTo("A reply.");
        assertThat(reply.model()).isEqualTo("llama3.2:latest");
    }

    @Test
    void leavesOutReasoningThatTheModelWroteIntoItsAnswer() {
        answer(200, """
                {"model":"qwen3","message":{"role":"assistant","content":"<think>Let me see.</think>\\nThe reply."},"done":true,"done_reason":"stop"}""");

        assertThat(client().generate(new LlmPrompt("system", "user")).text()).isEqualTo("The reply.");
    }

    @Test
    void failsForGoodWhenTheAnswerWasCutOff() {
        answer(200, """
                {"model":"llama3.2","message":{"role":"assistant","content":"A long"},"done":true,"done_reason":"length"}""");

        assertThatThrownBy(() -> client().generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isFalse())
                .hasMessageContaining("cut off");
    }

    @Test
    void failsForGoodOnAnEmptyAnswer() {
        answer(200, """
                {"model":"llama3.2","message":{"role":"assistant","content":"<think>Hm.</think>"},"done":true,"done_reason":"stop"}""");

        assertThatThrownBy(() -> client().generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isFalse())
                .hasMessageContaining("empty");
    }

    @Test
    void failsForGoodWhenTheModelWasNeverPulled() {
        answer(404, """
                {"error":"model 'llama3.2' not found"}""");

        assertThatThrownBy(() -> client().generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isFalse())
                .hasMessageContaining("404")
                .hasMessageContaining("not found");
    }

    @Test
    void retriesWhenOllamaFails() {
        answer(500, """
                {"error":"out of memory"}""");

        assertThatThrownBy(() -> client().generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void retriesWhenOllamaAnswersTooLate() {
        server.removeContext("/api/chat");
        server.createContext("/api/chat", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        // The settings allow 2 seconds.
        assertThatThrownBy(() -> client().generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isTrue())
                .hasMessageContaining("no answer within 2 seconds");
    }

    @Test
    void retriesWhenOllamaIsNotRunning() throws IOException {
        int unusedPort;
        try (var socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        var client = new OllamaLlmClient("http://localhost:" + unusedPort, SETTINGS);

        assertThatThrownBy(() -> client.generate(new LlmPrompt("system", "user")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isTrue())
                .hasMessageContaining("could not be reached at http://localhost:" + unusedPort)
                .hasMessageContaining("Is Ollama running");
    }
}
