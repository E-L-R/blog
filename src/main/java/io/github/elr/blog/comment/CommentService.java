package io.github.elr.blog.comment;

import io.github.elr.blog.post.PostNotFoundException;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.elr.blog.common.persistence.ConstraintViolations.isViolationOf;

/**
 * The public API of the {@code comment} module. It returns {@link CommentView} read models,
 * never the {@code Comment} entity.
 */
@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository comments;
    private final PostService postService;
    private final Clock clock;

    CommentService(CommentRepository comments, PostService postService, Clock clock) {
        this.comments = comments;
        this.postService = postService;
        this.clock = clock;
    }

    /**
     * Writes a comment on a post, or a reply to one of its comments.
     *
     * @param parentId the comment to reply to, or {@code null} for a top-level comment
     * @throws PostNotFoundException    if the post does not exist
     * @throws InvalidParentException   if the parent is not a comment on this post
     * @throws CommentDeletedException  if the parent is deleted
     * @throws IllegalArgumentException if the content is blank
     */
    @Transactional
    public CommentView create(UUID postId, UUID authorId, String content, UUID parentId) {
        if (postService.findById(postId).isEmpty()) {
            throw new PostNotFoundException(postId);
        }
        Comment comment;
        if (parentId == null) {
            comment = Comment.topLevel(postId, authorId, content, clock.instant());
        } else {
            var parent = comments.findById(parentId)
                    .filter(candidate -> candidate.getPostId().equals(postId))
                    .orElseThrow(() -> new InvalidParentException(parentId));
            comment = parent.reply(authorId, content, clock.instant());
        }
        return CommentView.of(insert(comment));
    }

    /**
     * Writes the AI bot's reply: a reply to the comment, or a top-level comment on the post
     * when {@code commentId} is {@code null}. Used by the {@code ai} module.
     *
     * @throws PostNotFoundException    if the post does not exist
     * @throws CommentNotFoundException if the comment does not exist
     * @throws CommentDeletedException  if the comment is deleted
     */
    @Transactional
    public CommentView createBotReply(UUID postId, UUID commentId, String content) {
        if (commentId == null) {
            if (postService.findById(postId).isEmpty()) {
                throw new PostNotFoundException(postId);
            }
            return CommentView.of(insert(Comment.topLevel(postId, UserService.BOT_USER_ID, content, clock.instant())));
        }
        var target = comments.findById(commentId).orElseThrow(() -> new CommentNotFoundException(commentId));
        return CommentView.of(insert(target.reply(UserService.BOT_USER_ID, content, clock.instant())));
    }

    public Optional<CommentView> findById(UUID commentId) {
        return comments.findById(commentId).map(CommentView::of);
    }

    /**
     * A post's whole thread as a flat list, oldest first. Clients build the tree from each
     * comment's {@code parentId}.
     *
     * @throws PostNotFoundException if the post does not exist
     */
    public List<CommentView> findThread(UUID postId) {
        if (postService.findById(postId).isEmpty()) {
            throw new PostNotFoundException(postId);
        }
        return comments.findAllByPostIdOrderByCreatedAtAscIdAsc(postId).stream().map(CommentView::of).toList();
    }

    /**
     * @throws CommentNotFoundException   if the comment does not exist
     * @throws NotCommentAuthorException  if the user is not the author
     * @throws CommentDeletedException    if the comment is deleted
     */
    @Transactional
    public CommentView edit(UUID commentId, UUID userId, String content) {
        var comment = findOwnComment(commentId, userId);
        comment.edit(content, clock.instant());
        return CommentView.of(comment);
    }

    /**
     * Soft-deletes a comment: its content goes, its replies and likes stay. Deleting a comment
     * that is already deleted changes nothing.
     *
     * @throws CommentNotFoundException  if the comment does not exist
     * @throws NotCommentAuthorException if the user is not the author
     */
    @Transactional
    public void delete(UUID commentId, UUID userId) {
        findOwnComment(commentId, userId).softDelete(clock.instant());
    }

    private Comment findOwnComment(UUID commentId, UUID userId) {
        var comment = comments.findById(commentId).orElseThrow(() -> new CommentNotFoundException(commentId));
        if (!comment.isAuthoredBy(userId)) {
            throw new NotCommentAuthorException(commentId);
        }
        return comment;
    }

    /**
     * Inserts a new comment right away, so a refused insert can be reported here. If the post
     * was deleted after the checks above, a foreign key refuses the comment: the post's, or the
     * parent's, which the post's deletion removed with it. Either way, the post is gone.
     */
    private Comment insert(Comment comment) {
        try {
            return comments.saveAndFlush(comment);
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "comments_post_fkey") || isViolationOf(exception, "comments_parent_fkey")) {
                throw new PostNotFoundException(comment.getPostId());
            }
            throw exception;
        }
    }
}
