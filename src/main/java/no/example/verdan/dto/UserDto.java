package no.example.verdan.dto;

/**
 * DTOs for user-related API operations.
 */
public final class UserDto {
    private UserDto() {}

    /** Request body for creating a new user. */
    public record CreateRequest(String username, String password, String role,
            String firstName, String lastName, String email, String phone,
            String gender, String birthDate, Integer institutionId) {}

    /** Request body for updating an existing user. */
    public record UpdateRequest(String username, String password, String role,
            String firstName, String lastName, String email, String phone,
            String gender, String birthDate, Integer institutionId) {}

    /** Response body for user data (never includes password). */
    public record Response(int id, String username, String role,
            String firstName, String lastName, String email, String phone,
            String gender, String birthDate, Integer institutionId, String institutionName,
            Integer transferredFromInstitutionId, String transferredFromInstitutionName) {}
}
