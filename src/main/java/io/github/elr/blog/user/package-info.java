/**
 * Users: accounts, the AI bot user and deleted accounts.
 * <p>
 * The module depends on no other module. Others use it through {@code UserService} and
 * the read models it returns ({@code UserView}, {@code AccountView}); the {@code User}
 * entity and its repository stay package-private.
 */
@ApplicationModule(displayName = "User Management Module")
package io.github.elr.blog.user;

import org.springframework.modulith.ApplicationModule;
