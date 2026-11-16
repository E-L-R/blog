package io.github.elr.blog.common.error;

/**
 * Base class for failures that the API reports as 401 Unauthorized: the caller is not
 * logged in, or could not prove who they are. The code identifies the failure for API
 * clients, for example INVALID_CREDENTIALS.
 * <p>
 * Works like {@link ConflictException}: modules extend it, and {@link GlobalExceptionHandler}
 * only knows this base class. The message becomes the response's {@code detail}.
 */
public abstract class UnauthorizedException extends RuntimeException {

    private final String code;

    protected UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
