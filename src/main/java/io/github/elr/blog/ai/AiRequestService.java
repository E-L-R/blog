package io.github.elr.blog.ai;

import io.github.elr.blog.comment.CommentDeletedException;
import io.github.elr.blog.comment.CommentNotFoundException;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static io.github.elr.blog.common.persistence.ConstraintViolations.isViolationOf;

/**
 * Users' AI requests: asking the bot to reply, and following the request. AI use is not
 * limited: a user may ask as often as they like (see docs/DECISIONS.md, D9).
 */
@Service
@Transactional(readOnly = true)
public class AiRequestService {

    private final AiRequestRepository requests;
    private final PostService postService;
    private final CommentService commentService;
    private final Clock clock;

    AiRequestService(AiRequestRepository requests, PostService postService, CommentService commentService,
                     Clock clock) {
        this.requests = requests;
        this.postService = postService;
        this.commentService = commentService;
        this.clock = clock;
    }

    /**
     * Queues a bot reply to a post, as a top-level comment.
     *
     * @throws PostNotFoundException if the post does not exist
     */
    @Transactional
    public AiRequestView requestPostReply(UUID userId, UUID postId) {
        if (!postService.exists(postId)) {
            throw new PostNotFoundException(postId);
        }
        return insert(AiRequest.reply(userId, postId, null, clock.instant()));
    }

    /**
     * Queues a bot reply to a comment.
     *
     * @throws CommentNotFoundException if the comment does not exist
     * @throws CommentDeletedException  if the comment is deleted
     */
    @Transactional
    public AiRequestView requestCommentReply(UUID userId, UUID commentId) {
        var comment = commentService.findById(commentId).orElseThrow(() -> new CommentNotFoundException(commentId));
        if (comment.deleted()) {
            throw new CommentDeletedException(commentId);
        }
        return insert(AiRequest.reply(userId, comment.postId(), commentId, clock.instant()));
    }

    /** A reply request, only for the user who asked for it. */
    public Optional<AiRequestView> findForRequester(UUID requestId, UUID userId) {
        return requests.findById(requestId)
                .filter(request -> userId.equals(request.getRequestedById()))
                .map(AiRequestView::of);
    }

    /** Inserts right away, so a target deleted after the checks above is reported here as missing. */
    private AiRequestView insert(AiRequest request) {
        try {
            return AiRequestView.of(requests.saveAndFlush(request));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "ai_requests_post_fkey")) {
                throw new PostNotFoundException(request.getPostId());
            }
            if (isViolationOf(exception, "ai_requests_comment_fkey")) {
                throw new CommentNotFoundException(request.getCommentId());
            }
            throw exception;
        }
    }
}
