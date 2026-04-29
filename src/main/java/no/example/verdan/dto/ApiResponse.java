package no.example.verdan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard API response wrapper for all endpoints.
 * Provides consistent JSON structure across the entire API.
 *
 * @param <T> the type of the response data
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        java.util.List<String> errors,
        String timestamp
) {
    /** Create a successful response with data. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now().toString());
    }

    /** Create a successful response with no data (e.g. 204). */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null, Instant.now().toString());
    }

    /** Create an error response with a single message. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null, Instant.now().toString());
    }

    /** Create a validation error response with multiple messages. */
    public static <T> ApiResponse<T> validationError(java.util.List<String> errors) {
        return new ApiResponse<>(false, null, "Validation failed", errors, Instant.now().toString());
    }
}
