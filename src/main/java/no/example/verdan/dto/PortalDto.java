package no.example.verdan.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for the student portal: announcements, comments, folders, files, submissions.
 */
public class PortalDto {

    // ── Announcements ──

    public record AnnouncementResponse(
        int id, String title, String content,
        int authorId, String authorName,
        Integer programId, String programName,
        String subjectCode, String subjectName,
        boolean pinned, LocalDateTime createdAt, LocalDateTime updatedAt,
        int commentCount
    ) {}

    public record CreateAnnouncementRequest(
        String title, String content,
        Integer programId, String subjectCode
    ) {}

    public record CommentResponse(
        int id, int announcementId,
        int authorId, String authorName, String authorRole,
        String content, LocalDateTime createdAt
    ) {}

    public record CreateCommentRequest(String content) {}

    // ── Folders ──

    public record FolderResponse(
        int id, String name, String subjectCode, String subjectName,
        Integer programId, String programName,
        int createdById, String createdByName,
        boolean assignment, String description,
        LocalDateTime deadline,
        LocalDateTime createdAt, int sortOrder,
        int fileCount, int submissionCount
    ) {}

    public record CreateFolderRequest(
        String name, String subjectCode, int programId,
        boolean assignment, String description,
        String deadline
    ) {}

    public record UpdateFolderRequest(
        String name, String description, boolean assignment,
        String deadline
    ) {}

    // ── Files ──

    public record FileResponse(
        int id, int folderId, String fileName, String mimeType,
        long fileSize, int uploadedById, String uploadedByName,
        LocalDateTime uploadedAt
    ) {}

    // ── Submissions ──

    public record SubmissionResponse(
        int id, int folderId, String folderName,
        int studentId, String studentName, String studentUsername,
        String fileName, String mimeType, long fileSize,
        String status, String feedback,
        LocalDateTime submittedAt, LocalDateTime reviewedAt
    ) {}

    public record ReviewRequest(String status, String feedback) {}
}
