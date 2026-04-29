package no.example.verdan.service;

import no.example.verdan.dao.*;
import no.example.verdan.dto.PortalDto;
import no.example.verdan.model.*;
import no.example.verdan.security.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for the student portal: announcements, folders, files, submissions.
 */
public class PortalService {

    private static final Logger LOG = LoggerFactory.getLogger(PortalService.class);
    private static final String PORTAL_DIR = "data/portal-files";

    private final PortalDao portalDao;
    private final UserDao userDao;
    private final ProgramDao programDao;
    private final ProgramMemberDao memberDao;
    private final SubjectDao subjectDao;
    private final InstitutionDao institutionDao;

    public PortalService() {
        this.portalDao = new PortalDao();
        this.userDao = new UserDao();
        this.programDao = new ProgramDao();
        this.memberDao = new ProgramMemberDao();
        this.subjectDao = new SubjectDao();
        this.institutionDao = new InstitutionDao();
    }

    // ═══════════════════════════════════════════════════════════════
    // ANNOUNCEMENTS
    // ═══════════════════════════════════════════════════════════════

    public List<PortalDto.AnnouncementResponse> getAnnouncements(int institutionId, String callerRole, int callerUserId) {
        List<PortalAnnouncement> all = portalDao.findAnnouncementsByInstitution(institutionId);

        // Students only see announcements for their programs/subjects
        if ("STUDENT".equalsIgnoreCase(callerRole)) {
            Set<Integer> myProgramIds = memberDao.findByUser(callerUserId).stream()
                .filter(pm -> !pm.isGraduated() && "STUDENT".equalsIgnoreCase(pm.getRole()))
                .map(pm -> pm.getProgram().getId())
                .collect(Collectors.toSet());

            all = all.stream().filter(a -> {
                if (a.getProgram() != null && myProgramIds.contains(a.getProgram().getId())) return true;
                // If announcement has no program filter, show it if student is in any program of the institution
                return a.getProgram() == null && !myProgramIds.isEmpty();
            }).toList();
        }

        // Teachers only see announcements for their programs/subjects
        if ("TEACHER".equalsIgnoreCase(callerRole)) {
            Set<Integer> myProgramIds = memberDao.findByUser(callerUserId).stream()
                .filter(pm -> "TEACHER".equalsIgnoreCase(pm.getRole()))
                .map(pm -> pm.getProgram().getId())
                .collect(Collectors.toSet());

            // Get teacher's assigned subject codes
            SubjectAssignmentDao saDao = new SubjectAssignmentDao();
            User teacher = userDao.find(callerUserId);
            Set<String> mySubjectCodes = teacher != null
                ? new HashSet<>(saDao.subjectsForTeacher(teacher.getUsername(), institutionId))
                : Set.of();

            all = all.stream().filter(a -> {
                // Announcement tied to a program the teacher is in
                if (a.getProgram() != null) return myProgramIds.contains(a.getProgram().getId());
                // Announcement tied to a subject the teacher teaches
                if (a.getSubjectCode() != null) return mySubjectCodes.contains(a.getSubjectCode());
                // Institution-wide announcement (no program/subject) — visible to all
                return true;
            }).toList();
        }

        return all.stream().map(this::toAnnouncementResponse).toList();
    }

    public PortalDto.AnnouncementResponse createAnnouncement(PortalDto.CreateAnnouncementRequest req,
                                                              int institutionId, int authorId) {
        PortalAnnouncement a = new PortalAnnouncement();
        a.setTitle(InputValidator.sanitize(req.title()));
        a.setContent(InputValidator.sanitize(req.content()));
        a.setAuthor(userDao.find(authorId));
        a.setInstitution(institutionDao.find(institutionId));
        if (req.programId() != null && req.programId() > 0) {
            a.setProgram(programDao.find(req.programId()));
        }
        a.setSubjectCode(req.subjectCode());
        portalDao.saveAnnouncement(a);
        // Re-fetch with joins to avoid lazy loading issues
        PortalAnnouncement saved = portalDao.findAnnouncementFull(a.getId());
        return toAnnouncementResponse(saved != null ? saved : a);
    }

