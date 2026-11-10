/**
 * Authentication: registration, password hashing and the security filter chain.
 * <p>
 * The module may use {@code user} and nothing else (apart from the shared {@code common}).
 * It creates accounts through {@code UserService}, so the {@code user} module stores only
 * password hashes and never sees a password.
 */
@ApplicationModule(displayName = "Authentication", allowedDependencies = "user")
package io.github.elr.blog.auth;

import org.springframework.modulith.ApplicationModule;
