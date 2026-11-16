package io.github.elr.blog.common.error;

/**
 * Base class for failures that the API reports as 404 Not Found, always with the code
 * NOT_FOUND. Also used when something exists but the caller may not see it, so the API
 * does not reveal that it exists.
 * <p>
 * Works like {@link ConflictException}: modules extend it, and {@link GlobalExceptionHandler}
 * only knows this base class. The message becomes the response's {@code detail}.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
