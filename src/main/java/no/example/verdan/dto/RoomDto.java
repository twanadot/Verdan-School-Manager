package no.example.verdan.dto;

/**
 * DTOs for room-related API operations.
 */
public final class RoomDto {
    private RoomDto() {}

    /** Request body for creating/updating a room. */
    public record Request(String roomNumber, String roomType, int capacity, Integer institutionId) {}

    /** Response body for room data. */
    public record Response(int id, String roomNumber, String roomType, int capacity, Integer institutionId, String institutionName) {}
}
