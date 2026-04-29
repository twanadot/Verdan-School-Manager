package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.AttendanceDto;
import no.example.verdan.service.AttendanceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for attendance management.
 * Handles HTTP concerns only — all business logic is in AttendanceService.
 */
public class AttendanceApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceApiController.class);
    private final AttendanceService attendanceService;

    public AttendanceApiController() {
        this(new AttendanceService());
    }

    public AttendanceApiController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/attendance", this::getAll);
        app.get("/api/attendance/my-absence-stats", this::getMyAbsenceStats);
        app.get("/api/attendance/student/{username}", this::getByStudent);
        app.get("/api/attendance/stats/absence-rate/{username}", this::getAbsenceRate);
        app.get("/api/attendance/absence-stats/{username}", this::getAbsenceStats);
        app.get("/api/attendance/{id}", this::getById);
        app.post("/api/attendance", this::create);
        app.put("/api/attendance/{id}", this::update);
        app.delete("/api/attendance/{id}", this::delete);
    }

    private void getAll(Context ctx) {
        String role = AuthMiddleware.getRole(ctx);
        String username = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(attendanceService.getAttendance(role, username, institutionId, isSuperAdmin)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(attendanceService.getAttendanceById(id, institutionId, isSuperAdmin)));
    }

    private void getByStudent(Context ctx) {
        String username = ctx.pathParam("username");
        String role = AuthMiddleware.getRole(ctx);
        String currentUser = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);

        if ("STUDENT".equalsIgnoreCase(role) && !username.equalsIgnoreCase(currentUser)) {
            ctx.status(403).json(ApiResponse.error("Students can only view their own attendance"));
            return;
        }

        ctx.json(ApiResponse.ok(attendanceService.getAttendanceByStudent(username, institutionId)));
    }

    private void getAbsenceRate(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        String username = ctx.pathParam("username");
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.json(ApiResponse.ok(attendanceService.getAbsenceRate(username, institutionId)));
    }

    /** Per-subject absence stats for a specific student (teacher/admin only). */
    private void getAbsenceStats(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        String username = ctx.pathParam("username");
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.json(ApiResponse.ok(attendanceService.getSubjectAbsenceStats(username, institutionId)));
    }

    /** Per-subject absence stats for the current student (student only). */
    private void getMyAbsenceStats(Context ctx) {
        String username = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.json(ApiResponse.ok(attendanceService.getSubjectAbsenceStats(username, institutionId)));
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        AttendanceDto.Request req = ctx.bodyAsClass(AttendanceDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.status(201).json(ApiResponse.ok(attendanceService.createAttendance(req, institutionId)));
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        AttendanceDto.Request req = ctx.bodyAsClass(AttendanceDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(attendanceService.updateAttendance(id, req, institutionId, isSuperAdmin)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        attendanceService.deleteAttendance(id, institutionId, isSuperAdmin);
        ctx.status(204);
    }
}
