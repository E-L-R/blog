package io.github.elr.blog.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A user account: a regular user, or the bot that posts AI replies.
 * <p>
 * The entity has no setters. The constructor creates a valid account, and each allowed
 * change is a method that checks its own rules. The no-argument constructor exists only
 * because JPA needs one to load rows from the database.
 * <p>
 * Deleting an account keeps the row as a tombstone, so everything that refers to the
 * user stays valid (see docs/DECISIONS.md, D14). Only a regular account that is not
 * deleted has an email and a password hash; check constraints in the schema enforce it.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class User {

    // UUIDv7 starts with a timestamp, so new rows land at the end of the primary key index.
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String email;

    private String username;

    private String passwordHash;

    // Stored by name ("USER", "BOT"), so reordering the enum can never change existing rows.
    @Enumerated(EnumType.STRING)
    private UserRole role;

    private Instant deletedAt;

    private Instant createdAt;

    /**
     * Creates a regular user. The bot is never created here: it is inserted by the first
     * migration. The email is stored in lower case, so that {@code Alice@Example.com} and
     * {@code alice@example.com} count as the same address; {@link Locale#ROOT} keeps the
     * result the same whatever the server's language. The username keeps the case it was
     * typed in, and its uniqueness is checked ignoring case.
     */
    User(String email, String username, String passwordHash, Instant createdAt) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.createdAt = createdAt;
    }

    boolean isBot() {
        return role == UserRole.BOT;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Deletes the account. The email and password hash are cleared, so the account can
     * never log in again and the email is free for a new account. The username stays, so
     * nobody else can take it and pose as the old account. Deleting twice changes nothing.
     */
    void delete(Instant now) {
        if (isBot()) {
            throw new IllegalStateException("The bot user cannot be deleted");
        }
        if (isDeleted()) {
            return;
        }
        email = null;
        passwordHash = null;
        deletedAt = now;
    }
}
