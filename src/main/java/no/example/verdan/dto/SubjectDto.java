package no.example.verdan.dto;

import java.util.List;

/**
 * DTOs for subject-related API operations.
 */
public final class SubjectDto {
    private SubjectDto() {}

    /** Request body for creating/updating a subject. */
    public record Request(String code, String name, String description, String level,
                          String teacherUsername, Integer institutionId,
                          String program, String yearLevel) {}

    /** Response body for subject data. */
    public record Response(int id, String code, String name, String description, String level,
                           Integer institutionId, String institutionName,
                           String program, String yearLevel) {}

    /** Request body for assigning a user to a subject. */
    public record AssignRequest(String username, String role) {}

    /** Response body containing all members of a subject. */
    public record MembersResponse(List<UserDto.Response> students, List<UserDto.Response> teachers) {}
}

