package io.github.elr.blog.auth;

import io.github.elr.blog.user.AccountView;
import io.github.elr.blog.user.UserCredentials;
import io.github.elr.blog.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Registers accounts and logs users in. Hashing and checking passwords is the job of this
 * module, so the {@code user} module only ever sees password hashes.
 * <p>
 * This service is deliberately not {@code @Transactional}. bcrypt is slow on purpose, and
 * hashing inside a transaction would hold a database connection for the whole time. The
 * {@link UserService} methods it calls open their own short transactions.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * A hash to check against when the email is unknown. Checking a password against it takes
     * as long as a real check, so the response time doesn't reveal whether an email is registered.
     */
    private final String unknownUserHash;

    AuthService(UserService userService, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.unknownUserHash = passwordEncoder.encode("a password that no account has");
    }

    /**
     * Creates a regular user account. Registering does not log the user in.
     *
     * @return the new account, as its owner sees it
     * @throws io.github.elr.blog.user.EmailTakenException    if the email is already registered
     * @throws io.github.elr.blog.user.UsernameTakenException if the username is taken, ignoring case
     */
    public AccountView register(String email, String username, String password) {
        return userService.create(email, username, passwordEncoder.encode(password));
    }

    /**
     * Checks an email and password, and issues an access token for the account.
     *
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     */
    public AccessToken login(String email, String password) {
        var credentials = userService.findCredentialsByEmail(email);
        var hash = credentials.map(UserCredentials::passwordHash).orElse(unknownUserHash);
        var matches = passwordEncoder.matches(password, hash);
        if (credentials.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(credentials.get().id());
    }

    /**
     * Deletes the caller's account, after checking their password. A valid token alone is
     * not enough: the password confirms that the account's owner is asking. Once deleted,
     * every token of the account stops working, because each request checks the account.
     *
     * @throws InvalidPasswordException if the password is wrong
     */
    public void deleteAccount(UUID userId, String password) {
        var credentials = userService.findCredentialsById(userId);
        var hash = credentials.map(UserCredentials::passwordHash).orElse(unknownUserHash);
        if (!passwordEncoder.matches(password, hash) || credentials.isEmpty()) {
            throw new InvalidPasswordException();
        }
        userService.delete(userId);
    }
}
