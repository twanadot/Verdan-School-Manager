package no.example.verdan.service;

/**
 * Custom exception for duplicate/conflict errors.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
