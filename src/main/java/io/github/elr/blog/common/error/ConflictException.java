package io.github.elr.blog.common.error;

/**
 * Base class for failures that the API reports as 409 Conflict.
 * The code identifies the failure for API clients, for example EMAIL_TAKEN.
 * <p>
 * {@code common} must not depend on the feature modules, so {@link GlobalExceptionHandler}
 * cannot know their exceptions. Instead, a module's exception extends this class and the
 * handler deals with the base class only. The message becomes the response's
 * {@code detail}, so it is shown to clients and must not contain internal information.
 * <p>
 * It is unchecked, so {@code @Transactional} rolls back the transaction it is thrown in.
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
