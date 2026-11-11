package com.webox.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already registered"),
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "An active order already exists"),
    STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "Insufficient stock"),
    RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Ordering rule violated"),
    ORDER_STATE_CONFLICT(HttpStatus.CONFLICT, "Order state conflict"),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Assistant temporarily unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public String defaultMessage() { return defaultMessage; }
}
