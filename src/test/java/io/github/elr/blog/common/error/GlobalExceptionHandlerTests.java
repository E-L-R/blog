package io.github.elr.blog.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks error mappings that are hard to trigger through a real endpoint, such as two edits
 * saved at the same moment. A tiny controller throws the exception, and MockMvc runs it with
 * only this advice: no Spring context, no database.
 */
class GlobalExceptionHandlerTests {

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build());

    @Test
    void anOptimisticLockingFailureIsAnEditConflict() {
        var result = mvc.get().uri("/edit-conflict").exchange();

        assertThat(result).hasStatus(409).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EDIT_CONFLICT");
    }

    @Test
    void anUnexpectedErrorHidesItsDetails() {
        var result = mvc.get().uri("/unexpected").exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("secret internal detail");
    }

    @RestController
    static class FailingController {

        @GetMapping("/edit-conflict")
        void editConflict() {
            throw new OptimisticLockingFailureException("Row was updated or deleted by another transaction");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
