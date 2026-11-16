package io.github.elr.blog.like;

import io.github.elr.blog.comment.CommentDeletedException;
import io.github.elr.blog.comment.CommentNotFoundException;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static io.github.elr.blog.common.persistence.ConstraintViolations.isViolationOf;

/**
 * Likes and unlikes posts and comments.
 * <p>
 * Each like is a row, and the target's {@code like_count} is a cached count of those rows.
 * Both change in one transaction, and the count only changes when a row was really inserted
 * or deleted. So liking and unliking are idempotent: liking twice, or unliking something not
 * liked, changes nothing (see docs/DECISIONS.md, D7).
 */
@Service
@Transactional(readOnly = true)
public class LikeService {

    private final PostLikeRepository postLikes;
    private final CommentLikeRepository commentLikes;
    private final PostService postService;
    private final CommentService commentService;
    private final Clock clock;

    LikeService(PostLikeRepository postLikes, CommentLikeRepository commentLikes,
                PostService postService, CommentService commentService, Clock clock) {
        this.postLikes = postLikes;
        this.commentLikes = commentLikes;
        this.postService = postService;
        this.commentService = commentService;
        this.clock = clock;
    }

    /** @throws PostNotFoundException if the post does not exist */
    @Transactional
    public void likePost(UUID userId, UUID postId) {
        if (!postService.exists(postId)) {
            throw new PostNotFoundException(postId);
        }
        try {
            if (postLikes.insertIfAbsent(newId(), userId, postId, clock.instant()) == 1) {
                postService.incrementLikeCount(postId);
            }
        } catch (DataIntegrityViolationException exception) {
            // The post was deleted between the check above and the insert.
            if (isViolationOf(exception, "post_likes_post_fkey")) {
                throw new PostNotFoundException(postId);
            }
            throw exception;
        }
    }

    /** @throws PostNotFoundException if the post does not exist */
    @Transactional
    public void unlikePost(UUID userId, UUID postId) {
        if (!postService.exists(postId)) {
            throw new PostNotFoundException(postId);
        }
        if (postLikes.deleteLike(userId, postId) == 1) {
            postService.decrementLikeCount(postId);
        }
    }

    /**
     * @throws CommentNotFoundException if the comment does not exist
     * @throws CommentDeletedException  if the comment is deleted
     */
    @Transactional
    public void likeComment(UUID userId, UUID commentId) {
        commentService.requireLikeable(commentId);
        try {
            if (commentLikes.insertIfAbsent(newId(), userId, commentId, clock.instant()) == 1) {
                commentService.incrementLikeCount(commentId);
            }
        } catch (DataIntegrityViolationException exception) {
            // The comment's post was deleted between the check above and the insert.
            if (isViolationOf(exception, "comment_likes_comment_fkey")) {
                throw new CommentNotFoundException(commentId);
            }
            throw exception;
        }
    }

    /**
     * Unliking a deleted comment is allowed: its likes stay until they are taken back.
     *
     * @throws CommentNotFoundException if the comment does not exist
     */
    @Transactional
    public void unlikeComment(UUID userId, UUID commentId) {
        if (!commentService.exists(commentId)) {
            throw new CommentNotFoundException(commentId);
        }
        if (commentLikes.deleteLike(userId, commentId) == 1) {
            commentService.decrementLikeCount(commentId);
        }
    }

    /** Which of these posts the user likes. Unknown IDs are simply not in the answer. */
    public Set<UUID> likedPostIds(UUID userId, Collection<UUID> postIds) {
        return postLikes.findLikedPostIds(userId, postIds);
    }

    /** Which of these comments the user likes. */
    public Set<UUID> likedCommentIds(UUID userId, Collection<UUID> commentIds) {
        return commentLikes.findLikedCommentIds(userId, commentIds);
    }

    /**
     * A new UUIDv7 like every other ID. Likes are inserted with SQL instead of through JPA,
     * so Hibernate doesn't assign the ID; this calls the same generator it would have used.
     */
    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }
}
