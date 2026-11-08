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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        var view = userService.create("Alice@Example.com", "alice", "hash");

        var user = users.findById(view.id()).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(view.bot()).isFalse();
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
                .isInstanceOf(DataIntegrityViolationException.class);
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
    void databaseRejectsDeletingTheBot() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE users SET deleted_at = now() WHERE id = ?", UserService.BOT_USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}