package io.github.elr.blog.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * Calls Claude through the official Anthropic Java SDK.
 * <p>
 * The API key comes from the environment ({@code ANTHROPIC_API_KEY}), read by the SDK itself.
 * The SDK's own retries are turned off: the worker already retries with backoff, and stacked
 * retries would make one call take far longer than its timeout.
 * <p>
 * Requests opt into server-side refusal fallbacks: if the model declines a request for safety
 * reasons, the API retries it on a fallback model it chooses, and the answer says which model
 * wrote it.
 */
class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final AnthropicClient client;
    private final AiProperties.Llm settings;

    AnthropicLlmClient(AiProperties.Llm settings) {
        this.settings = settings;
        if (System.getenv("ANTHROPIC_API_KEY") == null && System.getenv("ANTHROPIC_AUTH_TOKEN") == null) {
            // The SDK can also find credentials elsewhere (a profile from `ant auth login`), so
            // this is a warning, not an error. Without any, every call fails with HTTP 401 and
            // every AI request ends FAILED.
            log.warn("ANTHROPIC_API_KEY is not set: AI calls will fail unless the SDK finds other credentials. "
                    + "Set it, or set blog.ai.fake=true to use the fake LLM.");
        }
        this.client = AnthropicOkHttpClient.builder()
                .fromEnv()
                .timeout(settings.timeout())
                .maxRetries(0)
                .build();
    }

    @Override
    public LlmReply generate(LlmPrompt prompt) {
        var params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxOutputTokens())
                .system(prompt.system())
                .addUserMessage(prompt.user())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(settings.effort()))
                        .build())
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .build();
        BetaMessage response;
        try {
            response = client.beta().messages().create(params);
        } catch (AnthropicServiceException exception) {
            // 408 (timeout), 429 (rate limit) and 5xx (the provider's side) may pass; any other
            // status means the request itself was refused and would be refused again.
            var status = exception.statusCode();
            var retryable = status == 408 || status == 429 || status >= 500;
            throw new LlmException("The provider answered HTTP " + status, retryable, exception);
        } catch (AnthropicIoException | AnthropicRetryableException exception) {
            throw new LlmException("The call failed: " + exception.getMessage(), true, exception);
        }
        return toReply(response);
    }

    private static LlmReply toReply(BetaMessage response) {
        var stopReason = response.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            throw new LlmException("The model declined to answer", false);
        }
        if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
            throw new LlmException("The answer was cut off at the output token limit", false);
        }
        var text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining()).strip();
        if (text.isEmpty()) {
            throw new LlmException("The model's answer was empty", false);
        }
        return new LlmReply(text, response.model().asString());
    }
}
