package io.github.elr.blog.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The {@code blog.cors.*} settings.
 *
 * @param allowedOrigins the browser origins (such as {@code https://blog.example.com}) that may
 *                       call the API. Empty by default, so no other website can call it from a browser.
 */
@ConfigurationProperties("blog.cors")
record CorsProperties(List<String> allowedOrigins) {

    CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
