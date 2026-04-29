package no.example.verdan.api;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.PortalDto;
import no.example.verdan.model.PortalFile;
import no.example.verdan.model.PortalSubmission;
import no.example.verdan.security.JwtUtil;
import no.example.verdan.service.PortalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * REST API controller for the student portal.
 * Handles announcements, comments, folders, files, and submissions.
 */
public class PortalApiController {

    private static final Logger LOG = LoggerFactory.getLogger(PortalApiController.class);
    private final PortalService portalService = new PortalService();

    public void registerRoutes(Javalin app) {
        // ── Announcements ──
        app.get("/api/portal/announcements", this::getAnnouncements);
        app.post("/api/portal/announcements", this::createAnnouncement);
        app.put("/api/portal/announcements/{id}", this::updateAnnouncement);
        app.delete("/api/portal/announcements/{id}", this::deleteAnnouncement);
        app.put("/api/portal/announcements/{id}/pin", this::togglePin);

        // ── Comments ──
        app.get("/api/portal/announcements/{id}/comments", this::getComments);
        app.post("/api/portal/announcements/{id}/comments", this::addComment);
        app.delete("/api/portal/comments/{id}", this::deleteComment);

        // ── Folders ──
        app.get("/api/portal/folders", this::getFolders);
        app.post("/api/portal/folders", this::createFolder);
        app.put("/api/portal/folders/{id}", this::updateFolder);
        app.delete("/api/portal/folders/{id}", this::deleteFolder);

        // ── Files ──
        app.get("/api/portal/folders/{id}/files", this::getFiles);
        app.post("/api/portal/folders/{id}/files", this::uploadFile);
        app.delete("/api/portal/files/{id}", this::deleteFile);
        app.get("/api/portal/files/{id}/download", this::downloadFile);

        // ── Submissions ──
        app.get("/api/portal/folders/{id}/submissions", this::getSubmissions);
        app.post("/api/portal/folders/{id}/submissions", this::submitAssignment);
        app.put("/api/portal/submissions/{id}/review", this::reviewSubmission);
        app.get("/api/portal/submissions/{id}/download", this::downloadSubmission);
        app.get("/api/portal/my-submissions", this::getMySubmissions);
    }

    // ═══════════════════════════════════════════════════════════════
    // ANNOUNCEMENTS
    // ═══════════════════════════════════════════════════════════════

    private void getAnnouncements(Context ctx) {
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        String role = AuthMiddleware.getRole(ctx);
        int userId = AuthMiddleware.getUserId(ctx);
        ctx.json(ApiResponse.ok(portalService.getAnnouncements(institutionId, role, userId)));
    }

    private void createAnnouncement(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        int userId = AuthMiddleware.getUserId(ctx);
        var req = ctx.bodyAsClass(PortalDto.CreateAnnouncementRequest.class);
        ctx.status(201).json(ApiResponse.ok(portalService.createAnnouncement(req, institutionId, userId)));
    }

    private void updateAnnouncement(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        var req = ctx.bodyAsClass(PortalDto.CreateAnnouncementRequest.class);
        ctx.json(ApiResponse.ok(portalService.updateAnnouncement(id, req)));
    }

    private void deleteAnnouncement(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        portalService.deleteAnnouncement(id);
        ctx.json(ApiResponse.ok("Deleted"));
    }

    private void togglePin(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        portalService.togglePin(id);
        ctx.json(ApiResponse.ok("Toggled"));
    }

    // ── Comments ──

    private void getComments(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        ctx.json(ApiResponse.ok(portalService.getComments(id)));
    }

    private void addComment(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        var req = ctx.bodyAsClass(PortalDto.CreateCommentRequest.class);
        ctx.status(201).json(ApiResponse.ok(portalService.addComment(id, req.content(), userId)));
    }

    private void deleteComment(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        portalService.deleteComment(id);
        ctx.json(ApiResponse.ok("Deleted"));
    }

    // ═══════════════════════════════════════════════════════════════
    // FOLDERS
    // ═══════════════════════════════════════════════════════════════

    private void getFolders(Context ctx) {
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        String role = AuthMiddleware.getRole(ctx);
        int userId = AuthMiddleware.getUserId(ctx);
        ctx.json(ApiResponse.ok(portalService.getFolders(institutionId, role, userId)));
    }

    private void createFolder(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        int userId = AuthMiddleware.getUserId(ctx);
        var req = ctx.bodyAsClass(PortalDto.CreateFolderRequest.class);
        ctx.status(201).json(ApiResponse.ok(portalService.createFolder(req, institutionId, userId)));
    }

