package io.github.elr.blog.auth.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * The text must not be longer than the given number of bytes when encoded as UTF-8.
 * A character can take up to 4 bytes, so this is not the same as a number of characters.
 * <p>
 * A custom Bean Validation constraint has two parts: this annotation, which declares the
 * rule and its message, and the {@link Validator} class named in {@code @Constraint},
 * which checks it. The {@code message}, {@code groups} and {@code payload} attributes are
 * required by the Bean Validation specification; {@code {value}} in the message is
 * replaced by the annotation's value.
 */
@Constraint(validatedBy = MaxBytes.Validator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@interface MaxBytes {

    int value();

    String message() default "must be at most {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxBytes, String> {

        private int max;

        @Override
        public void initialize(MaxBytes annotation) {
            max = annotation.value();
        }

        // null counts as valid, like in the built-in constraints: combine with @NotNull to require a value.
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
