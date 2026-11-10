package io.github.elr.blog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * Spring Data generates the implementation of this interface at startup. A method such
 * as {@code existsByEmail} is turned into a query from its name.
 */
interface UserRepository extends JpaRepository<User, UUID> {

    // Emails are always stored in lower case, so plain equality is enough once the caller lower-cases too.
    boolean existsByEmail(String email);

    // Written by hand to compare lower(username), the same expression as the unique index
    // users_username_lower_key, so the check agrees exactly with what the database enforces.
    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(String username);
}
