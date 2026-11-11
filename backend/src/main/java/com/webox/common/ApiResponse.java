package com.webox.common;

/** Uniform envelope: clients only ever see normalised success/error shapes. */
public record ApiResponse<T>(boolean success, T data, ErrorBody error, String requestId) {

    public record ErrorBody(String code, String message) {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, RequestContext.requestId());
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null,
                new ErrorBody(code.name(), message), RequestContext.requestId());
    }
}
