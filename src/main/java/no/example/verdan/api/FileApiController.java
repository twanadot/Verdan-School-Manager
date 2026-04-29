package no.example.verdan.api;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.ChatDto;
import no.example.verdan.model.ChatAttachment;
import no.example.verdan.security.JwtUtil;
import no.example.verdan.service.FileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * REST API controller for file upload/download.
 * Upload requires Authorization header.
 * Download supports token as query parameter for direct browser links.
 */
public class FileApiController {

    private static final Logger LOG = LoggerFactory.getLogger(FileApiController.class);
    private final FileService fileService = new FileService();

    public void registerRoutes(Javalin app) {
        app.post("/api/files/upload", this::upload);
        app.get("/api/files/{id}", this::download);
    }

    /** POST /api/files/upload — Multipart file upload (requires auth header). */
    private void upload(Context ctx) {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            ctx.status(400).json(ApiResponse.error("No file provided"));
            return;
        }

        try (InputStream is = file.content()) {
            ChatAttachment att = fileService.upload(
                is, file.filename(), file.contentType(), file.size());

            ctx.status(201).json(ApiResponse.ok(new ChatDto.UploadResponse(
                att.getId(), att.getFileName(), att.getFileSize(), att.getMimeType()
            )));
        } catch (Exception e) {
            LOG.error("File upload failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Filopplasting feilet. Prøv igjen."));
        }
    }

    /**
     * GET /api/files/{id} — Download/view a file.
     * Supports token via query parameter (?token=xxx) for direct browser links
     * and via Authorization header for API calls.
     */
    private void download(Context ctx) {
        // Validate JWT from query param or header
        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null || token.isBlank()) {
            ctx.status(401).json(ApiResponse.error("Authentication required"));
            return;
        }

        DecodedJWT jwt = JwtUtil.verifyToken(token);
        if (jwt == null || JwtUtil.isRefreshToken(jwt)) {
            ctx.status(401).json(ApiResponse.error("Invalid token"));
            return;
        }

        int id = Integer.parseInt(ctx.pathParam("id"));
        ChatAttachment att = fileService.getAttachment(id);
        if (att == null) {
            ctx.status(404).json(ApiResponse.error("File not found"));
            return;
        }

        try {
            InputStream stream = fileService.getFileStream(att.getStoredPath());
            ctx.contentType(att.getMimeType());
            ctx.header("Content-Disposition", "inline; filename=\"" + att.getFileName() + "\"");
            ctx.header("Cache-Control", "private, max-age=3600");
            ctx.result(stream);
        } catch (Exception e) {
            ctx.status(404).json(ApiResponse.error("File not found"));
        }
    }
}
