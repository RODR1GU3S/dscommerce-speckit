package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.ProductCategoryDiagnosticService;
import com.devsuperior.dscommerce.services.ProductInputValidationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Adapts service diagnoses only; no repository, business policy, request mutation or maintenance call. */
public class ProductInputConstraintValidator implements ConstraintValidator<ValidProductInput, ProductWriteRequestDTO> {
    private final ProductInputValidationService local;
    private final ProductCategoryDiagnosticService categories;

    public ProductInputConstraintValidator(ProductInputValidationService local, ProductCategoryDiagnosticService categories) {
        this.local = local;
        this.categories = categories;
    }

    @Override
    public boolean isValid(ProductWriteRequestDTO input, ConstraintValidatorContext context) {
        // Missing/non-object bodies are handled by the reader/MVC, not invented as five field violations.
        if (input == null) return true;
        var diagnostic = categories.diagnose(input, local.validate(input).diagnostic(), principal());
        if (diagnostic.errors().isEmpty()) return true;
        context.disableDefaultConstraintViolation();
        var hibernate = context.unwrap(HibernateConstraintValidatorContext.class);
        for (var error : diagnostic.errors()) addViolation(hibernate, error);
        // Service/guard failures are intentionally not caught: Bean Validation retains them as runtime causes.
        return false;
    }

    private static AuthenticatedAccountPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal ? principal : null;
    }

    private static void addViolation(HibernateConstraintValidatorContext context, AdminFieldErrorDTO error) {
        // Preserve the service's field/code/message without encoding codes into human-readable messages.
        var builder = context.withDynamicPayload(error).buildConstraintViolationWithTemplate(error.message());
        String field = error.field();
        if (field.startsWith("categoryIds[") && field.endsWith("]")) {
            int index = Integer.parseInt(field.substring("categoryIds[".length(), field.length() - 1));
            builder.addPropertyNode("categoryIds").addBeanNode().inIterable().atIndex(index).addConstraintViolation();
        } else builder.addPropertyNode(field).addConstraintViolation();
    }
}
