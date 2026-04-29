package no.example.verdan.dto;

import java.util.List;

/**
 * DTOs for program (linje/degree/fagskolegrad) API operations.
 */
public final class ProgramDto {
    private ProgramDto() {}

    /** Request body for creating/updating a program. */
    public record Request(String name, String description, Integer institutionId,
                          Double minGpa, Integer maxStudents, String prerequisites,
                          Boolean attendanceRequired, Integer minAttendancePct,
                          String programType) {}

    /** Response body for program data. */
    public record Response(int id, String name, String description,
                           Integer institutionId, String institutionName,
                           Double minGpa, Integer maxStudents, String prerequisites,
                           boolean attendanceRequired, Integer minAttendancePct,
                           String programType,
                           List<SubjectSummary> subjects) {}

    /** Lightweight subject info for embedding in program responses. */
    public record SubjectSummary(int id, String code, String name, String yearLevel) {}

    /** Request body for adding a member to a program. */
    public record MemberRequest(int userId, String role, String yearLevel) {}

    /** Response body for a program member. */
    public record MemberResponse(int id, int userId, String username, String firstName, String lastName,
                                  String email, String role, String yearLevel,
                                  String enrolledAt, boolean graduated) {}

    /** Summary for listing all members. */
    public record MembersResponse(List<MemberResponse> students, List<MemberResponse> teachers, int totalCount) {}

    /** Response body for a graduated student. */
    public record GraduatedResponse(int userId, String username, String firstName, String lastName,
                                     String email, int programId, String programName,
                                     String yearLevel, boolean diplomaEligible,
                                     String enrolledAt, String programType) {}
}
