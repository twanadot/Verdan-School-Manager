package no.example.verdan.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs for grade-related API operations.
 */
public final class GradeDto {
    private GradeDto() {}

    /** Request body for creating/updating a grade. */
    public record Request(String studentUsername, String subject, String value, Integer institutionId, String yearLevel, Boolean retake) {}

    /** Response body for grade data. */
    public record Response(Integer id, String studentUsername, String studentName,
            String subject, String value, LocalDate dateGiven, String teacherUsername,
            Integer institutionId, String institutionName, String yearLevel,
            String originalValue, boolean blockedByAbsence, boolean retake) {}

    /** A group of grades from a specific education level. */
    public record EducationLevel(String level, String levelLabel, String institutionName,
            int institutionId, List<Response> grades, double average, boolean allPassing) {}
}
