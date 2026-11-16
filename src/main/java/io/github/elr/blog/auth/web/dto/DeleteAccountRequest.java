package io.github.elr.blog.auth.web.dto;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /me/delete}: the account's password, to confirm the owner is asking. */
public record DeleteAccountRequest(@NotNull String password) {
}
