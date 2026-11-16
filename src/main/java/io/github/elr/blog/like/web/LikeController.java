package io.github.elr.blog.like.web;

import io.github.elr.blog.common.web.CurrentUser;
import io.github.elr.blog.like.LikeService;
import io.github.elr.blog.like.web.dto.LikedIdsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Liking and unliking, and looking up what the caller has liked. Every endpoint needs a token.
 * <p>
 * {@code PUT} and {@code DELETE} fit liking well: both are idempotent in HTTP, as liking is here.
 * Liking twice, or unliking something not liked, answers 204 and changes nothing.
 */
@Tag(name = "Likes", description = "Liking posts and comments, and looking up what you liked")
@RestController
class LikeController {

    /** The most IDs one lookup may ask about. */
    private static final int MAX_LOOKUP_IDS = 100;

    private final LikeService likeService;

    LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @Operation(summary = "Like a post")
    @PutMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void likePost(@CurrentUser UUID userId, @PathVariable UUID id) {
        likeService.likePost(userId, id);
    }

    @Operation(summary = "Take back a like of a post")
    @DeleteMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlikePost(@CurrentUser UUID userId, @PathVariable UUID id) {
        likeService.unlikePost(userId, id);
    }

    @Operation(summary = "Like a comment")
    @PutMapping("/comments/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void likeComment(@CurrentUser UUID userId, @PathVariable UUID id) {
        likeService.likeComment(userId, id);
    }

    @Operation(summary = "Take back a like of a comment")
    @DeleteMapping("/comments/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlikeComment(@CurrentUser UUID userId, @PathVariable UUID id) {
        likeService.unlikeComment(userId, id);
    }

    /**
     * {@code GET /me/likes/posts?ids=a,b,c}: which of the posts a client is showing the caller
     * has liked. Post responses can't say it themselves, because {@code post} must not depend on
     * {@code like} (see docs/DECISIONS.md, D13).
     */
    @Operation(summary = "Which of these posts you like")
    @GetMapping("/me/likes/posts")
    LikedIdsResponse likedPosts(@CurrentUser UUID userId,
                                @RequestParam(required = false) @NotEmpty @Size(max = MAX_LOOKUP_IDS) List<UUID> ids) {
        var liked = likeService.likedPostIds(userId, ids);
        return new LikedIdsResponse(ids.stream().distinct().filter(liked::contains).toList());
    }

    /** {@code GET /me/likes/comments?ids=a,b,c}: the same for comments. */
    @Operation(summary = "Which of these comments you like")
    @GetMapping("/me/likes/comments")
    LikedIdsResponse likedComments(@CurrentUser UUID userId,
                                   @RequestParam(required = false) @NotEmpty @Size(max = MAX_LOOKUP_IDS) List<UUID> ids) {
        var liked = likeService.likedCommentIds(userId, ids);
        return new LikedIdsResponse(ids.stream().distinct().filter(liked::contains).toList());
    }
}
