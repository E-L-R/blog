package io.github.elr.blog.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

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
            if (violates(exception, "users_email_key")) {
                throw new EmailTakenException();
            }
            if (violates(exception, "users_username_lower_key")) {
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

    /**
     * Whether the database refused the insert because of the named constraint. The names come
     * from the migrations, so renaming either constraint there means changing them here too.
     */
    private static boolean violates(DataIntegrityViolationException exception, String constraintName) {
        return exception.getCause() instanceof ConstraintViolationException cause
                && constraintName.equals(cause.getConstraintName());
    }
}
