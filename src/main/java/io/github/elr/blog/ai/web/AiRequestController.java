package io.github.elr.blog.ai.web;

import io.github.elr.blog.ai.AiRequestNotFoundException;
import io.github.elr.blog.ai.AiRequestService;
import io.github.elr.blog.ai.AiRequestView;
import io.github.elr.blog.ai.web.dto.AiRequestResponse;
import io.github.elr.blog.common.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Asking the AI to reply, and following the request. Every endpoint needs a token.
 * <p>
 * The reply is written later by the worker, so asking answers {@code 202 Accepted}: the request
 * is taken, not done. The {@code Location} header points to the request, which the client polls.
 */
@RestController
class AiRequestController {

    private final AiRequestService aiRequestService;

    AiRequestController(AiRequestService aiRequestService) {
        this.aiRequestService = aiRequestService;
    }

    @PostMapping("/posts/{id}/ai-reply")
    ResponseEntity<AiRequestResponse> replyToPost(@CurrentUser UUID userId, @PathVariable UUID id) {
        return accepted(aiRequestService.requestPostReply(userId, id));
    }

    @PostMapping("/comments/{id}/ai-reply")
    ResponseEntity<AiRequestResponse> replyToComment(@CurrentUser UUID userId, @PathVariable UUID id) {
        return accepted(aiRequestService.requestCommentReply(userId, id));
    }

    /** Answers 404 to everyone but the user who asked, so request IDs reveal nothing to others. */
    @GetMapping("/ai-requests/{id}")
    AiRequestResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return aiRequestService.findForRequester(id, userId)
                .map(AiRequestResponse::of)
                .orElseThrow(() -> new AiRequestNotFoundException(id));
    }

    private static ResponseEntity<AiRequestResponse> accepted(AiRequestView request) {
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/ai-requests/{id}").buildAndExpand(request.id()).toUri();
        return ResponseEntity.accepted().location(location).body(AiRequestResponse.of(request));
    }
}
