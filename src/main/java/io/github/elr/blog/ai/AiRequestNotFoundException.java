package io.github.elr.blog.ai;

import io.github.elr.blog.common.error.NotFoundException;

import java.util.UUID;

/**
 * No AI request with this ID is visible to the caller: it does not exist, it is a TL;DR job,
 * or someone else asked for it. The API answers 404 NOT_FOUND in every case.
 */
public class AiRequestNotFoundException extends NotFoundException {

    public AiRequestNotFoundException(UUID requestId) {
        super("AI request " + requestId + " does not exist.");
    }
}