    private void updateFolder(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        var req = ctx.bodyAsClass(PortalDto.UpdateFolderRequest.class);
        ctx.json(ApiResponse.ok(portalService.updateFolder(id, req)));
    }

    private void deleteFolder(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        portalService.deleteFolder(id);
        ctx.json(ApiResponse.ok("Deleted"));
    }

    // ═══════════════════════════════════════════════════════════════
    // FILES
    // ═══════════════════════════════════════════════════════════════

    private void getFiles(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        ctx.json(ApiResponse.ok(portalService.getFiles(id)));
    }

    private void uploadFile(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int folderId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) { ctx.status(400).json(ApiResponse.error("No file provided")); return; }
        try (InputStream is = file.content()) {
            var resp = portalService.uploadFile(folderId, is, file.filename(), file.contentType(), file.size(), userId);
            ctx.status(201).json(ApiResponse.ok(resp));
        } catch (Exception e) {
            LOG.error("Portal file upload failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Filopplasting feilet. Prøv igjen."));
        }
    }

    private void deleteFile(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        try {
            portalService.deleteFile(id);
            ctx.json(ApiResponse.ok("Deleted"));
        } catch (Exception e) {
            LOG.error("Portal file delete failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Sletting feilet. Prøv igjen."));
        }
    }

    private void downloadFile(Context ctx) {
        // Auth via query param or header (same as chat files)
        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) token = authHeader.substring(7);
        }
        if (token == null || token.isBlank()) { ctx.status(401).json(ApiResponse.error("Auth required")); return; }
        DecodedJWT jwt = JwtUtil.verifyToken(token);
        if (jwt == null || JwtUtil.isRefreshToken(jwt)) { ctx.status(401).json(ApiResponse.error("Invalid token")); return; }

        int id = Integer.parseInt(ctx.pathParam("id"));
        PortalFile pf = portalService.getFileEntity(id);
        if (pf == null) { ctx.status(404).json(ApiResponse.error("File not found")); return; }

        try {
            InputStream stream = new java.io.FileInputStream(pf.getStoredPath());
            ctx.contentType(pf.getMimeType() != null ? pf.getMimeType() : "application/octet-stream");
            ctx.header("Content-Disposition", "inline; filename=\"" + pf.getFileName() + "\"");
            ctx.result(stream);
        } catch (Exception e) {
            ctx.status(404).json(ApiResponse.error("File not found on disk"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SUBMISSIONS
    // ═══════════════════════════════════════════════════════════════

    private void getSubmissions(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int folderId = Integer.parseInt(ctx.pathParam("id"));
        ctx.json(ApiResponse.ok(portalService.getSubmissions(folderId)));
    }

    private void submitAssignment(Context ctx) {
        int folderId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) { ctx.status(400).json(ApiResponse.error("No file provided")); return; }
        try (InputStream is = file.content()) {
            var resp = portalService.submitAssignment(folderId, is, file.filename(), file.contentType(), file.size(), userId);
            ctx.status(201).json(ApiResponse.ok(resp));
        } catch (Exception e) {
            LOG.error("Assignment submission failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Innlevering feilet. Prøv igjen."));
        }
    }

    private void reviewSubmission(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        var req = ctx.bodyAsClass(PortalDto.ReviewRequest.class);
        ctx.json(ApiResponse.ok(portalService.reviewSubmission(id, req.status(), req.feedback())));
    }

    private void getMySubmissions(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        ctx.json(ApiResponse.ok(portalService.getMySubmissions(userId, institutionId)));
    }

    private void downloadSubmission(Context ctx) {
        // Auth via query param or header (same as file downloads)
        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) token = authHeader.substring(7);
        }
        if (token == null || token.isBlank()) { ctx.status(401).json(ApiResponse.error("Auth required")); return; }
        DecodedJWT jwt = JwtUtil.verifyToken(token);
        if (jwt == null || JwtUtil.isRefreshToken(jwt)) { ctx.status(401).json(ApiResponse.error("Invalid token")); return; }

        int id = Integer.parseInt(ctx.pathParam("id"));
        PortalSubmission ps = portalService.getSubmissionEntity(id);
        if (ps == null) { ctx.status(404).json(ApiResponse.error("Submission not found")); return; }

        try {
            InputStream stream = new java.io.FileInputStream(ps.getStoredPath());
            ctx.contentType(ps.getMimeType() != null ? ps.getMimeType() : "application/octet-stream");
            ctx.header("Content-Disposition", "inline; filename=\"" + ps.getFileName() + "\"");
            ctx.result(stream);
        } catch (Exception e) {
            ctx.status(404).json(ApiResponse.error("File not found on disk"));
        }
    }
}