    public PortalDto.AnnouncementResponse updateAnnouncement(int id, PortalDto.CreateAnnouncementRequest req) {
        PortalAnnouncement a = portalDao.findAnnouncement(id);
        if (a == null) throw new IllegalArgumentException("Announcement not found");
        a.setTitle(InputValidator.sanitize(req.title()));
        a.setContent(InputValidator.sanitize(req.content()));
        if (req.programId() != null && req.programId() > 0) {
            a.setProgram(programDao.find(req.programId()));
        } else {
            a.setProgram(null);
        }
        a.setSubjectCode(req.subjectCode());
        portalDao.updateAnnouncement(a);
        // Re-fetch with joins to avoid lazy loading issues
        PortalAnnouncement updated = portalDao.findAnnouncementFull(id);
        return toAnnouncementResponse(updated != null ? updated : a);
    }

    public void deleteAnnouncement(int id) {
        PortalAnnouncement a = portalDao.findAnnouncement(id);
        if (a != null) portalDao.deleteAnnouncement(a);
    }

    public void togglePin(int id) {
        PortalAnnouncement a = portalDao.findAnnouncement(id);
        if (a == null) throw new IllegalArgumentException("Announcement not found");
        a.setPinned(!a.isPinned());
        portalDao.updateAnnouncement(a);
    }

    // ── Comments ──

    public List<PortalDto.CommentResponse> getComments(int announcementId) {
        return portalDao.findCommentsByAnnouncement(announcementId).stream()
            .map(this::toCommentResponse).toList();
    }

    public PortalDto.CommentResponse addComment(int announcementId, String content, int authorId) {
        PortalAnnouncement a = portalDao.findAnnouncement(announcementId);
        if (a == null) throw new IllegalArgumentException("Announcement not found");
        PortalComment c = new PortalComment();
        c.setAnnouncement(a);
        c.setAuthor(userDao.find(authorId));
        c.setContent(InputValidator.sanitize(content));
        portalDao.saveComment(c);
        return toCommentResponse(c);
    }

    public void deleteComment(int id) {
        PortalComment c = portalDao.findComment(id);
        if (c != null) portalDao.deleteComment(c);
    }

    // ═══════════════════════════════════════════════════════════════
    // FOLDERS
    // ═══════════════════════════════════════════════════════════════

    public List<PortalDto.FolderResponse> getFolders(int institutionId, String callerRole, int callerUserId) {
        List<PortalFolder> all = portalDao.findFoldersByInstitution(institutionId);

        if ("STUDENT".equalsIgnoreCase(callerRole)) {
            Set<Integer> myProgramIds = memberDao.findByUser(callerUserId).stream()
                .filter(pm -> !pm.isGraduated() && "STUDENT".equalsIgnoreCase(pm.getRole()))
                .map(pm -> pm.getProgram().getId())
                .collect(Collectors.toSet());

            all = all.stream().filter(f ->
                f.getProgram() != null && myProgramIds.contains(f.getProgram().getId())
            ).toList();
        } else if ("TEACHER".equalsIgnoreCase(callerRole)) {
            // Teachers see folders in their programs + folders they created
            Set<Integer> myProgramIds = memberDao.findByUser(callerUserId).stream()
                .filter(pm -> "TEACHER".equalsIgnoreCase(pm.getRole()))
                .map(pm -> pm.getProgram().getId())
                .collect(Collectors.toSet());
            all = all.stream().filter(f ->
                f.getCreatedBy().getId() == callerUserId ||
                (f.getProgram() != null && myProgramIds.contains(f.getProgram().getId()))
            ).toList();
        }

        return all.stream().map(this::toFolderResponse).toList();
    }

    public PortalDto.FolderResponse createFolder(PortalDto.CreateFolderRequest req, int institutionId, int userId) {
        PortalFolder f = new PortalFolder();
        f.setName(InputValidator.sanitize(req.name()));
        f.setSubjectCode(InputValidator.sanitize(req.subjectCode()));
        f.setProgram(programDao.find(req.programId()));
        f.setInstitution(institutionDao.find(institutionId));
        f.setCreatedBy(userDao.find(userId));
        f.setAssignment(req.assignment());
        f.setDescription(InputValidator.sanitize(req.description()));
        if (req.deadline() != null && !req.deadline().isBlank()) {
            f.setDeadline(LocalDateTime.parse(req.deadline()));
        }
        portalDao.saveFolder(f);
        PortalFolder saved = portalDao.findFolderFull(f.getId());
        return toFolderResponse(saved != null ? saved : f);
    }

