package io.github.elr.blog.auth.web;

import io.github.elr.blog.auth.AuthService;
import io.github.elr.blog.auth.web.dto.MeResponse;
import io.github.elr.blog.auth.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code /auth} endpoints. The controller only translates between HTTP and the service:
 * it reads a request record and returns a response record, never an entity.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * {@code POST /auth/register}. {@code @Valid} checks the body before this method runs.
     * A broken rule raises {@code MethodArgumentNotValidException}, which
     * {@code GlobalExceptionHandler} turns into a {@code 400} listing each invalid field.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    MeResponse register(@Valid @RequestBody RegisterRequest request) {
        var account = authService.register(request.email(), request.username(), request.password());
        return MeResponse.of(account);
    }
}
