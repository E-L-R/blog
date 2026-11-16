package io.github.elr.blog.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Chooses the LLM client: the fake one when {@code blog.ai.fake=true} (tests and the
 * {@code dev} profile); otherwise Claude, or a local Ollama server when
 * {@code blog.ai.provider=ollama}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
class AiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    @ConditionalOnBooleanProperty("blog.ai.fake")
    FakeLlmClient fakeLlmClient() {
        log.info("AI: the fake LLM writes TL;DRs and replies (blog.ai.fake=true)");
        return new FakeLlmClient();
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "blog.ai.fake", havingValue = false, matchIfMissing = true)
    LlmClient llmClient(AiProperties properties) {
        // One line at startup that says which model is used, so a setting that didn't take
        // effect shows at once.
        var model = properties.llm().model();
        return switch (properties.provider()) {
            case ANTHROPIC -> {
                log.info("AI: Claude model {} writes TL;DRs and replies", model);
                yield new AnthropicLlmClient(properties.llm());
            }
            case OLLAMA -> {
                log.info("AI: Ollama model {} at {} writes TL;DRs and replies", model, properties.ollama().baseUrl());
                yield new OllamaLlmClient(properties.ollama().baseUrl(), properties.llm());
            }
        };
    }

    @Bean
    PromptBuilder promptBuilder(AiProperties properties) {
        return new PromptBuilder(properties.prompt().maxInputChars());
    }
}
