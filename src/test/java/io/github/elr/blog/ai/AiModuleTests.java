package io.github.elr.blog.ai;

import io.github.elr.blog.TestcontainersConfiguration;
import io.github.elr.blog.comment.CommentService;
import io.github.elr.blog.comment.CommentView;
import io.github.elr.blog.post.PostService;
import io.github.elr.blog.post.TldrStatus;
import io.github.elr.blog.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the AI flows end to end: queuing jobs, the worker, TL;DRs and replies, with the fake LLM
 * and a clock the tests move forward. Scheduling is off in the test profile, so each test runs the
 * worker itself, one {@code pollOnce()} at a time.
 * <p>
 * The table is shared by every test in this context, so each test starts by cancelling the
 * unfinished jobs that earlier tests left, and only sees its own ({@link AiTestConfiguration.Reset}).
 */
@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES, extraIncludes = "auth")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AiTestConfiguration.class})
@ActiveProfiles("test")
class AiModuleTests {

    @Autowired
    AiWorker worker;

    @Autowired
    AiJobClaimer claimer;

    @Autowired
    AiJobFinisher finisher;

    @Autowired
    AiRequestService aiRequestService;

    @Autowired
    AiRequestRepository requests;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    MutableClock clock;

    @Autowired
    AiProperties properties;

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    MeterRegistry meters;

    @Autowired
    AiTestConfiguration.Reset reset;

    @BeforeEach
    void startWithAnEmptyQueue() {
        reset.run();
    }

    // TL;DR

