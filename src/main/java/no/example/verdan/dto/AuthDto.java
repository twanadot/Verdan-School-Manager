package no.example.verdan.dto;

/**
 * DTOs for authentication-related API operations.
 */
public final class AuthDto {
    private AuthDto() {}

    /** Request body for login. */
    public record LoginRequest(String username, String password) {}

    /** Response body for successful login. */
    public record LoginResponse(String token, String refreshToken, UserInfo user) {}

    /** User info included in the login response. */
    public record UserInfo(int id, String username, String role,
            String firstName, String lastName, String email,
            Integer institutionId, String institutionName, String institutionLevel) {}

    /** Request body for token refresh. */
    public record RefreshRequest(String refreshToken) {}

    /** Response body for token refresh. */
    public record TokenResponse(String token, String refreshToken) {}
}
