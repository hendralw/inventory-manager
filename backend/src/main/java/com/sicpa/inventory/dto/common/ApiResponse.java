package com.sicpa.inventory.dto.common;

/**
 * Consistent response envelope for all endpoints.
 * Shape: {@code { "status": "success"|"error", "message": "...", "data": {...} }}
 *
 * @param status  "success" or "error"
 * @param message user-facing message, or {@code null} on GET responses
 * @param data    response payload; {@code null} on error
 */
public record ApiResponse<T>(String status, String message, T data) {

    /** Successful response with data and no message (read operations). */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", null, data);
    }

    /** Successful response with a message and data (write operations). */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }

    /** Error response — {@code data} will be {@code null}. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}
