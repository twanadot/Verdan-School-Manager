package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.RoomDto;
import no.example.verdan.service.RoomService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for room management.
 * Handles HTTP concerns only — all business logic is in RoomService.
 */
public class RoomApiController {

    private static final Logger LOG = LoggerFactory.getLogger(RoomApiController.class);
    private final RoomService roomService;

    public RoomApiController() {
        this(new RoomService());
    }

    public RoomApiController(RoomService roomService) {
        this.roomService = roomService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/rooms", this::getAll);
        app.get("/api/rooms/{id}", this::getById);
        app.post("/api/rooms", this::create);
        app.put("/api/rooms/{id}", this::update);
        app.delete("/api/rooms/{id}", this::delete);
    }

    private void getAll(Context ctx) {
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(roomService.getAllRooms(institutionId, isSuperAdmin)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(roomService.getRoomById(id, institutionId, isSuperAdmin)));
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        RoomDto.Request req = ctx.bodyAsClass(RoomDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.status(201).json(ApiResponse.ok(roomService.createRoom(req, institutionId, isSuperAdmin)));
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        RoomDto.Request req = ctx.bodyAsClass(RoomDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(roomService.updateRoom(id, req, institutionId, isSuperAdmin)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdmin(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        roomService.deleteRoom(id, institutionId, isSuperAdmin);
        ctx.status(204);
    }
}
