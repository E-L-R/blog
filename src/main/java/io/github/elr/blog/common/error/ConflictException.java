package io.github.elr.blog.common.error;

/**
 * Base class for failures that the API reports as 409 Conflict.
 * The code identifies the failure for API clients, for example EMAIL_TAKEN.
 */
public abstract class ConflictException extends RuntimeException {

    private final String code;

    protected ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
