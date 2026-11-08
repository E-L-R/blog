package io.github.elr.blog.user;

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
    public UserView create(String email, String username, String passwordHash) {
        var user = users.save(new User(email, username, passwordHash, clock.instant()));
        return UserView.of(user);
    }

    public Optional<UserView> findById(UUID id) {
        return users.findById(id)
                .filter(user -> !user.isDeleted())
                .map(UserView::of);
    }
}