    @Test
    void creatingAPostQueuesATldrThatTheWorkerWrites() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "A long post about modular monoliths.").id();
        assertThat(tldrRequests(postId)).singleElement().extracting(AiRequest::getStatus).isEqualTo(AiRequestStatus.PENDING);
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.PENDING);
        fake.answerWith(prompt -> new LlmReply("A short summary.", "fake-model"));

        worker.pollOnce();

        var post = postService.findById(postId).orElseThrow();
        assertThat(post.tldr()).isEqualTo("A short summary.");
        assertThat(post.tldrStatus()).isEqualTo(TldrStatus.GENERATED);
        var request = tldrRequests(postId).getFirst();
        assertThat(request.getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
        assertThat(request.getModel()).isEqualTo("fake-model");
        assertThat(fake.prompts()).singleElement().satisfies(prompt -> {
            assertThat(prompt.system()).isEqualTo(PromptBuilder.TLDR_SYSTEM);
            assertThat(prompt.user()).contains("A long post about modular monoliths.");
        });
    }

    @Test
    void aRolledBackPostLeavesNoJob() {
        var authorId = newUser();
        var before = requests.count();

        transactions.executeWithoutResult(status -> {
            postService.create(authorId, "Title", "Content");
            status.setRollbackOnly();
        });

        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    void aTitleOnlyEditQueuesNoJob() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();

        postService.edit(postId, authorId, "New title", "Content");

        assertThat(tldrRequests(postId)).hasSize(1);
    }

    @Test
    void onlyTheNewestOfSeveralJobsCallsTheModel() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "First").id();
        postService.edit(postId, authorId, "Title", "Second");
        postService.edit(postId, authorId, "Title", "Third");

        worker.pollOnce();

        assertThat(fake.prompts()).singleElement().satisfies(prompt -> assertThat(prompt.user()).contains("Third"));
        assertThat(tldrRequests(postId)).extracting(AiRequest::getStatus)
                .containsExactly(AiRequestStatus.CANCELLED, AiRequestStatus.CANCELLED, AiRequestStatus.COMPLETED);
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.GENERATED);
    }

    // The model is still working when the author edits the post. Its result is for the old
    // content, so it must not be applied; the job for the new content produces the TL;DR.
    @Test
    void anEditDuringGenerationDiscardsTheStaleResult() throws Exception {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Old content").id();
        var modelStarted = new CountDownLatch(1);
        var editDone = new CountDownLatch(1);
        fake.answerWith(prompt -> {
            modelStarted.countDown();
            await(editDone);
            return new LlmReply("Summary of the old content", "fake-model");
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var poll = executor.submit(() -> worker.pollOnce());
            assertThat(modelStarted.await(10, TimeUnit.SECONDS)).isTrue();
            postService.edit(postId, authorId, "Title", "New content");
            editDone.countDown();
            poll.get(10, TimeUnit.SECONDS);
        }

        var afterStaleResult = postService.findById(postId).orElseThrow();
        assertThat(afterStaleResult.tldr()).isNull();
        assertThat(afterStaleResult.tldrStatus()).isEqualTo(TldrStatus.PENDING);

        fake.answerWith(prompt -> new LlmReply("Summary of the new content", "fake-model"));
        worker.pollOnce();

        var post = postService.findById(postId).orElseThrow();
        assertThat(post.tldr()).isEqualTo("Summary of the new content");
        assertThat(post.tldrStatus()).isEqualTo(TldrStatus.GENERATED);
    }

    @Test
    void aRetryableErrorBacksOffUntilTheLastAttemptFails() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();
        fake.answerWith(prompt -> {
            throw new LlmException("Overloaded", true);
        });
        var maxAttempts = properties.worker().maxAttempts();

        for (var attempt = 1; attempt < maxAttempts; attempt++) {
            worker.pollOnce();
            var request = tldrRequests(postId).getFirst();
            var backoff = properties.worker().initialBackoff().multipliedBy(1L << (attempt - 1));
            assertThat(request.getStatus()).isEqualTo(AiRequestStatus.PENDING);
            assertThat(request.getAttempts()).isEqualTo(attempt);
            assertThat(request.getNextAttemptAt()).isEqualTo(clock.instant().plus(backoff));

            // Not due before its backoff has passed
            worker.pollOnce();
            assertThat(fake.prompts()).hasSize(attempt);
            clock.advance(backoff);
        }
        worker.pollOnce();

        assertThat(tldrRequests(postId).getFirst().getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.FAILED);

        // A later content edit tries again
        postService.edit(postId, authorId, "Title", "New content");
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.PENDING);
    }

    @Test
    void anErrorThatWontGoAwayFailsAtOnce() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        fake.answerWith(prompt -> {
            throw new LlmException("Rejected", false);
        });

        worker.pollOnce();

        assertThat(tldrRequests(postId).getFirst().getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.FAILED);
    }

    @Test
    void aCallThatTakesTooLongIsRetriedLater() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var release = new CountDownLatch(1);
        fake.answerWith(prompt -> {
            await(release);
            return new LlmReply("Too late", "fake-model");
        });

        try {
            worker.pollOnce();
        } finally {
            release.countDown();
        }

        var request = tldrRequests(postId).getFirst();
        assertThat(request.getStatus()).isEqualTo(AiRequestStatus.PENDING);
        assertThat(request.getError()).contains("timed out");
    }

    // Worker

    @Test
    void twoWorkersNeverClaimTheSameJob() throws Exception {
        for (var i = 0; i < 6; i++) {
            postService.create(newUser(), "Title", "Content " + i);
        }
        var bothReady = new CyclicBarrier(2);

        List<ClaimedJob> first;
        List<ClaimedJob> second;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                bothReady.await();
                return claimer.claimDueJobs();
            });
            var b = executor.submit(() -> {
                bothReady.await();
                return claimer.claimDueJobs();
            });
            first = a.get();
            second = b.get();
        }

        var all = new ArrayList<>(first);
        all.addAll(second);
        assertThat(all).hasSize(6);
        assertThat(new HashSet<>(all.stream().map(ClaimedJob::id).toList())).hasSize(6);
    }

    @Test
    void anExpiredLeaseIsClaimedAgainAndTheLateResultIsDiscarded() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var hash = postService.findContent(postId).orElseThrow().contentHash();
        var firstClaim = claimer.claimDueJobs().getFirst();

        clock.advance(properties.worker().lease().plusSeconds(1));
        var secondClaim = claimer.claimDueJobs().getFirst();
        assertThat(secondClaim.id()).isEqualTo(firstClaim.id());
        assertThat(secondClaim.attempt()).isEqualTo(2);

        finisher.finish(firstClaim, new JobOutcome.Success("Late", "fake-model", hash));
        assertThat(tldrRequests(postId).getFirst().getStatus()).isEqualTo(AiRequestStatus.IN_PROGRESS);
        assertThat(postService.findById(postId).orElseThrow().tldr()).isNull();

        finisher.finish(secondClaim, new JobOutcome.Success("On time", "fake-model", hash));
        assertThat(tldrRequests(postId).getFirst().getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
        assertThat(postService.findById(postId).orElseThrow().tldr()).isEqualTo("On time");
    }

    @Test
    void aJobWhoseLeaseExpiredWithNoAttemptsLeftFails() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        jdbc.update("UPDATE ai_requests SET status = 'IN_PROGRESS', attempts = ?, locked_until = ? WHERE post_id = ?",
                properties.worker().maxAttempts(), Timestamp.from(clock.instant().minusSeconds(1)), postId);

        worker.pollOnce();

        assertThat(fake.prompts()).isEmpty();
        assertThat(tldrRequests(postId).getFirst().getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(postService.findById(postId).orElseThrow().tldrStatus()).isEqualTo(TldrStatus.FAILED);
    }

    @Test
    void countsFinishedJobs() {
        var before = finishedCount("TLDR", "COMPLETED");
        postService.create(newUser(), "Title", "Content");

        worker.pollOnce();

        assertThat(finishedCount("TLDR", "COMPLETED")).isEqualTo(before + 1);
    }

    // Replies

    @Test
    void repliesToACommentWithTheThreadInThePrompt() {
        var postId = postService.create(newUser(), "A question", "What is a modular monolith?").id();
        var root = commentService.create(postId, newUser(), "Root comment", null);
        var middle = commentService.create(postId, newUser(), "Middle comment", root.id());
        var target = commentService.create(postId, newUser(), "Target comment", middle.id());
        commentService.create(postId, newUser(), "A sibling that must not be in the prompt", null);
        var request = aiRequestService.requestCommentReply(newUser(), target.id());
        fake.answerWith(prompt -> new LlmReply("The bot's answer", "fake-model"));

        worker.pollOnce();

        var done = requests.findById(request.id()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
        var reply = commentService.findById(done.getResultCommentId()).orElseThrow();
        assertThat(reply.authorId()).isEqualTo(UserService.BOT_USER_ID);
        assertThat(reply.parentId()).isEqualTo(target.id());
        assertThat(reply.content()).isEqualTo("The bot's answer");
        var prompt = fake.prompts().stream().filter(p -> p.system().equals(PromptBuilder.REPLY_SYSTEM)).findFirst()
                .orElseThrow().user();
        assertThat(prompt).containsSubsequence("What is a modular monolith?", "Root comment", "Middle comment",
                "<target>", "Target comment");
        assertThat(prompt).doesNotContain("sibling");
    }

    @Test
    void repliesToThePostAsATopLevelComment() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var request = aiRequestService.requestPostReply(newUser(), postId);

        worker.pollOnce();

        var reply = commentService.findById(requests.findById(request.id()).orElseThrow().getResultCommentId())
                .orElseThrow();
        assertThat(reply.parentId()).isNull();
        assertThat(reply.postId()).isEqualTo(postId);
    }

    @Test
    void aReplyToACommentDeletedBeforeTheWorkerRunsIsCancelled() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var authorId = newUser();
        var comment = commentService.create(postId, authorId, "Text", null);
        var request = aiRequestService.requestCommentReply(newUser(), comment.id());
        commentService.delete(comment.id(), authorId);

        worker.pollOnce();

        assertThat(requests.findById(request.id()).orElseThrow().getStatus()).isEqualTo(AiRequestStatus.CANCELLED);
        assertThat(fake.prompts()).noneMatch(prompt -> prompt.system().equals(PromptBuilder.REPLY_SYSTEM));
        assertThat(botCommentsOn(postId)).isZero();
    }

    // Two workers end up with the same job: the first one's lease expired while it was still
    // working. Both try to write their reply at the same moment; only the current holder may.
    @Test
    void twoWorkersRacingOnAReplyCreateOneBotComment() throws Exception {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var request = aiRequestService.requestPostReply(newUser(), postId);
        var firstClaim = claimedReply(request.id());
        clock.advance(properties.worker().lease().plusSeconds(1));
        var secondClaim = claimedReply(request.id());
        var bothReady = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                bothReady.await();
                finisher.finish(firstClaim, new JobOutcome.Success("First", "fake-model", null));
                return null;
            });
            var b = executor.submit(() -> {
                bothReady.await();
                finisher.finish(secondClaim, new JobOutcome.Success("Second", "fake-model", null));
                return null;
            });
            a.get();
            b.get();
        }

        assertThat(botCommentsOn(postId)).isEqualTo(1);
        assertThat(requests.findById(request.id()).orElseThrow().getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
    }

    @Test
    void aReplyRequestedBeforeTheAccountWasDeletedStillCompletes() {
        var postId = postService.create(newUser(), "Title", "Content").id();
        var requesterId = newUser();
        var request = aiRequestService.requestPostReply(requesterId, postId);
        userService.delete(requesterId);

        worker.pollOnce();

        assertThat(requests.findById(request.id()).orElseThrow().getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
    }

    @Test
    void deletingAPostDetachesItsRequestsAndTheWorkerCancelsThem() {
        var authorId = newUser();
        var postId = postService.create(authorId, "Title", "Content").id();
        var tldr = tldrRequests(postId).getFirst();
        var reply = aiRequestService.requestPostReply(newUser(), postId);

        postService.delete(postId, authorId);
        worker.pollOnce();

        for (var id : List.of(tldr.getId(), reply.id())) {
            var request = requests.findById(id).orElseThrow();
            assertThat(request.getPostId()).isNull();
            assertThat(request.getStatus()).isEqualTo(AiRequestStatus.CANCELLED);
        }
        assertThat(fake.prompts()).isEmpty();
    }

    private ClaimedJob claimedReply(UUID requestId) {
        return claimer.claimDueJobs().stream().filter(job -> job.id().equals(requestId)).findFirst().orElseThrow();
    }

    private List<AiRequest> tldrRequests(UUID postId) {
        return requests.findAll().stream()
                .filter(request -> request.getType() == AiRequestType.TLDR && postId.equals(request.getPostId()))
                .sorted(Comparator.comparing(AiRequest::getCreatedAt).thenComparing(AiRequest::getId))
                .toList();
    }

    private int botCommentsOn(UUID postId) {
        return (int) commentService.findThread(postId).stream()
                .map(CommentView::authorId)
                .filter(UserService.BOT_USER_ID::equals)
                .count();
    }

    private double finishedCount(String type, String status) {
        var counter = meters.find("blog.ai.requests.finished").tag("type", type).tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID newUser() {
        var name = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return userService.create(name + "@example.com", name, "hash").id();
    }
}
