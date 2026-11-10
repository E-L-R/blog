package io.github.elr.blog.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(String username);
}
