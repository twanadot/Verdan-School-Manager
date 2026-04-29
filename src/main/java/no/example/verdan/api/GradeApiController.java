package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.GradeDto;
import no.example.verdan.service.GradeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for grade management.
 * Handles HTTP concerns only — all business logic is in GradeService.
 */
public class GradeApiController {

    private static final Logger LOG = LoggerFactory.getLogger(GradeApiController.class);
    private final GradeService gradeService;

    public GradeApiController() {
        this(new GradeService());
    }

    public GradeApiController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/grades", this::getAll);
        app.get("/api/grades/history", this::getEducationHistory);
        app.get("/api/grades/{id}", this::getById);
        app.get("/api/grades/student/{username}", this::getByStudent);
        app.get("/api/grades/stats/averages", this::getAverages);
        app.post("/api/grades", this::create);
        app.put("/api/grades/{id}", this::update);
        app.delete("/api/grades/{id}", this::delete);
    }

    private void getAll(Context ctx) {
        String role = AuthMiddleware.getRole(ctx);
        String username = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(gradeService.getGrades(role, username, institutionId, isSuperAdmin)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(gradeService.getGradeById(id, institutionId, isSuperAdmin)));
    }

    private void getByStudent(Context ctx) {
        String username = ctx.pathParam("username");
        String role = AuthMiddleware.getRole(ctx);
        String currentUser = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);

        if ("STUDENT".equalsIgnoreCase(role) && !username.equalsIgnoreCase(currentUser)) {
            ctx.status(403).json(ApiResponse.error("Students can only view their own grades"));
            return;
        }

        ctx.json(ApiResponse.ok(gradeService.getGradesByStudent(username, institutionId)));
    }

    private void getAverages(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.json(ApiResponse.ok(gradeService.getAverages(institutionId)));
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        GradeDto.Request req = ctx.bodyAsClass(GradeDto.Request.class);
        String creatorUsername = AuthMiddleware.getUsername(ctx);
        String role = AuthMiddleware.getRole(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.status(201).json(ApiResponse.ok(gradeService.createGrade(req, creatorUsername, role, institutionId)));
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        GradeDto.Request req = ctx.bodyAsClass(GradeDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(gradeService.updateGrade(id, req, institutionId, isSuperAdmin)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        gradeService.deleteGrade(id, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    /** Returns all grades for the current student grouped by institution level. */
    private void getEducationHistory(Context ctx) {
        String username = AuthMiddleware.getUsername(ctx);
        ctx.json(ApiResponse.ok(gradeService.getEducationHistory(username)));
    }
}
