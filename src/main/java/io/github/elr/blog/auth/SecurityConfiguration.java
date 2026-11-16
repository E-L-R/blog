package io.github.elr.blog.auth;

import io.github.elr.blog.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Spring Security setup for a stateless REST API.
 * <p>
 * Clients authenticate with a token in the {@code Authorization} header, never with a
 * cookie, so the server keeps no session and CSRF protection is not needed: CSRF attacks
 * rely on the browser sending a cookie automatically, and a header is never sent that way.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService, JsonMapper jsonMapper) {
        var entryPoint = new ProblemAuthenticationEntryPoint(jsonMapper);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Rules are checked in order, and the first one that matches decides.
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        // The caller's own data, even when only read
                        .requestMatchers("/me", "/me/**", "/ai-requests/**").authenticated()
                        // Everything else can be read by anyone
                        .requestMatchers(HttpMethod.GET).permitAll()
                        // and every write needs a token.
                        .anyRequest().authenticated())
                // Reads "Authorization: Bearer <token>", checks the token with the JwtDecoder bean
                // (see JwtConfiguration), then checks that its account still exists. A token that
                // fails is refused with 401, even on a public endpoint.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ActiveAccountJwtConverter(userService)))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .build();
    }

    /**
     * bcrypt hashes are salted and deliberately slow to compute, which makes guessing
     * passwords from a leaked hash expensive. bcrypt only uses the first 72 bytes of a
     * password, which is why {@code RegisterRequest} refuses longer ones.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Which other websites may call the API from a browser (CORS). Browsers block such calls
     * unless the API allows the calling site's origin. Clients that are not browsers ignore CORS.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
