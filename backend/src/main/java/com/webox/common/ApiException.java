package com.webox.common;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }
}
