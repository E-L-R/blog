package io.github.elr.blog.common.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tells which database constraint refused a write.
 * <p>
 * Services check their rules before writing, but a concurrent request can still break one
 * between the check and the write: for example, a post deleted while someone comments on it.
 * The database then refuses the write, and the constraint's name says which rule it was, so
 * the service can report the same error as its own check would have. Constraint names are
 * set in the migrations, so renaming one there means changing the code that refers to it.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** Whether the write was refused because of the constraint with this name. */
    public static boolean isViolationOf(DataIntegrityViolationException exception, String constraintName) {
        return exception.getCause() instanceof ConstraintViolationException cause
                && constraintName.equals(cause.getConstraintName());
    }
}
