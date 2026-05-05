package no.example.verdan.api;

import com.auth0.jwt.interfaces.DecodedJWT;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.ForbiddenResponse;

import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.model.Institution;

import no.example.verdan.security.JwtUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT-based authentication and authorization middleware for the API.
 * Protects all /api/* endpoints except login and health.
 *
 * Role hierarchy:
 *   SUPER_ADMIN > INSTITUTION_ADMIN > TEACHER > STUDENT
 */
public class AuthMiddleware {

    private static final Logger LOG = LoggerFactory.getLogger(AuthMiddleware.class);
    private static final InstitutionDao institutionDao = new InstitutionDao();

    /** Context attribute keys for storing decoded token data. */
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_INSTITUTION_ID = "institutionId";

    /**
     * Register the JWT authentication filter on the Javalin app.
     */
    public static void register(Javalin app) {
        app.before("/api/*", ctx -> {
            String path = ctx.path();

            // Skip auth for public endpoints and file downloads (uses token query param)
            if (path.equals("/api/login") || path.equals("/api/health")
                    || path.equals("/api/openapi.json") || path.equals("/api/auth/refresh")
                    || path.matches("/api/files/\\d+")
                    || path.matches("/api/portal/files/\\d+/download")
                    || path.matches("/api/portal/submissions/\\d+/download")) {
                return;
            }

            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LOG.warn("Missing or invalid Authorization header on {} {}", ctx.method(), path);
                throw new UnauthorizedResponse("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            DecodedJWT jwt = JwtUtil.verifyToken(token);

            if (jwt == null) {
                LOG.warn("Invalid JWT token on {} {}", ctx.method(), path);
                throw new UnauthorizedResponse("Invalid or expired token");
            }

            // Reject refresh tokens used as access tokens
            if (JwtUtil.isRefreshToken(jwt)) {
                LOG.warn("Refresh token used as access token on {} {}", ctx.method(), path);
                throw new UnauthorizedResponse("Cannot use refresh token for API access");
            }

            // Store decoded info in context for downstream handlers
            ctx.attribute(ATTR_USER_ID, JwtUtil.getUserId(jwt));
            ctx.attribute(ATTR_USERNAME, JwtUtil.getUsername(jwt));
            ctx.attribute(ATTR_ROLE, JwtUtil.getRole(jwt));
            ctx.attribute(ATTR_INSTITUTION_ID, JwtUtil.getInstitutionId(jwt));

            // Block staff (INSTITUTION_ADMIN, TEACHER) if their institution is deactivated.
            // Students are allowed through so they can use the application portal to transfer.
            String role = JwtUtil.getRole(jwt);
            Integer instId = JwtUtil.getInstitutionId(jwt);
            if (instId != null && instId > 0 && ("INSTITUTION_ADMIN".equalsIgnoreCase(role) || "TEACHER".equalsIgnoreCase(role))) {
                Institution inst = institutionDao.find(instId);
                if (inst == null || !inst.isActive()) {
                    LOG.warn("Access denied: institution {} is deactivated for user={}", instId, JwtUtil.getUsername(jwt));
                    throw new UnauthorizedResponse("Unauthorized");
                }
            }

            LOG.debug("Authenticated: user={}, role={}", JwtUtil.getUsername(jwt), JwtUtil.getRole(jwt));
        });
    }

    /**
     * Get the authenticated user's role from the context.
     */
    public static String getRole(Context ctx) {
        return ctx.attribute(ATTR_ROLE);
    }

    /**
     * Get the authenticated user's username from the context.
     */
    public static String getUsername(Context ctx) {
        return ctx.attribute(ATTR_USERNAME);
    }

    /**
     * Get the authenticated user's ID from the context.
     */
    public static int getUserId(Context ctx) {
        Integer id = ctx.attribute(ATTR_USER_ID);
        return id != null ? id : -1;
    }

    /**
     * Get the authenticated user's Institution ID from the context.
     */
    public static int getInstitutionId(Context ctx) {
        Integer id = ctx.attribute(ATTR_INSTITUTION_ID);
        return id != null ? id : -1;
    }

    /**
     * Check if the current user has SUPER_ADMIN role.
     */
    public static boolean isSuperAdmin(Context ctx) {
        return "SUPER_ADMIN".equalsIgnoreCase(getRole(ctx));
    }

    /**
     * Check if the current user has any admin role (SUPER_ADMIN or INSTITUTION_ADMIN).
     */
    public static boolean isAnyAdmin(Context ctx) {
        String role = getRole(ctx);
        return "SUPER_ADMIN".equalsIgnoreCase(role) || "INSTITUTION_ADMIN".equalsIgnoreCase(role);
    }

    /**
     * Require a specific role. Throws ForbiddenResponse if the user does not have
     * the role.
     */
    public static void requireRole(Context ctx, String... allowedRoles) {
        String role = getRole(ctx);
        for (String allowed : allowedRoles) {
            if (allowed.equalsIgnoreCase(role))
                return;
        }
        LOG.warn("Access denied: user={} role={} required={}", getUsername(ctx), role, String.join(",", allowedRoles));
        throw new ForbiddenResponse("Insufficient permissions");
    }

    /**
     * Require SUPER_ADMIN role only.
     */
    public static void requireSuperAdmin(Context ctx) {
        requireRole(ctx, "SUPER_ADMIN");
    }

    /**
     * Require any admin role (SUPER_ADMIN or INSTITUTION_ADMIN).
     */
    public static void requireAdmin(Context ctx) {
        requireRole(ctx, "SUPER_ADMIN", "INSTITUTION_ADMIN");
    }

    /**
     * Require ADMIN or TEACHER role.
     */
    public static void requireAdminOrTeacher(Context ctx) {
        requireRole(ctx, "SUPER_ADMIN", "INSTITUTION_ADMIN", "TEACHER");
    }
}
