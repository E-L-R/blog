package io.github.elr.blog.ai;

import io.github.elr.blog.comment.CommentView;
import io.github.elr.blog.post.PostContent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The prompts and their size budget, without Spring. */
class PromptBuilderTests {

    private static final PostContent POST = new PostContent(UUID.randomUUID(), "The title", "The post's content", "hash");

    @Test
    void aTldrPromptHoldsTheContentBetweenMarkers() {
        var prompt = new PromptBuilder(1_000).tldr("Some content");

        assertThat(prompt.system()).isEqualTo(PromptBuilder.TLDR_SYSTEM);
        assertThat(prompt.user()).isEqualTo("<post>\nSome content\n</post>");
    }

    @Test
    void aReplyPromptHasThePostTheAncestorsRootFirstAndTheTarget() {
        var prompt = new PromptBuilder(1_000).reply(POST, List.of(comment("Root"), deleted(), comment("Parent")),
                comment("Target"));

        assertThat(prompt.system()).isEqualTo(PromptBuilder.REPLY_SYSTEM);
        assertThat(prompt.user()).containsSubsequence("<title>The title</title>", "The post's content",
                "Root", "[deleted]", "Parent", "<target>", "Target", "</target>");
    }

    @Test
    void overBudgetTheOldestAncestorsGoFirst() {
        var oldest = comment("O".repeat(100));
        var newest = comment("N".repeat(100));
        var target = comment("Target");
        var full = new PromptBuilder(10_000).reply(POST, List.of(oldest, newest), target).user().length();

        // Room for everything but one ancestor
        var prompt = new PromptBuilder(full - 50).reply(POST, List.of(oldest, newest), target).user();

        assertThat(prompt).doesNotContain("OOOO").contains("NNNN").contains("Target").contains("The post's content");
    }

    @Test
    void thenThePostContentIsShortenedButTheTargetIsKept() {
        var longPost = new PostContent(UUID.randomUUID(), "Title", "P".repeat(5_000), "hash");

        var prompt = new PromptBuilder(500).reply(longPost, List.of(comment("Ancestor")), comment("Target")).user();

        assertThat(prompt).doesNotContain("Ancestor").contains("Target").contains("[...]");
        assertThat(prompt.length()).isLessThan(600);
    }

    private static CommentView comment(String content) {
        return new CommentView(UUID.randomUUID(), POST.postId(), null, UUID.randomUUID(), content, false, 0,
                Instant.EPOCH, Instant.EPOCH);
    }

    private static CommentView deleted() {
        return new CommentView(UUID.randomUUID(), POST.postId(), null, UUID.randomUUID(), null, true, 0,
                Instant.EPOCH, Instant.EPOCH);
    }
}
