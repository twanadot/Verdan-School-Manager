package no.example.verdan.dto;

import java.util.List;

public final class AdmissionDto {
    private AdmissionDto() {}

    // ── Period ──
    public record PeriodRequest(String name, String fromLevel, String toLevel,
                                 String startDate, String endDate, int maxChoices,
                                 Integer institutionId) {}

    public record PeriodResponse(int id, String name, String fromLevel, String toLevel,
                                  String startDate, String endDate, String status,
                                  int maxChoices, int institutionId, String institutionName,
                                  int applicationCount) {}

    // ── Requirements ──
    public record RequirementRequest(int programId, Double minGpa, Integer maxStudents) {}

    public record RequirementResponse(int id, int programId, String programName,
                                       Double minGpa, Integer maxStudents, int applicantCount) {}

    // ── Application (student submits) ──
    public record ApplicationSubmit(int periodId, List<ApplicationChoice> choices) {}
    public record ApplicationChoice(int programId, int priority) {}

    // ── Application (response) ──
    public record ApplicationResponse(int id, int periodId, String periodName,
                                       int programId, String programName,
                                       String institutionName,
                                       int priority, Double gpaSnapshot,
                                       String status, String submittedAt, String processedAt) {}

    // ── Processing result ──
    public record ProcessingResult(int accepted, int waitlisted, int rejected, int total) {}

    // ── Admin overview per program ──
    public record ProgramApplicantSummary(int programId, String programName,
                                           Double minGpa, Integer maxStudents,
                                           int totalApplicants,
                                           List<ApplicantDetail> applicants) {}

    public record ApplicantDetail(int applicationId, int studentId, String username,
                                   String fullName, int priority, Double gpaSnapshot,
                                   String status) {}

    // ── Portal listing (cross-institution) ──
    public record PortalListing(int periodId, String periodName, String fromLevel, String toLevel,
                                 String startDate, String endDate, int maxChoices,
                                 int institutionId, String institutionName, String ownership,
                                 int programId, String programName,
                                 Double minGpa, Integer maxStudents, String prerequisites,
                                 int applicantCount) {}
    // ── Enrollment result (admin enrolls accepted students) ──
    public record EnrollmentResult(int enrolled, int skipped, int total) {}
}
