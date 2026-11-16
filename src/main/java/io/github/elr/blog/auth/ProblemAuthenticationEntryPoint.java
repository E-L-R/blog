package io.github.elr.blog.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * Answers 401 when a request needs a token and has none, or has one that doesn't work.
 * <p>
 * This runs in the security filter chain, before any controller, so the
 * {@code @RestControllerAdvice} in {@code common} never sees these failures. This class
 * writes the same Problem Details format itself, with the code UNAUTHENTICATED. The standard
 * {@link BearerTokenAuthenticationEntryPoint} first sets the status and the
 * {@code WWW-Authenticate: Bearer} header that tells clients which kind of token to send.
 */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final JsonMapper jsonMapper;

    ProblemAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        bearer.commence(request, response, exception);
        var body = new LinkedHashMap<String, Object>();
        body.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", "A valid access token is required.");
        body.put("instance", request.getRequestURI());
        body.put("code", "UNAUTHENTICATED");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
