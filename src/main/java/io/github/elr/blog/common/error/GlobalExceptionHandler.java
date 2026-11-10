package io.github.elr.blog.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;

/**
 * Turns exceptions thrown by any controller into RFC 9457 Problem Details responses
 * ({@code application/problem+json}). Every response gets a {@code code} property:
 * a stable, machine-readable name for the failure that clients can rely on, unlike
 * the human-readable {@code detail}.
 * <p>
 * Extending {@link ResponseEntityExceptionHandler} gives the same format to the errors
 * that Spring MVC raises itself, such as a body that is not valid JSON.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    /**
     * The last resort for anything no other handler matches. The full exception goes to
     * the log, and the client gets a generic message, so no stack trace or internal
     * detail leaks into the response.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    /**
     * A request body failed {@code @Valid}. The response lists every invalid field with
     * its message, so a client can show all the problems at once.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldMessage(error.getField(), error.getDefaultMessage()))
                // Bean Validation reports errors in no particular order; sorting keeps responses stable.
                .sorted(Comparator.comparing(FieldMessage::field).thenComparing(FieldMessage::message))
                .toList();
        var problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * Every response built by {@link ResponseEntityExceptionHandler} passes through here.
     * Spring's own errors arrive without a {@code code}, so they get the name of their
     * status, for example {@code BAD_REQUEST}. Problems built in this class keep theirs.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The response is null when it has already been sent, and then there is nothing to change.
        var response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty("code", HttpStatus.valueOf(status.value()).name());
        }
        return response;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("code");
    }

    /** One entry of the {@code errors} list in a validation failure. */
    private record FieldMessage(String field, String message) {
    }
}
