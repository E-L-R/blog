package io.github.elr.blog.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * The OpenAPI description that springdoc generates from the controllers, served at
 * {@code /v3/api-docs} and shown by Swagger UI at {@code /swagger-ui.html}. Each controller
 * names its group with {@code @Tag} and describes its endpoints with {@code @Operation}; the
 * Javadoc stays for developers, and is not shown to API clients.
 * <p>
 * Which endpoints need a token follows from the code: an endpoint that takes a
 * {@link CurrentUser} parameter needs one, so its operation gets the bearer security
 * requirement, and Swagger UI sends the token there.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER = "bearerAuth";

    static {
        // The current user's ID comes from the token, not from the request: hide the parameter,
        // which would otherwise be described as a query parameter.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI blogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Blog API")
                        .description("""
                                A blogging platform with AI features: posts, threaded comments, likes, \
                                an AI TL;DR for every post, and replies by an AI bot. Log in with \
                                POST /auth/login, then send the access token as "Authorization: Bearer <token>". \
                                Errors are Problem Details (RFC 9457) with a stable "code".""")
                        .version("unreleased")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    /** Marks the operations whose controller method takes a {@link CurrentUser} parameter as needing a token. */
    @Bean
    OperationCustomizer bearerForCurrentUser() {
        return (operation, handlerMethod) -> {
            var needsToken = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentUser.class));
            if (needsToken) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
            }
            return operation;
        };
    }
}
