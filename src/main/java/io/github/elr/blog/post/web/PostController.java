package io.github.elr.blog.post.web;

import io.github.elr.blog.common.web.CurrentUser;
import io.github.elr.blog.common.web.PageResponse;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.post.PostView;
import io.github.elr.blog.post.web.dto.PostRequest;
import io.github.elr.blog.post.web.dto.PostResponse;
import io.github.elr.blog.post.web.dto.PostSummaryResponse;
import io.github.elr.blog.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * The {@code /posts} endpoints. Reading is public; writing needs a token, and only the
 * author may edit or delete (the service checks that).
 * <p>
 * The controller adds each post's author, which it gets from the {@code user} module.
 */
@RestController
@RequestMapping("/posts")
class PostController {

    private final PostService postService;
    private final UserService userService;

    PostController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    /**
     * {@code GET /posts}: newest first, optionally only one author's posts. The constraints on
     * the parameters are checked before the method runs; a broken one answers 400.
     */
    @GetMapping
    PageResponse<PostSummaryResponse> list(
            @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        var pageable = PageRequest.of(page, size);
        var posts = authorId == null ? postService.list(pageable) : postService.listByAuthor(authorId, pageable);
        // One query for all the authors on the page, instead of one per post
        var authors = userService.findAllById(posts.map(PostView::authorId).toSet());
        return PageResponse.of(posts, post -> PostSummaryResponse.of(post, authors.get(post.authorId())));
    }

    @GetMapping("/{id}")
    PostResponse get(@PathVariable UUID id) {
        var post = postService.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        return withAuthor(post);
    }

    /** {@code POST /posts}: answers 201, with the new post's URL in the {@code Location} header. */
    @PostMapping
    ResponseEntity<PostResponse> create(@CurrentUser UUID userId, @Valid @RequestBody PostRequest request) {
        var post = postService.create(userId, request.title(), request.content());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(post.id()).toUri();
        return ResponseEntity.created(location).body(withAuthor(post));
    }

    @PutMapping("/{id}")
    PostResponse edit(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody PostRequest request) {
        return withAuthor(postService.edit(id, userId, request.title(), request.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        postService.delete(id, userId);
    }

    private PostResponse withAuthor(PostView post) {
        return PostResponse.of(post, userService.findById(post.authorId()).orElse(null));
    }
}
