package no.example.verdan.service;

/**
 * Custom exception for resource not found errors.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
