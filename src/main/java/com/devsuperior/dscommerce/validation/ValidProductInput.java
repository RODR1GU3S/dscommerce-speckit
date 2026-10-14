package com.devsuperior.dscommerce.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Complete administrative diagnosis at the validation boundary, under the documented gate IV exception. */
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProductInputConstraintValidator.class)
public @interface ValidProductInput {
    String message() default "Invalid product maintenance data";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
