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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String email;

    private String username;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private Instant deletedAt;

    private Instant createdAt;

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