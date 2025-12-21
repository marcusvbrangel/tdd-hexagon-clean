package com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.exception;

import com.mvbr.retailstore.customer.domain.exception.DomainException;
import com.mvbr.retailstore.customer.domain.exception.InvalidCustomerException;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.ApiErrorResponse;
import com.mvbr.retailstore.customer.infrastructure.adapter.in.rest.dto.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class CustomerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerExceptionHandler.class);

    @ExceptionHandler(InvalidCustomerException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCustomer(InvalidCustomerException ex,
                                                                  HttpServletRequest request) {
        log.warn("Invalid customer: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        log.warn("Domain error: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_ERROR", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        log.warn("Illegal argument: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("Validation error: {} - Path: {}", fieldErrors, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(resolveParameterName(result), error.getDefaultMessage())))
                .toList();
        log.warn("Method validation error: {} - Path: {}", fieldErrors, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "METHOD_VALIDATION_ERROR", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        resolveFieldName(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        log.warn("Constraint violation: {} - Path: {}", fieldErrors, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("Bind error: {} - Path: {}", fieldErrors, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "BIND_ERROR", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String requiredType = ex.getRequiredType() == null ? "unknown" : ex.getRequiredType().getSimpleName();
        String message = "Parameter '" + ex.getName() + "' must be of type " + requiredType;
        log.warn("Type mismatch: {} - Path: {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpServletRequest request) {
        String message = "Missing required parameter '" + ex.getParameterName() + "'";
        log.warn("Missing request parameter: {} - Path: {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request, List.of());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPathVariable(MissingPathVariableException ex,
                                                                      HttpServletRequest request) {
        String message = "Missing path variable '" + ex.getVariableName() + "'";
        log.warn("Missing path variable: {} - Path: {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PATH_VARIABLE", message, request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
                                                                     HttpServletRequest request) {
        log.warn("Malformed JSON: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed JSON request", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        String message = "Method '" + ex.getMethod() + "' not supported";
        log.warn("Method not supported: {} - Path: {}", message, request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                        HttpServletRequest request) {
        String contentType = ex.getContentType() == null ? "unknown" : ex.getContentType().toString();
        String message = "Content type '" + contentType + "' not supported";
        log.warn("Media type not supported: {} - Path: {}", message, request.getRequestURI());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message, request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   String code,
                                                   String message,
                                                   HttpServletRequest request,
                                                   List<FieldErrorResponse> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                code,
                message,
                request.getRequestURI(),
                Instant.now(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private String resolveParameterName(org.springframework.validation.method.ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "arg" + result.getMethodParameter().getParameterIndex();
    }

    private String resolveFieldName(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "value";
        }
        int lastDot = propertyPath.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < propertyPath.length() - 1) {
            return propertyPath.substring(lastDot + 1);
        }
        return propertyPath;
    }
}
