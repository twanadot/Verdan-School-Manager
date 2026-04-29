package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.SubjectDto;
import no.example.verdan.service.SubjectService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for subject (course) management.
 * Handles HTTP concerns only — all business logic is in SubjectService.
 */
public class SubjectApiController {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectApiController.class);
    private final SubjectService subjectService;

    public SubjectApiController() {
        this(new SubjectService());
    }

    public SubjectApiController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/subjects", this::getAll);
        app.get("/api/subjects/search", this::search);
        app.get("/api/subjects/{code}/members", this::getMembers);
        app.post("/api/subjects/{code}/members", this::assignMember);
        app.delete("/api/subjects/{code}/members/{username}", this::removeMember);
        app.get("/api/subjects/{id}", this::getById);
        app.post("/api/subjects", this::create);
        app.put("/api/subjects/{id}", this::update);
        app.delete("/api/subjects/{id}", this::delete);
    }

    private void getAll(Context ctx) {
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        String role = ctx.attribute(AuthMiddleware.ATTR_ROLE);
        String username = ctx.attribute(AuthMiddleware.ATTR_USERNAME);
        ctx.json(ApiResponse.ok(subjectService.getAllSubjects(institutionId, isSuperAdmin, role, username)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(subjectService.getSubjectById(id, institutionId, isSuperAdmin)));
    }

    private void search(Context ctx) {
        String query = ctx.queryParam("q");
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(subjectService.searchSubjects(query, institutionId)));
    }

    private void getMembers(Context ctx) {
        String code = ctx.pathParam("code");
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(subjectService.getMembersForSubject(code, institutionId)));
    }

    private void assignMember(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        String code = ctx.pathParam("code");
        SubjectDto.AssignRequest req = ctx.bodyAsClass(SubjectDto.AssignRequest.class);
        int institutionId = safeInstitutionId(ctx);
        subjectService.assignUserToSubject(code, req.username(), req.role(), institutionId);
        ctx.status(201).json(ApiResponse.ok("Member assigned"));
    }

    private void removeMember(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        String code = ctx.pathParam("code");
        String username = ctx.pathParam("username");
        int institutionId = safeInstitutionId(ctx);
        subjectService.removeUserFromSubject(code, username, institutionId);
        ctx.status(204);
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        SubjectDto.Request req = ctx.bodyAsClass(SubjectDto.Request.class);
        Integer institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        // SUPER_ADMIN has no institutionId in JWT — use the one from request body
        int effectiveInstId = (institutionId != null) ? institutionId : (req.institutionId() != null ? req.institutionId() : -1);
        ctx.status(201).json(ApiResponse.ok(subjectService.createSubject(req, effectiveInstId, isSuperAdmin)));
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        SubjectDto.Request req = ctx.bodyAsClass(SubjectDto.Request.class);
        Integer institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        int effectiveInstId = (institutionId != null) ? institutionId : (req.institutionId() != null ? req.institutionId() : -1);
        ctx.json(ApiResponse.ok(subjectService.updateSubject(id, req, effectiveInstId, isSuperAdmin)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        subjectService.deleteSubject(id, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    /** Safely extract institutionId — returns -1 for SUPER_ADMIN (who has no institution). */
    private int safeInstitutionId(Context ctx) {
        Integer id = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        return id != null ? id : -1;
    }
}
