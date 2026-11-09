package io.github.elr.blog.post;

import io.github.elr.blog.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository posts;
    private final UserService userService;
    private final Clock clock;

    PostService(PostRepository posts, UserService userService, Clock clock) {
        this.posts = posts;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public PostView create(UUID authorId, String title, String content) {
        if (userService.findById(authorId).isEmpty()) {
            throw new AuthorNotFoundException(authorId);
        }
        var post = posts.save(new Post(authorId, title, content, clock.instant()));
        return PostView.of(post);
    }

    public Optional<PostView> findById(UUID postId) {
        return posts.findById(postId).map(PostView::of);
    }

    public Page<PostView> list(Pageable pageable) {
        return posts.findAllByOrderByCreatedAtDescIdDesc(pageable).map(PostView::of);
    }

    public Page<PostView> listByAuthor(UUID authorId, Pageable pageable) {
        if (userService.findById(authorId).isEmpty()) {
            return Page.empty(pageable);
        }
        return posts.findAllByAuthorIdOrderByCreatedAtDescIdDesc(authorId, pageable).map(PostView::of);
    }

    @Transactional
    public PostView edit(UUID postId, UUID userId, String title, String content) {
        var post = findOwnPost(postId, userId);
        post.edit(title, content, clock.instant());
        return PostView.of(post);
    }

    @Transactional
    public void delete(UUID postId, UUID userId) {
        posts.delete(findOwnPost(postId, userId));
    }

    private Post findOwnPost(UUID postId, UUID userId) {
        var post = posts.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.isAuthoredBy(userId)) {
            throw new NotAuthorException(postId, userId);
        }
        return post;
    }
}
