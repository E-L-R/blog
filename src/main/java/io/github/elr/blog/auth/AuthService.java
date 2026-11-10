package io.github.elr.blog.auth;

import io.github.elr.blog.user.AccountView;
import io.github.elr.blog.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    AuthService(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    public AccountView register(String email, String username, String password) {
        return userService.create(email, username, passwordEncoder.encode(password));
    }
}
