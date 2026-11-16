package io.github.elr.blog.comment.web;

import io.github.elr.blog.comment.CommentNotFoundException;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.comment.CommentView;
import io.github.elr.blog.comment.web.dto.CommentRequest;
import io.github.elr.blog.comment.web.dto.CommentResponse;
import io.github.elr.blog.comment.web.dto.CommentUpdateRequest;
import io.github.elr.blog.common.web.CurrentUser;
import io.github.elr.blog.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The comment endpoints: a post's thread under {@code /posts/{id}/comments}, and single
 * comments under {@code /comments/{id}}. Reading is public; writing needs a token, and only
 * the author may edit or delete (the service checks that).
 */
@RestController
class CommentController {

    private final CommentService commentService;
    private final UserService userService;

    CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    /** The post's whole thread as a flat list, oldest first. Clients build the tree from {@code parentId}. */
    @GetMapping("/posts/{postId}/comments")
    List<CommentResponse> thread(@PathVariable UUID postId) {
        var thread = commentService.findThread(postId);
        // One query for all the authors in the thread, instead of one per comment
        var authors = userService.findAllById(thread.stream().map(CommentView::authorId).collect(Collectors.toSet()));
        return thread.stream().map(comment -> CommentResponse.of(comment, authors.get(comment.authorId()))).toList();
    }

    /** Answers 201, with the new comment's URL in the {@code Location} header. */
    @PostMapping("/posts/{postId}/comments")
    ResponseEntity<CommentResponse> create(@CurrentUser UUID userId, @PathVariable UUID postId,
                                           @Valid @RequestBody CommentRequest request) {
        var comment = commentService.create(postId, userId, request.content(), request.parentId());
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/comments/{id}").buildAndExpand(comment.id()).toUri();
        return ResponseEntity.created(location).body(withAuthor(comment));
    }

    @GetMapping("/comments/{id}")
    CommentResponse get(@PathVariable UUID id) {
        return withAuthor(commentService.findById(id).orElseThrow(() -> new CommentNotFoundException(id)));
    }

    @PutMapping("/comments/{id}")
    CommentResponse edit(@CurrentUser UUID userId, @PathVariable UUID id,
                         @Valid @RequestBody CommentUpdateRequest request) {
        return withAuthor(commentService.edit(id, userId, request.content()));
    }

    /** A soft delete. Deleting a comment that is already deleted still answers 204. */
    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        commentService.delete(id, userId);
    }

    private CommentResponse withAuthor(CommentView comment) {
        return CommentResponse.of(comment, userService.findById(comment.authorId()).orElse(null));
    }
}