    public PortalDto.FolderResponse updateFolder(int id, PortalDto.UpdateFolderRequest req) {
        PortalFolder f = portalDao.findFolder(id);
        if (f == null) throw new IllegalArgumentException("Folder not found");
        f.setName(InputValidator.sanitize(req.name()));
        f.setDescription(InputValidator.sanitize(req.description()));
        f.setAssignment(req.assignment());
        if (req.deadline() != null && !req.deadline().isBlank()) {
            f.setDeadline(LocalDateTime.parse(req.deadline()));
        } else {
            f.setDeadline(null);
        }
        portalDao.updateFolder(f);
        PortalFolder updated = portalDao.findFolderFull(id);
        return toFolderResponse(updated != null ? updated : f);
    }

    public void deleteFolder(int id) {
        PortalFolder f = portalDao.findFolder(id);
        if (f != null) portalDao.deleteFolder(f);
    }

    // ═══════════════════════════════════════════════════════════════
    // FILES
    // ═══════════════════════════════════════════════════════════════

    public List<PortalDto.FileResponse> getFiles(int folderId) {
        return portalDao.findFilesByFolder(folderId).stream().map(this::toFileResponse).toList();
    }

    public PortalDto.FileResponse uploadFile(int folderId, InputStream is, String fileName, String mimeType, long size, int userId) throws IOException {
        PortalFolder folder = portalDao.findFolder(folderId);
        if (folder == null) throw new IllegalArgumentException("Folder not found");

        Path dir = Paths.get(PORTAL_DIR, String.valueOf(folderId));
        Files.createDirectories(dir);
        String safeName = System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = dir.resolve(safeName);
        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);

