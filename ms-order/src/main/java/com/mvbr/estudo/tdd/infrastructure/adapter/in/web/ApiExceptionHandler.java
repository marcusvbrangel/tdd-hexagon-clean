package com.mvbr.estudo.tdd.infrastructure.adapter.in.web;

import com.mvbr.estudo.tdd.domain.exception.DomainException;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ApiError> handleInvalidOrder(InvalidOrderException ex, HttpServletRequest request) {
        log.error("Invalid order exception: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(DomainException ex, HttpServletRequest request) {
        log.warn("Domain exception: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity
                .badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("Validation error: {} - Path: {}", message, request.getRequestURI());
        return ResponseEntity
                .badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), message, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on path: {} - Error: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unexpected error", request.getRequestURI()));
    }
}
