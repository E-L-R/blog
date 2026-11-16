package io.github.elr.blog.user.web;

import io.github.elr.blog.user.UserNotFoundException;
import io.github.elr.blog.user.UserService;
import io.github.elr.blog.user.web.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public user profiles. A deleted account is not found, like one that never existed. */
@RestController
@RequestMapping("/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable UUID id) {
        return userService.findById(id).map(UserResponse::of).orElseThrow(() -> new UserNotFoundException(id));
    }
}
