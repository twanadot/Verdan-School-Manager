package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.dto.ApiResponse;
import no.example.verdan.model.Institution;
import no.example.verdan.service.ValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class InstitutionApiController {

    private static final Logger LOG = LoggerFactory.getLogger(InstitutionApiController.class);
    private final InstitutionDao institutionDao;

    public InstitutionApiController() {
        this.institutionDao = new InstitutionDao();
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/institutions", this::getAll);
        app.post("/api/institutions", this::create);
        app.put("/api/institutions/{id}", this::update);
        app.delete("/api/institutions/{id}", this::softDelete);
    }

    /** GET /api/institutions – SUPER_ADMIN sees all, INSTITUTION_ADMIN sees own. */
    private void getAll(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        if (AuthMiddleware.isSuperAdmin(ctx)) {
            ctx.json(ApiResponse.ok(institutionDao.findAllActive()));
        } else {
            // INSTITUTION_ADMIN sees only their own institution
            int institutionId = AuthMiddleware.getInstitutionId(ctx);
            Institution inst = institutionDao.find(institutionId);
            ctx.json(ApiResponse.ok(inst != null ? List.of(inst) : List.of()));
        }
    }

    /** POST /api/institutions – Create a new institution (SUPER_ADMIN only). */
    private void create(Context ctx) {
        AuthMiddleware.requireSuperAdmin(ctx);
        Institution req = ctx.bodyAsClass(Institution.class);
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ValidationException(List.of("Institution name is required"));
        }
        // Check for duplicate name before hitting DB constraint
        if (institutionDao.findByName(req.getName()) != null) {
            throw new no.example.verdan.service.ConflictException("An institution with this name already exists");
        }
        institutionDao.save(req);
        LOG.info("Institution created: {} (ID: {})", req.getName(), req.getId());
        ctx.status(201).json(ApiResponse.ok(req));
    }

    /** PUT /api/institutions/{id} – Update institution (SUPER_ADMIN only). */
    private void update(Context ctx) {
        AuthMiddleware.requireSuperAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        Institution req = ctx.bodyAsClass(Institution.class);
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ValidationException(List.of("Institution name is required"));
        }
        // Check for duplicate name (excluding this institution's own current name)
        Institution existingByName = institutionDao.findByName(req.getName());
        if (existingByName != null && existingByName.getId() != id) {
            throw new no.example.verdan.service.ConflictException("An institution with this name already exists");
        }
        Institution updated = institutionDao.updateInstitution(id, req.getName(), req.getLocation(), req.getLevel(), req.getOwnership());
        LOG.info("Institution updated: {} (ID: {})", updated.getName(), id);
        ctx.json(ApiResponse.ok(updated));
    }

    /** DELETE /api/institutions/{id} – Soft-delete (SUPER_ADMIN only). */
    private void softDelete(Context ctx) {
        AuthMiddleware.requireSuperAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        institutionDao.softDelete(id);
        LOG.info("Institution soft-deleted (ID: {})", id);
        ctx.status(204);
    }
}
