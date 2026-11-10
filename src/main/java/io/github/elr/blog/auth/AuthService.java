package io.github.elr.blog.auth;

import io.github.elr.blog.user.AccountView;
import io.github.elr.blog.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registers accounts. Hashing passwords is the job of this module, so the {@code user}
 * module only ever receives a hash.
 * <p>
 * This service is deliberately not {@code @Transactional}. bcrypt is slow on purpose, and
 * hashing inside a transaction would hold a database connection for the whole time.
 * {@link UserService#create} opens its own short transaction once the hash is ready.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a regular user account.
     *
     * @return the new account, as its owner sees it
     * @throws io.github.elr.blog.user.EmailTakenException    if the email is already registered
     * @throws io.github.elr.blog.user.UsernameTakenException if the username is taken, ignoring case
     */
    public AccountView register(String email, String username, String password) {
        return userService.create(email, username, passwordEncoder.encode(password));
    }
}
