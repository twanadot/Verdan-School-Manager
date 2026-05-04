package no.example.verdan.model;

/**
 * Enum for application statuses in the admission system.
 * Using an enum instead of raw strings provides compile-time type safety
 * and prevents typos or invalid status values.
 */
public enum ApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CONFIRMED,
    WAITLISTED,
    WITHDRAWN,
    ENROLLED;

    /**
     * Parse a string to ApplicationStatus, case-insensitive.
     * @throws IllegalArgumentException if the string doesn't match any status
     */
    public static ApplicationStatus fromString(String status) {
        if (status == null) return null;
        try {
            return valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ugyldig søknadsstatus: " + status);
        }
    }
}
