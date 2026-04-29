package no.example.verdan.service;

import java.util.List;

/**
 * Custom exception for validation errors.
 * Carries a list of human-readable error messages.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed: " + String.join(", ", errors));
        this.errors = errors;
    }

    public ValidationException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
