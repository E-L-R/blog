package io.github.elr.blog.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Calls a model on an Ollama server, through its chat endpoint ({@code POST /api/chat}), so
 * the AI features can run on a local model without an API key ({@code blog.ai.provider=ollama}).
 * <p>
 * Only Ollama's own API is used, through Spring's {@link RestClient}, so no extra dependency
 * is needed. Errors are sorted the way {@link AnthropicLlmClient} sorts them: a server that
 * can't be reached, is overloaded or fails (HTTP 5xx) may recover, so the worker retries;
 * anything else, such as a model that was never pulled (HTTP 404), would fail again.
 */
class OllamaLlmClient implements LlmClient {

    // Some models write their reasoning into the answer between these tags. It is not part of
    // the reply a reader should see.
    private static final Pattern THINKING = Pattern.compile("(?s)<think>.*?</think>");

    private final RestClient client;
    private final AiProperties.Llm settings;

    OllamaLlmClient(String baseUrl, AiProperties.Llm settings) {
        this.settings = settings;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        // A local model can be slow, especially while it is first loaded into memory: the whole
        // call may take up to the configured timeout.
        requestFactory.setReadTimeout(settings.timeout());
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LlmReply generate(LlmPrompt prompt) {
        var request = new ChatRequest(
                settings.model(),
                List.of(new Message("system", prompt.system()), new Message("user", prompt.user())),
                false,
                Map.of("num_predict", settings.maxOutputTokens()));
        ChatResponse response;
        try {
            response = client.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException exception) {
            var status = exception.getStatusCode();
            throw new LlmException("Ollama answered HTTP " + status.value() + ": "
                    + exception.getResponseBodyAsString(), isRetryable(status), exception);
        } catch (ResourceAccessException exception) {
            // No connection, or no answer within the timeout.
            throw new LlmException("Ollama could not be reached: " + exception.getMessage(), true, exception);
        } catch (RestClientException exception) {
            throw new LlmException("Ollama's answer could not be read: " + exception.getMessage(), false, exception);
        }
        return toReply(response);
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 408 || status.value() == 429 || status.is5xxServerError();
    }

    private LlmReply toReply(ChatResponse response) {
        if (response == null || response.message() == null) {
            throw new LlmException("Ollama's answer had no message", false);
        }
        if ("length".equals(response.doneReason())) {
            throw new LlmException("The answer was cut off at the output token limit", false);
        }
        var content = response.message().content();
        var text = content == null ? "" : THINKING.matcher(content).replaceAll("").strip();
        if (text.isEmpty()) {
            throw new LlmException("The model's answer was empty", false);
        }
        return new LlmReply(text, response.model() == null ? settings.model() : response.model());
    }

    /** The body of {@code POST /api/chat}. {@code options} holds model settings such as {@code num_predict}. */
    record ChatRequest(String model, List<Message> messages, boolean stream, Map<String, Object> options) {
    }

    record Message(String role, String content) {
    }

    /** The parts of Ollama's answer that are used; the rest is ignored. */
    record ChatResponse(String model, Message message, @JsonProperty("done_reason") String doneReason) {
    }
}
