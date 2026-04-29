package no.example.verdan.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for booking-related API operations.
 */
public final class BookingDto {
    private BookingDto() {}

    /** Request body for creating a booking. */
    public record Request(Integer roomId, LocalDateTime startDateTime,
            LocalDateTime endDateTime, String description, String subject, Integer programId) {}

    /** Response body for booking data. */
    public record Response(Integer id, List<String> rooms,
            LocalDateTime startDateTime, LocalDateTime endDateTime,
            String status, String description, String createdBy, String subject,
            Integer programId, String programName) {}
}
