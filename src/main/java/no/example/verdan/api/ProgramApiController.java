package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.ProgramDto;
import no.example.verdan.service.ProgramService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for program (linje/degree/fagskolegrad) management.
 */
public class ProgramApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramApiController.class);
    private final ProgramService programService;

    public ProgramApiController() {
        this(new ProgramService());
    }

    public ProgramApiController(ProgramService programService) {
        this.programService = programService;
    }

    public void registerRoutes(Javalin app) {
        // Program CRUD
        app.get("/api/programs", this::getAll);
        // Static paths MUST be registered BEFORE /api/programs/{id} to avoid matching as an ID
        app.get("/api/programs/graduated", this::getGraduated);
        app.get("/api/programs/my-graduation", this::getMyGraduation);
        app.get("/api/programs/archived", this::getArchived);
        app.post("/api/programs/archive-all", this::bulkArchiveAll);
        // Wildcard {id} routes after all static paths
        app.get("/api/programs/{id}", this::getById);
        app.post("/api/programs", this::create);
        app.put("/api/programs/{id}", this::update);
        app.delete("/api/programs/{id}", this::delete);

        // Subject linking
        app.post("/api/programs/{id}/subjects/{subjectId}", this::addSubject);
        app.delete("/api/programs/{id}/subjects/{subjectId}", this::removeSubject);

        // Member management
        app.get("/api/programs/{id}/members", this::getMembers);
        app.post("/api/programs/{id}/members", this::addMember);
        app.delete("/api/programs/{id}/members/{userId}", this::removeMember);

        // Archive management (these have {id} so order doesn't matter)
        app.post("/api/programs/{id}/archive/{userId}", this::archiveStudent);
        app.post("/api/programs/{id}/restore/{userId}", this::restoreStudent);
    }

    // ── Program CRUD ──

    private void getAll(Context ctx) {
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        String role = ctx.attribute(AuthMiddleware.ATTR_ROLE);
        String username = ctx.attribute(AuthMiddleware.ATTR_USERNAME);
        ctx.json(ApiResponse.ok(programService.getAllPrograms(institutionId, isSuperAdmin, role, username)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(programService.getProgram(id, institutionId, isSuperAdmin)));
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        ProgramDto.Request req = ctx.bodyAsClass(ProgramDto.Request.class);
        Integer institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        int effectiveInstId = (institutionId != null) ? institutionId : (req.institutionId() != null ? req.institutionId() : -1);
        ctx.status(201).json(ApiResponse.ok(programService.createProgram(req, effectiveInstId, isSuperAdmin)));
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        ProgramDto.Request req = ctx.bodyAsClass(ProgramDto.Request.class);
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(programService.updateProgram(id, req, institutionId, isSuperAdmin)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        programService.deleteProgram(id, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    // ── Subject linking ──

    private void addSubject(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = Integer.parseInt(ctx.pathParam("id"));
        int subjectId = Integer.parseInt(ctx.pathParam("subjectId"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        programService.addSubjectToProgram(programId, subjectId, institutionId, isSuperAdmin);
        ctx.status(201).json(ApiResponse.ok("Subject added to program"));
    }

    private void removeSubject(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = Integer.parseInt(ctx.pathParam("id"));
        int subjectId = Integer.parseInt(ctx.pathParam("subjectId"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        programService.removeSubjectFromProgram(programId, subjectId, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    // ── Members ──

    private void getMembers(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(programService.getMembers(id, institutionId, isSuperAdmin)));
    }

    private void addMember(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = Integer.parseInt(ctx.pathParam("id"));
        ProgramDto.MemberRequest req = ctx.bodyAsClass(ProgramDto.MemberRequest.class);
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.status(201).json(ApiResponse.ok(programService.addMember(programId, req, institutionId, isSuperAdmin)));
    }

    private void removeMember(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = Integer.parseInt(ctx.pathParam("id"));
        int userId = Integer.parseInt(ctx.pathParam("userId"));
        int institutionId = safeInstitutionId(ctx);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        programService.removeMember(programId, userId, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    // ── Graduated ──

    private void getGraduated(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(programService.getGraduatedStudents(institutionId)));
    }

    /** Check if the current student is graduated from any program. */
    private void getMyGraduation(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        boolean graduated = programService.isStudentGraduated(userId);
        ctx.json(ApiResponse.ok(java.util.Map.of("graduated", graduated)));
    }

    // ── Archive ──

    private void getArchived(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(programService.getArchivedStudents(institutionId)));
    }

    private void archiveStudent(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = ctx.pathParamAsClass("id", Integer.class).get();
        int userId = ctx.pathParamAsClass("userId", Integer.class).get();
        programService.archiveStudent(programId, userId);
        ctx.json(ApiResponse.ok("Student archived"));
    }

    private void restoreStudent(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int programId = ctx.pathParamAsClass("id", Integer.class).get();
        int userId = ctx.pathParamAsClass("userId", Integer.class).get();
        programService.restoreStudent(programId, userId);
        ctx.json(ApiResponse.ok("Student restored"));
    }

    private void bulkArchiveAll(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        int count = programService.bulkArchiveAll(institutionId);
        ctx.json(ApiResponse.ok(java.util.Map.of("archivedCount", count)));
    }

    private int safeInstitutionId(Context ctx) {
        Integer id = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        return id != null ? id : -1;
    }
}
