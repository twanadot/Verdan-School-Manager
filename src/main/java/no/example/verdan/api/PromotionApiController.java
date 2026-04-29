package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.dto.ApiResponse;
import no.example.verdan.model.Institution;
import no.example.verdan.service.PromotionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for student year-level promotion.
 * Only INSTITUTION_ADMIN or SUPER_ADMIN can trigger promotions.
 */
public class PromotionApiController {

    private static final Logger LOG = LoggerFactory.getLogger(PromotionApiController.class);
    private final PromotionService promotionService;
    private final InstitutionDao institutionDao;

    public PromotionApiController() {
        this(new PromotionService(), new InstitutionDao());
    }

    public PromotionApiController(PromotionService promotionService, InstitutionDao institutionDao) {
        this.promotionService = promotionService;
        this.institutionDao = institutionDao;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/promotion/preview", this::preview);
        app.post("/api/promotion/advance", this::advance);
        app.post("/api/promotion/undo", this::undo);
        app.post("/api/promotion/transfer", this::transfer);
    }

    /**
     * Preview promotion: shows what will happen before executing.
     */
    private void preview(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        String instLevel = resolveLevel(institutionId);

        PromotionService.PromotionPreview result = promotionService.preview(institutionId, instLevel);
        ctx.json(ApiResponse.ok(result));
    }

    /**
     * Execute promotion: advances all students to next year level.
     */
    private void advance(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        String instLevel = resolveLevel(institutionId);

        PromotionService.PromotionResult result = promotionService.execute(institutionId, instLevel);
        LOG.info("Promotion executed: {} promoted, {} graduated in institution {}",
            result.promoted(), result.graduated(), institutionId);
        ctx.json(ApiResponse.ok(result));
    }

    /**
     * Undo promotion: reverses the last promotion for all students.
     */
    private void undo(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        String instLevel = resolveLevel(institutionId);

        PromotionService.PromotionResult result = promotionService.undoPromotion(institutionId, instLevel);
        LOG.info("Promotion undone: {} demoted, {} un-graduated in institution {}",
            result.promoted(), result.graduated(), institutionId);
        ctx.json(ApiResponse.ok(result));
    }

    /**
     * Transfer a student from one program to another (class transfer, e.g. 8A → 8B).
     */
    private void transfer(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int institutionId = safeInstitutionId(ctx);
        PromotionService.TransferRequest req = ctx.bodyAsClass(PromotionService.TransferRequest.class);
        promotionService.transferStudent(req.userId(), req.fromProgramId(), req.toProgramId(), institutionId);
        LOG.info("Student {} transferred from program {} to {} in institution {}",
            req.userId(), req.fromProgramId(), req.toProgramId(), institutionId);
        ctx.json(ApiResponse.ok("Transfer complete"));
    }

    private String resolveLevel(int institutionId) {
        Institution inst = institutionDao.find(institutionId);
        return inst != null && inst.getLevel() != null ? inst.getLevel() : "";
    }

    private int safeInstitutionId(Context ctx) {
        Integer id = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        return id != null ? id : -1;
    }
}
