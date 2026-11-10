package io.github.elr.blog.user;

import io.github.elr.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@code user} module on its own. Tests named {@code database...} write SQL directly,
 * to prove that the schema's constraints hold even for code that bypasses the entity.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class UserModuleTests {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsUserWithLowerCaseEmail() {
        var account = userService.create("Alice@Example.com", "alice", "hash");

        assertThat(account.email()).isEqualTo("alice@example.com");
        assertThat(account.username()).isEqualTo("alice");
        var user = users.findById(account.id()).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.isBot()).isFalse();
    }

    @Test
    void botUserExistsAfterMigration() {
        assertThat(userService.findById(UserService.BOT_USER_ID))
                .hasValueSatisfying(bot -> assertThat(bot.bot()).isTrue());
    }

    @Test
    void rejectsUsernameThatDiffersOnlyInCase() {
        userService.create("bob@example.com", "bob", "hash");

        assertThatThrownBy(() -> userService.create("other-bob@example.com", "BOB", "hash"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void rejectsEmailThatDiffersOnlyInCase() {
        userService.create("erin@example.com", "erin", "hash");

        assertThatThrownBy(() -> userService.create("Erin@Example.com", "other-erin", "hash"))
                .isInstanceOf(EmailTakenException.class);
    }

    @Test
    void rejectsTheBotsUsername() {
        assertThatThrownBy(() -> userService.create("not-the-bot@example.com", "Blog-AI", "hash"))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void usernameOfADeletedAccountStaysTakenButItsEmailIsFree() {
        var deleted = userService.create("frank@example.com", "frank", "hash");
        jdbc.update("UPDATE users SET deleted_at = now(), email = NULL, password_hash = NULL WHERE id = ?",
                deleted.id());

        assertThatThrownBy(() -> userService.create("new-frank@example.com", "frank", "hash"))
                .isInstanceOf(UsernameTakenException.class);
        assertThat(userService.create("frank@example.com", "frank-2", "hash").email())
                .isEqualTo("frank@example.com");
    }

    // Both threads wait at the barrier and are released together, so both usually pass the
    // "is it taken?" check before either inserts. Then the unique index must refuse one of them.
    @Test
    void onlyOneOfTwoSimultaneousRegistrationsGetsTheUsername() throws Exception {
        var bothReady = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                bothReady.await();
                return userService.create("racer-1@example.com", "racer", "hash");
            });
            var second = executor.submit(() -> {
                bothReady.await();
                return userService.create("racer-2@example.com", "RACER", "hash");
            });

            assertThat(new String[]{outcomeOf(first), outcomeOf(second)})
                    .containsExactlyInAnyOrder("created", "UsernameTakenException");
        }
    }

    @Test
    void deletedUserIsNotFound() {
        var view = userService.create("carol@example.com", "carol", "hash");
        var user = users.findById(view.id()).orElseThrow();

        user.delete(Instant.now());
        users.save(user);

        assertThat(userService.findById(view.id())).isEmpty();
    }

    @Test
    void databaseRejectsRegularUserWithoutPassword() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (id, email, username, role, created_at) VALUES (?, ?, ?, 'USER', now())",
                UUID.randomUUID(), "dave@example.com", "dave"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUsernameThatDiffersOnlyInCase() {
        userService.create("grace@example.com", "grace", "hash");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (id, email, username, password_hash, role, created_at) "
                        + "VALUES (?, ?, ?, 'hash', 'USER', now())",
                UUID.randomUUID(), "other-grace@example.com", "GRACE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDeletingTheBot() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE users SET deleted_at = now() WHERE id = ?", UserService.BOT_USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // "created", or the simple name of the exception the registration failed with.
    private static String outcomeOf(Future<AccountView> registration) throws InterruptedException {
        try {
            registration.get();
            return "created";
        } catch (ExecutionException exception) {
            return exception.getCause().getClass().getSimpleName();
        }
    }
}
