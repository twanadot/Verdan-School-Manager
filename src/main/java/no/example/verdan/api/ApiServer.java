package no.example.verdan.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import no.example.verdan.app.DataSeeder;
import no.example.verdan.dto.ApiResponse;
import no.example.verdan.service.ConflictException;
import no.example.verdan.service.NotFoundException;
import no.example.verdan.service.ValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main REST API server for Verdan University Manager.
 * 
 * Starts a Javalin HTTP server on port 8080 with JWT-based authentication,
 * CORS support, and RESTful endpoints for all entities.
 */
public class ApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    private static final int DEFAULT_PORT = 8080;

    private final Javalin app;

    public ApiServer() {
        this(DEFAULT_PORT, true);
    }

    public ApiServer(int port) {
        this(port, true);
    }

    /**
     * @param port      server port (0 for test mode)
     * @param autoStart if true, starts the server immediately
     */
    public ApiServer(int port, boolean autoStart) {
        // Configure Jackson for JSON serialization
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));

            // Enable CORS for all origins (adjust for production)
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.anyHost();
                });
            });
        });

        // ---- Global Exception Handlers ----

        // Service-layer exceptions → proper HTTP codes
        app.exception(ValidationException.class, (e, ctx) -> {
            LOG.warn("Validation error on {} {}: {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(400).json(ApiResponse.validationError(e.getErrors()));
        });

        app.exception(NotFoundException.class, (e, ctx) -> {
            LOG.warn("404 Not Found on {} {}: {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(404).json(ApiResponse.error(e.getMessage()));
        });

        app.exception(ConflictException.class, (e, ctx) -> {
            ctx.status(409).json(ApiResponse.error(e.getMessage()));
        });

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            LOG.warn("404 IllegalArg on {} {}: {}", ctx.method(), ctx.path(), e.getMessage());
            ctx.status(404).json(ApiResponse.error(e.getMessage()));
        });

        app.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        });

        app.exception(NumberFormatException.class, (e, ctx) -> {
            ctx.status(400).json(ApiResponse.error("Invalid numeric parameter"));
        });

        // Catch-all for unexpected errors
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled exception on {} {}: {}", ctx.method(), ctx.path(), e.getMessage(), e);
            ctx.status(500).json(ApiResponse.error("Noe gikk galt. Prøv igjen eller kontakt administrator."));
        });

        // Register middleware (order matters: metrics first, auth second)
        MetricsMiddleware.register(app);
        AuthMiddleware.register(app);

        // Register routes
        new AuthApiController().registerRoutes(app);
        new UserApiController().registerRoutes(app);
        new SubjectApiController().registerRoutes(app);
        new ProgramApiController().registerRoutes(app);
        new GradeApiController().registerRoutes(app);
        new AttendanceApiController().registerRoutes(app);
        new RoomApiController().registerRoutes(app);
        new BookingApiController().registerRoutes(app);
        new InstitutionApiController().registerRoutes(app);
        new ChatApiController().registerRoutes(app);
        new FileApiController().registerRoutes(app);
        new PromotionApiController().registerRoutes(app);
        new AdmissionApiController().registerRoutes(app);
        new PortalApiController().registerRoutes(app);

        // WebSocket for real-time chat
        app.ws("/ws/chat", ws -> ChatWebSocket.getInstance().configure(ws));

        // Health check
        app.get("/api/health", ctx -> ctx.json(ApiResponse.ok(
                new HealthResponse("UP", "Verdan API"))));

        // Metrics endpoint (ADMIN only)
        app.get("/api/metrics", ctx -> {
            AuthMiddleware.requireAdmin(ctx);
            var metrics = MetricsMiddleware.getMetrics();
            var result = new java.util.LinkedHashMap<String, Object>();
            metrics.forEach((endpoint, m) -> {
                var entry = new java.util.LinkedHashMap<String, Object>();
                entry.put("totalRequests", m.getTotalRequests());
                entry.put("avgDurationMs", Math.round(m.getAvgDurationMs() * 10.0) / 10.0);
                entry.put("maxDurationMs", m.getMaxDurationMs());
                entry.put("errorCount", m.getErrorCount());
                entry.put("errorRate", Math.round(m.getErrorRate() * 1000.0) / 10.0 + "%");
                result.put(endpoint, entry);
            });
            ctx.json(ApiResponse.ok(result));
        });

        // ---- Swagger / OpenAPI Documentation ----
        // Serve OpenAPI spec
        app.get("/api/openapi.json", ctx -> {
            ctx.contentType("application/json");
            var is = getClass().getResourceAsStream("/openapi.json");
            if (is != null) {
                ctx.result(is);
            } else {
                ctx.status(404).result("OpenAPI spec not found");
            }
        });

        // Swagger UI redirect
        app.get("/swagger", ctx -> ctx.redirect("/swagger/index.html"));
        app.get("/swagger/", ctx -> ctx.redirect("/swagger/index.html"));

        // Scalar API Documentation
        app.get("/scalar", ctx -> {
            ctx.contentType("text/html");
            ctx.result("""
                <!doctype html>
                <html>
                  <head>
                    <title>Verdan API Documentation</title>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <style>
                      body { margin: 0; }
                    </style>
                  </head>
                  <body>
                    <script
                      id="api-reference"
                      data-url="/api/openapi.json"></script>
                    <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  </body>
                </html>
                """);
        });

        // Serve Swagger UI from WebJar
        app.get("/swagger/{path}", ctx -> {
            String path = ctx.pathParam("path");
            // Override initializer to point to our spec
            if ("swagger-initializer.js".equals(path)) {
                ctx.contentType("application/javascript");
                ctx.result("""
                    window.onload = function() {
                      window.ui = SwaggerUIBundle({
                        url: "/api/openapi.json",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                          SwaggerUIBundle.presets.apis,
                          SwaggerUIStandalonePreset
                        ],
                        plugins: [
                          SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout"
                      });
                    };
                    """);
                return;
            }
            // Serve static files from the WebJar
            var is = getClass().getResourceAsStream("/META-INF/resources/webjars/swagger-ui/5.17.14/" + path);
            if (is != null) {
                if (path.endsWith(".html")) ctx.contentType("text/html");
                else if (path.endsWith(".css")) ctx.contentType("text/css");
                else if (path.endsWith(".js")) ctx.contentType("application/javascript");
                else if (path.endsWith(".png")) ctx.contentType("image/png");
                ctx.result(is);
            } else {
                ctx.status(404);
            }
        });

        if (autoStart) {
            app.start(port);
            LOG.info("Verdan REST API started on port {}", port);
        }
    }

    public void stop() {
        app.stop();
    }

    public Javalin getApp() {
        return app;
    }

    /**
     * Standalone entry point for the API server (without JavaFX).
     */
    public static void main(String[] args) {
        LOG.info("Seeding database...");
        new DataSeeder().seed();

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port '{}', using default {}", args[0], DEFAULT_PORT);
            }
        }

        new ApiServer(port);
        LOG.info("API documentation available at http://localhost:{}/api/health", port);
    }

    // --- Response DTOs ---

    public record ErrorResponse(String error) {
    }

    public record HealthResponse(String status, String service) {
    }
}
