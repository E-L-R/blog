package io.github.elr.blog.common.error;

/**
 * Base class for failures that the API reports as 403 Forbidden: the caller is logged in,
 * but may not do this. The code identifies the failure for API clients, for example NOT_AUTHOR.
 * <p>
 * Works like {@link ConflictException}: modules extend it, and {@link GlobalExceptionHandler}
 * only knows this base class. The message becomes the response's {@code detail}.
 */
public abstract class ForbiddenException extends RuntimeException {

    private final String code;

    protected ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
