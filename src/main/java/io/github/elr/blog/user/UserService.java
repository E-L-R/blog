package io.github.elr.blog.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.elr.blog.common.persistence.ConstraintViolations.isViolationOf;

/**
 * The public API of the {@code user} module. Other modules create and look up users here,
 * and get read models back, never the {@code User} entity.
 * <p>
 * Methods are read-only transactions by default; a method that writes overrides that
 * with its own {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    /** The bot that posts AI replies. The first migration inserts it with this fixed ID. */
    public static final UUID BOT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository users;
    private final Clock clock;

    UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * Creates a regular user. The caller hashes the password, so this module never sees it.
     * <p>
     * A taken email or username is refused, ignoring case. The checks run before the insert
     * to give a clear error in the usual case. They cannot stop two registrations that arrive
     * at the same moment, since both pass the checks; the database's unique constraints
     * refuse the second insert, and the {@code catch} below turns that into the same errors.
     *
     * @throws EmailTakenException    if the email is already registered
     * @throws UsernameTakenException if the username is taken, ignoring case. The bot's
     *                                username and those of deleted accounts count as taken.
     */
    @Transactional
    public AccountView create(String email, String username, String passwordHash) {
        var user = new User(email, username, passwordHash, clock.instant());
        // user.getEmail() is the lower-cased email, the form it is stored in.
        if (users.existsByEmail(user.getEmail())) {
            throw new EmailTakenException();
        }
        if (users.existsByUsernameIgnoringCase(username)) {
            throw new UsernameTakenException();
        }
        try {
            // saveAndFlush sends the INSERT now. With save(), it would only run at commit,
            // after this method returns, and the catch below would never see the violation.
            return AccountView.of(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // Another registration took the email or username after the checks above.
            if (isViolationOf(exception, "users_email_key")) {
                throw new EmailTakenException();
            }
            if (isViolationOf(exception, "users_username_lower_key")) {
                throw new UsernameTakenException();
            }
            throw exception;
        }
    }

    /**
     * Finds a user by ID. A deleted account is reported as not found, so other modules can
     * show its content as "[deleted]" without knowing anything about deletion.
     */
    public Optional<UserView> findById(UUID id) {
        return users.findById(id)
                .filter(user -> !user.isDeleted())
                .map(UserView::of);
    }

    /** The owner's view of an account, with its email. A deleted account is not found. */
    public Optional<AccountView> findAccount(UUID id) {
        return users.findById(id)
                .filter(user -> !user.isDeleted())
                .map(AccountView::of);
    }

    /**
     * The credentials of an account, for checking its password. Only active regular accounts
     * have credentials, so the bot and deleted accounts are not found.
     */
    public Optional<UserCredentials> findCredentialsById(UUID id) {
        return users.findById(id)
                .filter(user -> user.getPasswordHash() != null)
                .map(UserCredentials::of);
    }

    /**
     * Deletes an account, the way Reddit does it: the row stays as a tombstone, the email and
     * password hash are cleared, and the username stays taken. The account's posts, comments
     * and likes are not touched. Deleting an account that is already deleted changes nothing.
     * The caller must have checked the password first.
     *
     * @throws UserNotFoundException if no user has this ID
     * @throws IllegalStateException if it is the bot
     */
    @Transactional
    public void delete(UUID id) {
        var user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        // No save() needed: the change is written when the transaction commits (dirty checking).
        user.delete(clock.instant());
    }

    /**
     * Finds several users at once, for example the authors of a page of posts, in one query
     * instead of one per user. Deleted accounts and unknown IDs are left out of the map.
     */
    public Map<UUID, UserView> findAllById(Collection<UUID> ids) {
        return users.findAllById(Set.copyOf(ids)).stream()
                .filter(user -> !user.isDeleted())
                .collect(Collectors.toMap(User::getId, UserView::of));
    }

    /**
     * The credentials of the account with this email, for logging in. The email is compared
     * in lower case, the form it is stored in. The bot and deleted accounts have no email,
     * so they are never found.
     */
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return users.findByEmail(email.toLowerCase(Locale.ROOT)).map(UserCredentials::of);
    }
}
