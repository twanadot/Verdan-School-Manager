package no.example.verdan.dto;

import java.time.LocalDate;

/**
 * DTOs for attendance-related API operations.
 */
public final class AttendanceDto {
    private AttendanceDto() {}

    /** Request body for creating/updating attendance. */
    public record Request(String studentUsername, LocalDate date,
            String status, String subjectCode, String note, Integer institutionId,
            Boolean excused) {}

    /** Response body for attendance data. */
    public record Response(Integer id, String studentUsername, String studentName,
            LocalDate date, String status, String subjectCode, String note,
            Integer institutionId, String institutionName, boolean excused) {}

    /** Per-subject absence statistics for a student. */
    public record SubjectAbsenceStats(
        String subjectCode, String subjectName,
        int totalSessions, int attended, int absentUnexcused, int absentExcused,
        double absencePercent,
        Integer maxAbsencePercent,
        boolean overLimit,
        String status,
        String institutionLevel,
        String institutionName,
        String yearLevel
    ) {}
}
