package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.AdmissionDto;
import no.example.verdan.dto.ApiResponse;
import no.example.verdan.service.AdmissionService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for the admission system.
 * Handles both student-facing and admin-facing endpoints.
 */
public class AdmissionApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AdmissionApiController.class);
    private final AdmissionService admissionService;

    public AdmissionApiController() {
        this(new AdmissionService());
    }

    public AdmissionApiController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    public void registerRoutes(Javalin app) {
        // ── Portal (all authenticated users) ──
        app.get("/api/admissions/portal", this::getPortal);

        // ── Student endpoints ──
        app.get("/api/admissions/available", this::getAvailable);
        app.post("/api/admissions/apply", this::apply);
        app.get("/api/admissions/my-applications", this::getMyApplications);
        app.put("/api/admissions/applications/{id}/withdraw", this::withdraw);

        // ── Admin endpoints ──
        app.get("/api/admissions/periods", this::getPeriods);
        app.post("/api/admissions/periods", this::createPeriod);
        app.put("/api/admissions/periods/{id}", this::updatePeriod);
        app.put("/api/admissions/periods/{id}/close", this::closePeriod);
        app.put("/api/admissions/periods/{id}/reopen", this::reopenPeriod);
        app.delete("/api/admissions/periods/{id}", this::deletePeriod);
        app.post("/api/admissions/periods/{id}/process", this::processPeriod);
        app.post("/api/admissions/periods/{id}/enroll-accepted", this::enrollAccepted);
        app.get("/api/admissions/periods/{id}/overview", this::getOverview);

        // ── Requirements ──
        app.get("/api/admissions/periods/{id}/requirements", this::getRequirements);
        app.post("/api/admissions/periods/{id}/requirements", this::setRequirement);
        app.post("/api/admissions/periods/{id}/bulk-publish", this::bulkPublish);
    }

    // ── Portal ──

    private void getPortal(Context ctx) {
        String fromLevel = ctx.queryParam("fromLevel");
        ctx.json(ApiResponse.ok(admissionService.getPortalListings(fromLevel)));
    }

    // ── Student ──

    private void getAvailable(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        ctx.json(ApiResponse.ok(admissionService.getAvailablePeriods(userId)));
    }

    private void apply(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        AdmissionDto.ApplicationSubmit submit = ctx.bodyAsClass(AdmissionDto.ApplicationSubmit.class);
        ctx.status(201).json(ApiResponse.ok(admissionService.submitApplications(userId, submit)));
    }

    private void getMyApplications(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        ctx.json(ApiResponse.ok(admissionService.getMyApplications(userId)));
    }

    private void withdraw(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        int appId = Integer.parseInt(ctx.pathParam("id"));
        admissionService.withdrawApplication(appId, userId);
        ctx.json(ApiResponse.ok("Application withdrawn"));
    }

    // ── Admin ──

    private void getPeriods(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(admissionService.getPeriodsForAdmin(institutionId)));
    }

    private void createPeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        AdmissionDto.PeriodRequest req = ctx.bodyAsClass(AdmissionDto.PeriodRequest.class);
        ctx.status(201).json(ApiResponse.ok(admissionService.createPeriod(req, institutionId)));
    }

    private void updatePeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        AdmissionDto.PeriodRequest req = ctx.bodyAsClass(AdmissionDto.PeriodRequest.class);
        ctx.json(ApiResponse.ok(admissionService.updatePeriod(id, req, institutionId)));
    }

    private void closePeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        admissionService.closePeriod(id, institutionId);
        ctx.json(ApiResponse.ok("Period closed"));
    }

    private void processPeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(admissionService.processAdmissions(id, institutionId)));
    }

    private void enrollAccepted(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(admissionService.enrollAcceptedStudents(id, institutionId)));
    }

    private void getOverview(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(admissionService.getAdminOverview(id, institutionId)));
    }

    // ── Requirements ──

    private void getRequirements(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        ctx.json(ApiResponse.ok(admissionService.getRequirements(id, institutionId)));
    }

    private void setRequirement(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        AdmissionDto.RequirementRequest req = ctx.bodyAsClass(AdmissionDto.RequirementRequest.class);
        ctx.status(201).json(ApiResponse.ok(admissionService.setRequirement(id, req, institutionId)));
    }

    private void deletePeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        admissionService.deletePeriod(id, institutionId);
        ctx.json(ApiResponse.ok("Period deleted"));
    }

    private void reopenPeriod(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        admissionService.reopenPeriod(id, institutionId);
        ctx.json(ApiResponse.ok("Period reopened"));
    }

    @SuppressWarnings("unchecked")
    private void bulkPublish(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = safeInstitutionId(ctx);
        var body = ctx.bodyAsClass(java.util.Map.class);
        List<Integer> programIds = ((List<?>) body.get("programIds")).stream()
            .map(o -> ((Number) o).intValue()).toList();
        int count = admissionService.bulkPublishPrograms(id, programIds, institutionId);
        ctx.status(201).json(ApiResponse.ok(count));
    }

    private int safeInstitutionId(Context ctx) {
        Integer id = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        return id != null ? id : -1;
    }
}
