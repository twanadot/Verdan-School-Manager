package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.BookingDto;
import no.example.verdan.service.BookingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API controller for booking management.
 * Handles HTTP concerns only — all business logic is in BookingService.
 */
public class BookingApiController {

    private static final Logger LOG = LoggerFactory.getLogger(BookingApiController.class);
    private final BookingService bookingService;

    public BookingApiController() {
        this(new BookingService());
    }

    public BookingApiController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/bookings", this::getAll);
        app.get("/api/bookings/{id}", this::getById);
        app.post("/api/bookings", this::create);
        app.put("/api/bookings/{id}", this::update);
        app.put("/api/bookings/{id}/cancel", this::toggleCancel);
        app.delete("/api/bookings/{id}", this::delete);
    }

    private void getAll(Context ctx) {
        String role = AuthMiddleware.getRole(ctx);
        String username = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(bookingService.getBookingsForUser(role, username, institutionId, isSuperAdmin)));
    }

    private void getById(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(bookingService.getBookingById(id, institutionId, isSuperAdmin)));
    }

    private void create(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        BookingDto.Request req = ctx.bodyAsClass(BookingDto.Request.class);
        String createdBy = AuthMiddleware.getUsername(ctx);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        ctx.status(201).json(ApiResponse.ok(bookingService.createBooking(req, createdBy, institutionId)));
    }

    private void delete(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        boolean series = Boolean.parseBoolean(ctx.queryParam("series"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        bookingService.deleteBooking(id, series, institutionId, isSuperAdmin);
        ctx.status(204);
    }

    private void update(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        boolean series = Boolean.parseBoolean(ctx.queryParam("series"));
        BookingDto.Request req = ctx.bodyAsClass(BookingDto.Request.class);
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(bookingService.updateBooking(id, req, series, institutionId, isSuperAdmin)));
    }

    private void toggleCancel(Context ctx) {
        AuthMiddleware.requireAdminOrTeacher(ctx);
        int id = Integer.parseInt(ctx.pathParam("id"));
        int institutionId = ctx.attribute(AuthMiddleware.ATTR_INSTITUTION_ID);
        boolean isSuperAdmin = AuthMiddleware.isSuperAdmin(ctx);
        ctx.json(ApiResponse.ok(bookingService.toggleCancel(id, institutionId, isSuperAdmin)));
    }
}
