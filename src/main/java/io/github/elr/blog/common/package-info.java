/**
 * Shared infrastructure used by every module: the {@link java.time.Clock} bean and the
 * error handling in {@code common.error}.
 * <p>
 * The module is <em>open</em>, so its sub-packages are visible to other modules (normally
 * a module's sub-packages are internal). It is also declared shared on
 * {@code BlogApplication}, so modules use it without listing it as a dependency. Because
 * everything here is visible everywhere, it holds infrastructure only: no business rules
 * and no entities.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package io.github.elr.blog.common;

import org.springframework.modulith.ApplicationModule;
