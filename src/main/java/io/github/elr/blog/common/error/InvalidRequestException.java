package io.github.elr.blog.common.error;

/**
 * Base class for a request that is well-formed but breaks a rule that only the service can
 * check, for example a reply whose parent comment belongs to another post. The API reports it
 * like any other invalid field: 400 VALIDATION_FAILED, with the field and message in {@code errors}.
 */
public abstract class InvalidRequestException extends RuntimeException {

    private final String field;

    protected InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
