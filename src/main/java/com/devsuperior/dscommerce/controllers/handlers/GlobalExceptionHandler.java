package com.devsuperior.dscommerce.controllers.handlers;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.devsuperior.dscommerce.dto.AdminErrorResponseDTO;
import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.controllers.AdminQueryParameters;
import com.devsuperior.dscommerce.validation.ValidProductInput;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.devsuperior.dscommerce.services.exceptions.InvalidOrderException;
import com.devsuperior.dscommerce.services.exceptions.InvalidProductInputException;
import com.devsuperior.dscommerce.services.exceptions.ProductCreationPersistenceException;
import com.devsuperior.dscommerce.services.exceptions.ProductDeletionIntegrityException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.sql.DataSource;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String LOGIN_PATH = "/login";
    private static final String ORDER_PATH = "/orders";
    private static final String ADMIN_PATH = "/admin";
    private static final String ADMIN_AUTHENTICATION_MESSAGE = "Authentication required or invalid";
    private static final String ADMIN_ACCESS_MESSAGE = "Product maintenance not permitted";
    private static final String ADMIN_PROCESSING_MESSAGE = "Product maintenance could not be completed";
    private static final String INVALID_ADMIN_DATA_MESSAGE = "Invalid product maintenance data";
    private static final List<String> ADMIN_FIELDS = List.of(
            "body", "id", "page", "size", "name", "description", "price", "image", "categoryIds");
    private static final String INVALID_ORDER_DATA_MESSAGE = "Invalid order data";
    private static final String ORDER_AUTHENTICATION_MESSAGE = "Authentication required or invalid";
    private static final String ORDER_ACCESS_MESSAGE = "Order registration not permitted";
    private static final String ORDER_PROCESSING_MESSAGE = "Order registration could not be completed";
    private static final String INVALID_LOGIN_DATA_MESSAGE = "Invalid login data";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String AUTHENTICATION_PROCESSING_MESSAGE =
            "Authentication could not be completed";
    private static final Pattern ADMIN_PRODUCT_ITEM = Pattern.compile("/admin/products/[1-9][0-9]*");
    private static final Pattern PRODUCT_DELETE = Pattern.compile(
            "delete\\s+from\\s+tb_product\\s+where\\s+id\\s*=\\s*\\?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORY_PAIR_INSERT = Pattern.compile(
            "insert\\s+into\\s+tb_product_category\\s*\\(\\s*product_id\\s*,\\s*category_id\\s*\\)"
                    + "\\s*values\\s*\\(\\s*\\?\\s*,\\s*\\?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private DataSource persistenceDataSource;

    /** Optional in MVC slices; absent/unavailable metadata never produces a business diagnosis. */
    @Autowired(required = false)
    public void setPersistenceDataSource(DataSource persistenceDataSource) {
        this.persistenceDataSource = persistenceDataSource;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<?> handleProductNotFound(ProductNotFoundException exception, HttpServletRequest request) {
        var status = HttpStatus.NOT_FOUND;
        if (isAdminRequest(request)) {
            return adminErrorResponse(status, "Product not found", request);
        }
        var message = isOrderRequest(request) ? "Product not found" : exception.getMessage();
        return errorResponse(status, message, request);
    }

    @ExceptionHandler(AdminQueryParameters.InvalidParameterException.class)
    public ResponseEntity<AdminErrorResponseDTO> handleAdminQueryParameter(
            AdminQueryParameters.InvalidParameterException exception, HttpServletRequest request) {
        if (!isAdminRequest(request)) throw exception;
        var error = new AdminFieldErrorDTO(exception.getField(), "INVALID_PARAMETER", "Invalid request parameter");
        return adminInputResponse(List.of(error), request);
    }

    @ExceptionHandler(InvalidProductInputException.class)
    public ResponseEntity<AdminErrorResponseDTO> handleInvalidProductInput(
            InvalidProductInputException exception, HttpServletRequest request) {
        if (!isAdminRequest(request)) throw exception;
        return adminInputResponse(exception.getErrors(), request);
    }

    @ExceptionHandler(ProductCreationPersistenceException.class)
    public ResponseEntity<AdminErrorResponseDTO> handleProductCreationPersistence(
            ProductCreationPersistenceException exception, HttpServletRequest request) {
        if (!isAdminRequest(request)) throw exception;
        var applicationPath = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && "/admin/products".equals(applicationPath)
                && exception.isMissingCategoryReference()) {
            return adminInputResponse(List.of(new AdminFieldErrorDTO(
                    "categoryIds", "CATEGORY_NOT_FOUND", "Selected category does not exist")), request);
        }
        return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return adminBindingResponse(exception.getBindingResult().getAllErrors(), request);
        }
        var status = HttpStatus.BAD_REQUEST;
        var message = isOrderRequest(request) ? INVALID_ORDER_DATA_MESSAGE : isLoginRequest(request)
                ? INVALID_LOGIN_DATA_MESSAGE
                : "Validation failed";
        var body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        if (isAdminRequest(request)) {
            // Reader failures, initial parser errors and absent/empty bodies carry no complete field evidence.
            return adminInputResponse(List.of(new AdminFieldErrorDTO(
                    "body", "INVALID_BODY", "Invalid request body")), request);
        }
        if (isOrderRequest(request)) {
            return errorResponse(HttpStatus.BAD_REQUEST, INVALID_ORDER_DATA_MESSAGE, request);
        }
        if (!isLoginRequest(request)) {
            throw exception;
        }
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                INVALID_LOGIN_DATA_MESSAGE,
                request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                isOrderRequest(request) ? ORDER_AUTHENTICATION_MESSAGE : INVALID_CREDENTIALS_MESSAGE,
                request);
    }

    @ExceptionHandler(AuthenticationProcessingException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationProcessing(
            AuthenticationProcessingException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                isOrderRequest(request) ? ORDER_PROCESSING_MESSAGE : AUTHENTICATION_PROCESSING_MESSAGE,
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            var errors = new ArrayList<AdminFieldErrorDTO>();
            for (var violation : exception.getConstraintViolations()) {
                var error = adminInputDiagnostic(violation);
                if (error == null) return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
                errors.add(error);
            }
            // Entity/persistence constraints are internal; only the scoped input constraint is a consumer error.
            return adminDiagnosedResponse(errors, request);
        }
        if (isOrderRequest(request)) {
            // Incoming DTO validation is handled by handleValidation; service/persistence violations are internal.
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ORDER_PROCESSING_MESSAGE, request);
        }
        var status = HttpStatus.BAD_REQUEST;
        var message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");
        var body = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        if (!isAdminRequest(request)) throw exception;
        if (exception.isForReturnValue()) {
            return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
        }
        var errors = new ArrayList<ObjectError>();
        for (var result : exception.getAllValidationResults()) {
            if (!(result instanceof ParameterErrors parameterErrors)) {
                return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
            }
            errors.addAll(parameterErrors.getAllErrors());
        }
        return adminBindingResponse(errors, request);
    }

    private static ResponseEntity<AdminErrorResponseDTO> adminBindingResponse(
            List<ObjectError> bindingErrors, HttpServletRequest request) {
        var errors = new ArrayList<AdminFieldErrorDTO>();
        for (var bindingError : bindingErrors) {
            if (!bindingError.contains(ConstraintViolation.class)) {
                return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
            }
            var error = adminInputDiagnostic(bindingError.unwrap(ConstraintViolation.class));
            if (error == null) return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
            errors.add(error);
        }
        return adminDiagnosedResponse(errors, request);
    }

    private static AdminFieldErrorDTO adminInputDiagnostic(ConstraintViolation<?> violation) {
        if (!(violation.getConstraintDescriptor().getAnnotation() instanceof ValidProductInput)) return null;
        HibernateConstraintViolation<?> coded = violation.unwrap(HibernateConstraintViolation.class);
        return coded.getDynamicPayload(AdminFieldErrorDTO.class);
    }

    private static ResponseEntity<AdminErrorResponseDTO> adminDiagnosedResponse(
            List<AdminFieldErrorDTO> errors, HttpServletRequest request) {
        if (errors.isEmpty()) return adminErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_PROCESSING_MESSAGE, request);
        return adminInputResponse(errors, request);
    }

    private static ResponseEntity<AdminErrorResponseDTO> adminInputResponse(
            List<AdminFieldErrorDTO> errors, HttpServletRequest request) {
        var ordered = errors.stream().distinct().sorted(Comparator
                .comparingInt((AdminFieldErrorDTO error) -> adminFieldOrder(error.field()))
                .thenComparingInt(error -> adminFieldIndex(error.field()))
                .thenComparing(AdminFieldErrorDTO::code)
                .thenComparing(AdminFieldErrorDTO::message)).toList();
        var status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new AdminErrorResponseDTO(status.value(), status.getReasonPhrase(),
                INVALID_ADMIN_DATA_MESSAGE, request.getRequestURI(), ordered));
    }

    private static int adminFieldOrder(String field) {
        return ADMIN_FIELDS.indexOf(field.startsWith("categoryIds[") ? "categoryIds" : field);
    }

    private static int adminFieldIndex(String field) {
        return field.startsWith("categoryIds[") ? Integer.parseInt(field.substring("categoryIds[".length(), field.length() - 1)) : -1;
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidOrder(
            InvalidOrderException exception, HttpServletRequest request) {
        if (!isOrderRequest(request)) {
            throw exception;
        }
        var message = switch (exception.getReason()) {
            case DUPLICATE_PRODUCT -> "Duplicate product in order";
            case INVALID_PRODUCT_PRICE -> "Product price is not valid for order";
            case UNREPRESENTABLE_VALUE -> "Order value cannot be represented";
        };
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, message, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleOrderAuthentication(
            BadCredentialsException exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return adminErrorResponse(HttpStatus.UNAUTHORIZED, ADMIN_AUTHENTICATION_MESSAGE, request);
        }
        if (!isOrderRequest(request)) {
            throw exception;
        }
        return errorResponse(HttpStatus.UNAUTHORIZED, ORDER_AUTHENTICATION_MESSAGE, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleOrderAccess(
            AccessDeniedException exception, HttpServletRequest request) {
        if (isAdminRequest(request)) {
            return adminErrorResponse(HttpStatus.FORBIDDEN, ADMIN_ACCESS_MESSAGE, request);
        }
        if (!isOrderRequest(request)) {
            throw exception;
        }
        return errorResponse(HttpStatus.FORBIDDEN, ORDER_ACCESS_MESSAGE, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOrderProcessing(
            Exception exception, HttpServletRequest request) throws Exception {
        // Let MVC preserve client-error statuses and headers; keep internal failures safely sanitized.
        boolean nativeClientError = exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError();
        if (isAdminRequest(request) && nativeClientError && exception instanceof ErrorResponse error
                && List.of(405, 406, 415).contains(error.getStatusCode().value())) {
            var status = HttpStatus.valueOf(error.getStatusCode().value());
            var response = ResponseEntity.status(status).headers(error.getHeaders());
            // Offering an unwritable body makes MVC log an exception-handler stack trace before its fallback.
            // Keep native status/headers and omit only the representation the client cannot accept.
            if (!acceptsAdministrativeJsonError(request)) return response.build();
            return response.body(new AdminErrorResponseDTO(
                    status.value(), status.getReasonPhrase(), "Product maintenance request not supported",
                    request.getRequestURI(), List.of()));
        }
        if (isAdminRequest(request) && !nativeClientError) {
            var persistenceResponse = adminPersistenceResponse(exception, request);
            if (persistenceResponse != null) return persistenceResponse;
            var status = exception instanceof ValidationException validation
                    ? adminValidationStatus(validation) : HttpStatus.INTERNAL_SERVER_ERROR;
            var message = switch (status) {
                case UNAUTHORIZED -> ADMIN_AUTHENTICATION_MESSAGE;
                case FORBIDDEN -> ADMIN_ACCESS_MESSAGE;
                default -> ADMIN_PROCESSING_MESSAGE;
            };
            return adminErrorResponse(status, message, request);
        }
        if (!isOrderRequest(request) || nativeClientError) {
            throw exception;
        }
        // Includes failures raised by transaction completion after the service method returns.
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ORDER_PROCESSING_MESSAGE, request);
    }

    private ResponseEntity<AdminErrorResponseDTO> adminPersistenceResponse(Exception exception, HttpServletRequest request) {
        // Completion exceptions arrive here after the service proxy has unwound. Never inspect an aborted transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return null;
        var path = request.getRequestURI().substring(request.getContextPath().length());
        boolean deletion = "DELETE".equals(request.getMethod()) && ADMIN_PRODUCT_ITEM.matcher(path).matches();
        boolean writing = ("POST".equals(request.getMethod()) && "/admin/products".equals(path))
                || ("PUT".equals(request.getMethod()) && ADMIN_PRODUCT_ITEM.matcher(path).matches());
        if (!deletion && !writing) return null;
        if (deletion && exception instanceof ProductDeletionIntegrityException) {
            return adminErrorResponse(HttpStatus.CONFLICT, "Product deletion violates referential integrity", request);
        }
        var violation = referentialCause(exception);
        if (violation == null || violation.getSQL() == null) return null;
        String state = deletion ? "23503" : "23506";
        var sqlException = violation.getSQLException();
        if (!state.equals(violation.getSQLState()) || !state.equals(sqlException.getSQLState())
                || sqlException.getErrorCode() != Integer.parseInt(state)) return null;
        var operation = deletion ? PRODUCT_DELETE : CATEGORY_PAIR_INSERT;
        if (!operation.matcher(violation.getSQL().strip()).matches()) return null;
        // Hibernate's H2 23503 name includes trailing text; its 23506 extractor loses the first character.
        // Recover only the complete vendor header identity, never search the appended SQL/payload or guess a suffix.
        var prefix = "Referential integrity constraint violation: \"";
        var message = sqlException.getMessage();
        if (message == null || !message.startsWith(prefix)) return null;
        var name = org.hibernate.exception.spi.TemplatedViolatedConstraintNameExtractor
                .extractUsingTemplate(prefix, ": ", message);
        if (name == null || name.isBlank() || !confirmedForeignKey(name, deletion)) return null;
        if (deletion) return adminErrorResponse(HttpStatus.CONFLICT, "Product deletion violates referential integrity", request);
        return adminInputResponse(List.of(new AdminFieldErrorDTO(
                "categoryIds", "CATEGORY_NOT_FOUND", "Selected category does not exist")), request);
    }

    private static org.hibernate.exception.ConstraintViolationException referentialCause(Throwable failure) {
        var visited = new HashSet<Throwable>();
        for (var cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) return violation;
            // Only transaction/persistence transport wrappers; an unknown internal failure with a nested FK stays 500.
            var type = cause.getClass();
            if (type != org.springframework.dao.DataIntegrityViolationException.class
                    && type != org.springframework.transaction.TransactionSystemException.class
                    && type != org.springframework.orm.jpa.JpaSystemException.class
                    && type != jakarta.persistence.PersistenceException.class
                    && type != jakarta.persistence.RollbackException.class) return null;
        }
        return null;
    }

    private boolean confirmedForeignKey(String name, boolean deletion) {
        if (persistenceDataSource == null) return false;
        // Technical metadata only on a fresh connection after rollback; no row lookup or continuation of maintenance.
        try (var connection = persistenceDataSource.getConnection()) {
            var metadata = connection.getMetaData();
            if (!"H2".equals(metadata.getDatabaseProductName())) return false;
            var schema = connection.getSchema(); var catalog = connection.getCatalog();
            if (schema == null || catalog == null) return false;
            try (var keys = deletion ? metadata.getExportedKeys(catalog, schema, "TB_PRODUCT")
                    : metadata.getImportedKeys(catalog, schema, "TB_PRODUCT_CATEGORY")) {
                int matches = 0; boolean confirmed = true;
                while (keys.next()) {
                    if (!name.equals(keys.getString("FK_NAME"))) continue;
                    matches++;
                    boolean sameScope = schema.equals(keys.getString("FKTABLE_SCHEM"))
                            && schema.equals(keys.getString("PKTABLE_SCHEM"))
                            && catalog.equals(keys.getString("FKTABLE_CAT")) && catalog.equals(keys.getString("PKTABLE_CAT"))
                            && keys.getShort("KEY_SEQ") == 1 && "ID".equals(keys.getString("PKCOLUMN_NAME"));
                    boolean reference = deletion ? "TB_PRODUCT".equals(keys.getString("PKTABLE_NAME"))
                            && keys.getString("FKCOLUMN_NAME") != null
                            && (keys.getShort("DELETE_RULE") == DatabaseMetaData.importedKeyRestrict
                            || keys.getShort("DELETE_RULE") == DatabaseMetaData.importedKeyNoAction)
                            : "TB_PRODUCT_CATEGORY".equals(keys.getString("FKTABLE_NAME"))
                            && "CATEGORY_ID".equals(keys.getString("FKCOLUMN_NAME"))
                            && "TB_CATEGORY".equals(keys.getString("PKTABLE_NAME"));
                    confirmed &= sameScope && reference;
                }
                // Composite/ambiguous/unknown identities remain internal, rather than inventing a business defect.
                return matches == 1 && confirmed;
            }
        } catch (SQLException | RuntimeException unavailableMetadata) {
            return false;
        }
    }

    private static HttpStatus adminValidationStatus(ValidationException exception) {
        Throwable cause = exception;
        var wrappers = new HashSet<Throwable>();
        // Unwrap only the known validation wrapper, never an internal failure's deeper cause.
        while (cause != null && cause.getClass() == ValidationException.class && wrappers.add(cause)) {
            cause = cause.getCause();
        }
        if (cause instanceof BadCredentialsException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (cause instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ResponseEntity<AdminErrorResponseDTO> adminErrorResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(new AdminErrorResponseDTO(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), List.of()));
    }

    private static boolean acceptsAdministrativeJsonError(HttpServletRequest request) {
        try {
            var accepted = MediaType.parseMediaTypes(Collections.list(request.getHeaders(HttpHeaders.ACCEPT)));
            return accepted.isEmpty() || accepted.stream().anyMatch(type -> type.getQualityValue() > 0
                    && (type.isCompatibleWith(MediaType.APPLICATION_JSON)
                    || type.isCompatibleWith(new MediaType("application", "*+json"))));
        } catch (IllegalArgumentException invalidAccept) {
            // The native 406 already describes the invalid negotiation; do not fail while writing its error.
            return false;
        }
    }

    private static boolean isAdminRequest(HttpServletRequest request) {
        var applicationPath = request.getRequestURI().substring(request.getContextPath().length());
        return ADMIN_PATH.equals(applicationPath) || applicationPath.startsWith(ADMIN_PATH + "/");
    }

    private static ResponseEntity<ErrorResponseDTO> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        var body = new ErrorResponseDTO(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.UNAUTHORIZED && isOrderRequest(request)) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(body);
    }

    private static boolean isOrderRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && ORDER_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    private static boolean isLoginRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = requestUri.substring(contextPath.length());
        return LOGIN_PATH.equals(applicationPath);
    }
}
