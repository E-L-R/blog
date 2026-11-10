package io.github.elr.blog.auth.web.dto;

import io.github.elr.blog.user.AccountView;

import java.time.Instant;
import java.util.UUID;

/**
 * The caller's own account, as the API returns it (the "Me" resource in docs/API.md).
 * It includes the email, so it is only ever sent to the account's owner.
 * <p>
 * The web layer has its own record instead of returning {@link AccountView} directly,
 * so the JSON the API promises cannot change by accident when the read model changes.
 */
public record MeResponse(UUID id, String email, String username, Instant createdAt) {

    public static MeResponse of(AccountView account) {
        return new MeResponse(account.id(), account.email(), account.username(), account.createdAt());
    }
}
