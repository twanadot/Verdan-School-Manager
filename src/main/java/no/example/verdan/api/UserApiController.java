package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.UserDto;
import no.example.verdan.service.BatchImportService;
import no.example.verdan.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for user management.
 * Handles HTTP concerns only — all business logic is in UserService.
 */
public class UserApiController {

    private static final Logger LOG = LoggerFactory.getLogger(UserApiController.class);
    private final UserService userService;
    private final BatchImportService batchImportService;

    public UserApiController() {
        this(new UserService(), new BatchImportService());
    }

    public UserApiController(UserService userService, BatchImportService batchImportService) {
        this.userService = userService;
        this.batchImportService = batchImportService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/users", this::getAll);
        app.post("/api/users/import", this::batchImport);
        app.post("/api/users/batch-delete", this::batchDelete);
        app.get("/api/users/{id}", this::getById);
        app.post("/api/users", this::create);
        app.put("/api/users/{id}", this::update);
        app.delete("/api/users/{id}", this::delete);
    }

    /** GET /api/users – List all users (ADMIN or TEACHER). Supports ?role= filter. */
    private void getAll(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        String roleParam = ctx.queryParam("role");
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        String callerRole = AuthMiddleware.getRole(ctx);
        String callerUsername = AuthMiddleware.getUsername(ctx);
        ctx.json(ApiResponse.ok(userService.getAllUsers(roleParam, institutionId, isSuperAdmin, callerRole, callerUsername)));
    }

    /** GET /api/users/{id} – Get user by ID. */
    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        String role = AuthMiddleware.getRole(ctx);
        int currentUserId = AuthMiddleware.getUserId(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);

        // Students can only view their own profile
        if ("STUDENT".equalsIgnoreCase(role) && id != currentUserId) {
            ctx.status(403).json(ApiResponse.error("Students can only view their own profile"));
            return;
        }

        UserDto.Response user = userService.getUserById(id, institutionId, isSuperAdmin);
        if (user == null) {
            ctx.status(404).json(ApiResponse.error("User not found or access denied"));
            return;
        }
        ctx.json(ApiResponse.ok(user));
    }

    /** POST /api/users – Create a new user (ADMIN only). */
    private void create(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        UserDto.CreateRequest req = ctx.bodyAsClass(UserDto.CreateRequest.class);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        String callerRole = AuthMiddleware.getRole(ctx);
        UserDto.Response user = userService.createUser(req, institutionId, isSuperAdmin, callerRole);
        ctx.status(201).json(ApiResponse.ok(user));
    }

    /** POST /api/users/import – Batch import students from CSV/Excel (INSTITUTION_ADMIN only). */
    private void batchImport(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);

        var uploadedFile = ctx.uploadedFile("file");
        if (uploadedFile == null) {
            ctx.status(400).json(ApiResponse.error("Ingen fil lastet opp. Bruk 'file' som felt-navn."));
            return;
        }

        String fileName = uploadedFile.filename();
        try {
            byte[] fileData = uploadedFile.content().readAllBytes();
            BatchImportService.ImportResult result = batchImportService.importStudents(fileData, fileName, institutionId);
            LOG.info("Batch import by {}: {} created, {} skipped from '{}'",
                AuthMiddleware.getUsername(ctx), result.created(), result.skipped(), fileName);
            ctx.json(ApiResponse.ok(result));
        } catch (Exception e) {
            LOG.error("Batch import failed: {}", e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Import feilet. Sjekk at filen er i riktig format (CSV/Excel)."));
        }
    }

    /** PUT /api/users/{id} – Update user (ADMIN only). */
    private void update(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        UserDto.UpdateRequest req = ctx.bodyAsClass(UserDto.UpdateRequest.class);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        String callerRole = AuthMiddleware.getRole(ctx);
        UserDto.Response user = userService.updateUser(id, req, institutionId, isSuperAdmin, callerRole);
        ctx.json(ApiResponse.ok(user));
    }

    /** DELETE /api/users/{id} – Delete user (ADMIN only). */
    private void delete(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int currentUserId = AuthMiddleware.getUserId(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        userService.deleteUser(id, currentUserId, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    /** POST /api/users/batch-delete – Delete multiple users at once (for undo import). */
    private void batchDelete(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        int currentUserId = AuthMiddleware.getUserId(ctx);

        record BatchDeleteRequest(List<Integer> ids) {}
        BatchDeleteRequest req = ctx.bodyAsClass(BatchDeleteRequest.class);
        if (req.ids() == null || req.ids().isEmpty()) {
            ctx.status(400).json(ApiResponse.error("No user IDs provided"));
            return;
        }

        int deleted = 0;
        for (int uid : req.ids()) {
            try {
                userService.deleteUser(uid, currentUserId, institutionId, isSuperAdmin);
                deleted++;
            } catch (Exception e) {
                LOG.warn("Batch delete: failed to delete user {}: {}", uid, e.getMessage());
            }
        }

        LOG.info("Batch delete by {}: {} of {} users deleted",
            AuthMiddleware.getUsername(ctx), deleted, req.ids().size());
        ctx.json(ApiResponse.ok(java.util.Map.of("deleted", deleted)));
    }
}

