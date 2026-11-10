package io.github.elr.blog.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    public static final UUID BOT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository users;
    private final Clock clock;

    UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public AccountView create(String email, String username, String passwordHash) {
        var user = new User(email, username, passwordHash, clock.instant());
        if (users.existsByEmail(user.getEmail())) {
            throw new EmailTakenException();
        }
        if (users.existsByUsernameIgnoringCase(username)) {
            throw new UsernameTakenException();
        }
        try {
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

    public Optional<UserView> findById(UUID id) {
        return users.findById(id)
                .filter(user -> !user.isDeleted())
                .map(UserView::of);
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraintName) {
        return exception.getCause() instanceof ConstraintViolationException cause
                && constraintName.equals(cause.getConstraintName());
    }
}
