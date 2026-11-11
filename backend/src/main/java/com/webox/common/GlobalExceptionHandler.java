package com.webox.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/** Typed errors -> one normalised JSON shape. No stack traces ever leave the server. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<?>> handleApi(ApiException e) {
        JsonLog.warn("business_error", "code", e.getCode().name(), "message", e.getMessage());
        return ResponseEntity.status(e.getCode().status())
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::friendlyFieldError)
                .distinct()
                .collect(Collectors.joining("; "));
        return bad(message.isBlank() ? "Please check your input." : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraint(ConstraintViolationException e) {
        return bad(e.getMessage() == null ? "Please check your input." : e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleUnreadable(HttpMessageNotReadableException e) {
        return bad("Request body is not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException e) {
        return bad("Missing required parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleUpload(MaxUploadSizeExceededException e) {
        return bad("Image is too large. Maximum size is 5 MB.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleIntegrity(DataIntegrityViolationException e) {
        JsonLog.warn("db_constraint_violation", "detail", "unique or foreign key conflict");
        return ResponseEntity.status(ErrorCode.DUPLICATE_ORDER.status())
                .body(ApiResponse.fail(ErrorCode.DUPLICATE_ORDER,
                        "This record already exists. Please refresh and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpected(Exception e) {
        JsonLog.error("unhandled_exception", "type", e.getClass().getSimpleName(),
                "message", String.valueOf(e.getMessage()));
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR,
                        "Something went wrong. Please try again."));
    }

    private ResponseEntity<ApiResponse<?>> bad(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR, message));
    }

    private String friendlyFieldError(FieldError error) {
        String field = error.getField();
        String defaultMessage = error.getDefaultMessage();
        if (defaultMessage != null && defaultMessage.toLowerCase().contains("email")) {
            return "Please enter a valid email address.";
        }
        return field + ": " + defaultMessage;
    }
}
