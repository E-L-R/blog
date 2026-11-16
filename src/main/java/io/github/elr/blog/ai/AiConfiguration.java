package io.github.elr.blog.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Chooses the LLM client: the fake one when {@code blog.ai.fake=true} (tests and the
 * {@code dev} profile), Claude otherwise.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
class AiConfiguration {

    @Bean
    @ConditionalOnBooleanProperty("blog.ai.fake")
    FakeLlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "blog.ai.fake", havingValue = false, matchIfMissing = true)
    AnthropicLlmClient anthropicLlmClient(AiProperties properties) {
        return new AnthropicLlmClient(properties.llm());
    }

    @Bean
    PromptBuilder promptBuilder(AiProperties properties) {
        return new PromptBuilder(properties.prompt().maxInputChars());
    }
}