        PortalFile pf = new PortalFile();
        pf.setFolder(folder);
        pf.setFileName(fileName);
        pf.setStoredPath(target.toString());
        pf.setMimeType(mimeType);
        pf.setFileSize(size);
        pf.setUploadedBy(userDao.find(userId));
        portalDao.saveFile(pf);
        return toFileResponse(pf);
    }

    public void deleteFile(int id) throws IOException {
        PortalFile f = portalDao.findFile(id);
        if (f != null) {
            Path p = Paths.get(f.getStoredPath());
            if (java.nio.file.Files.exists(p)) java.nio.file.Files.delete(p);
            portalDao.deleteFile(f);
        }
    }

    public PortalFile getFileEntity(int id) { return portalDao.findFile(id); }

    // ═══════════════════════════════════════════════════════════════
    // SUBMISSIONS
    // ═══════════════════════════════════════════════════════════════

    public List<PortalDto.SubmissionResponse> getSubmissions(int folderId) {
        return portalDao.findSubmissionsByFolder(folderId).stream().map(this::toSubmissionResponse).toList();
    }

    public List<PortalDto.SubmissionResponse> getMySubmissions(int studentId, int institutionId) {
        return portalDao.findSubmissionsByStudent(studentId, institutionId).stream().map(this::toSubmissionResponse).toList();
    }

    public PortalDto.SubmissionResponse submitAssignment(int folderId, InputStream is, String fileName, String mimeType, long size, int studentId) throws IOException {
        PortalFolder folder = portalDao.findFolder(folderId);
        if (folder == null || !folder.isAssignment()) throw new IllegalArgumentException("Not an assignment folder");

        Path dir = Paths.get(PORTAL_DIR, "submissions", String.valueOf(folderId));
        Files.createDirectories(dir);
        String safeName = studentId + "_" + System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = dir.resolve(safeName);
        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);

        PortalSubmission s = new PortalSubmission();
        s.setFolder(folder);
        s.setStudent(userDao.find(studentId));
        s.setFileName(fileName);
        s.setStoredPath(target.toString());
        s.setMimeType(mimeType);
        s.setFileSize(size);
        s.setStatus(SubmissionStatus.PENDING);
        portalDao.saveSubmission(s);
        return toSubmissionResponse(s);
    }

    public PortalDto.SubmissionResponse reviewSubmission(int id, String status, String feedback) {
        PortalSubmission s = portalDao.findSubmission(id);
        if (s == null) throw new IllegalArgumentException("Submission not found");
        s.setStatus(SubmissionStatus.valueOf(status));
        s.setFeedback(InputValidator.sanitize(feedback));
        s.setReviewedAt(LocalDateTime.now());
        portalDao.updateSubmission(s);
        return toSubmissionResponse(s);
    }

    public PortalSubmission getSubmissionEntity(int id) { return portalDao.findSubmission(id); }

    // ═══════════════════════════════════════════════════════════════
    // MAPPERS
    // ═══════════════════════════════════════════════════════════════

    private PortalDto.AnnouncementResponse toAnnouncementResponse(PortalAnnouncement a) {
        String subjectName = null;
        if (a.getSubjectCode() != null) {
            Subject s = subjectDao.findByCode(a.getSubjectCode());
            if (s != null) subjectName = s.getName();
        }
        int commentCount = portalDao.findCommentsByAnnouncement(a.getId()).size();
        return new PortalDto.AnnouncementResponse(
            a.getId(), a.getTitle(), a.getContent(),
            a.getAuthor().getId(),
            a.getAuthor().getFirstName() + " " + a.getAuthor().getLastName(),
            a.getProgram() != null ? a.getProgram().getId() : null,
            a.getProgram() != null ? a.getProgram().getName() : null,
            a.getSubjectCode(), subjectName,
            a.isPinned(), a.getCreatedAt(), a.getUpdatedAt(),
            commentCount
        );
    }

    private PortalDto.CommentResponse toCommentResponse(PortalComment c) {
        return new PortalDto.CommentResponse(
            c.getId(), c.getAnnouncement().getId(),
            c.getAuthor().getId(),
            c.getAuthor().getFirstName() + " " + c.getAuthor().getLastName(),
            c.getAuthor().getRole(),
            c.getContent(), c.getCreatedAt()
        );
    }

    private PortalDto.FolderResponse toFolderResponse(PortalFolder f) {
        String subjectName = null;
        if (f.getSubjectCode() != null) {
            Subject s = subjectDao.findByCode(f.getSubjectCode());
            if (s != null) subjectName = s.getName();
        }
        int fileCount = portalDao.findFilesByFolder(f.getId()).size();
        int submissionCount = f.isAssignment() ? portalDao.findSubmissionsByFolder(f.getId()).size() : 0;
        return new PortalDto.FolderResponse(
            f.getId(), f.getName(), f.getSubjectCode(), subjectName,
            f.getProgram() != null ? f.getProgram().getId() : null,
            f.getProgram() != null ? f.getProgram().getName() : null,
            f.getCreatedBy().getId(),
            f.getCreatedBy().getFirstName() + " " + f.getCreatedBy().getLastName(),
            f.isAssignment(), f.getDescription(),
            f.getDeadline(),
            f.getCreatedAt(), f.getSortOrder(),
            fileCount, submissionCount
        );
    }

    private PortalDto.FileResponse toFileResponse(PortalFile f) {
        int uploadedById = f.getUploadedBy().getId();
        String uploaderName;
        try {
            uploaderName = f.getUploadedBy().getFirstName() + " " + f.getUploadedBy().getLastName();
        } catch (Exception e) {
            User u = userDao.find(uploadedById);
            uploaderName = u != null ? u.getFirstName() + " " + u.getLastName() : "Ukjent";
        }
        int folderId;
        try { folderId = f.getFolder().getId(); } catch (Exception e) { folderId = 0; }
        return new PortalDto.FileResponse(
            f.getId(), folderId, f.getFileName(), f.getMimeType(),
            f.getFileSize(), uploadedById, uploaderName,
            f.getUploadedAt()
        );
    }

    private PortalDto.SubmissionResponse toSubmissionResponse(PortalSubmission s) {
        int studentId = s.getStudent().getId();
        String studentName, studentUsername;
        try {
            studentName = s.getStudent().getFirstName() + " " + s.getStudent().getLastName();
            studentUsername = s.getStudent().getUsername();
        } catch (Exception e) {
            User u = userDao.find(studentId);
            studentName = u != null ? u.getFirstName() + " " + u.getLastName() : "Ukjent";
            studentUsername = u != null ? u.getUsername() : "";
        }
        int folderId; String folderName;
        try { folderId = s.getFolder().getId(); folderName = s.getFolder().getName(); }
        catch (Exception e) { folderId = 0; folderName = ""; }
        return new PortalDto.SubmissionResponse(
            s.getId(), folderId, folderName,
            studentId, studentName, studentUsername,
            s.getFileName(), s.getMimeType(), s.getFileSize(),
            s.getStatus().name(), s.getFeedback(),
            s.getSubmittedAt(), s.getReviewedAt()
        );
    }
}
