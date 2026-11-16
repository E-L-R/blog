package io.github.elr.blog.auth.web;

import io.github.elr.blog.auth.AuthService;
import io.github.elr.blog.auth.web.dto.DeleteAccountRequest;
import io.github.elr.blog.auth.web.dto.MeResponse;
import io.github.elr.blog.common.web.CurrentUser;
import io.github.elr.blog.user.UserNotFoundException;
import io.github.elr.blog.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The caller's own account. Every endpoint here needs a token. */
@Tag(name = "Account", description = "The caller's own account")
@RestController
@RequestMapping("/me")
class MeController {

    private final UserService userService;
    private final AuthService authService;

    MeController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @Operation(summary = "Get the caller's account")
    @GetMapping
    MeResponse me(@CurrentUser UUID userId) {
        return userService.findAccount(userId).map(MeResponse::of).orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * {@code POST /me/delete}. A POST with a body rather than a DELETE, because the password
     * must be sent, and a DELETE request with a body is not reliably supported by HTTP clients.
     */
    @Operation(summary = "Delete the caller's account, after checking the password")
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(userId, request.password());
    }
}
