package io.github.elr.blog.auth.web.dto;

import io.github.elr.blog.user.AccountView;

import java.time.Instant;
import java.util.UUID;

public record MeResponse(UUID id, String email, String username, Instant createdAt) {

    public static MeResponse of(AccountView account) {
        return new MeResponse(account.id(), account.email(), account.username(), account.createdAt());
    }
}
