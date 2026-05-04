package no.example.verdan.api;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.auth.AuthService;
import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.AuthDto;
import no.example.verdan.model.User;
import no.example.verdan.security.JwtUtil;
import no.example.verdan.security.RateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication API controller.
 * Handles login, token refresh, and rate limiting.
 */
public class AuthApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthApiController.class);
    private final AuthService authService = new AuthService();

    // Rate limiter: max 50 login attempts per IP per 15 minutes
    private final RateLimiter loginRateLimiter = new RateLimiter(50, 15 * 60 * 1000);

    public void registerRoutes(Javalin app) {
        app.post("/api/login", this::login);
        app.post("/api/auth/refresh", this::refreshToken);
    }

    /**
     * POST /api/login
     * Body: { "username": "...", "password": "..." }
     * Returns: JWT access token + refresh token + user info
     */
    private void login(Context ctx) {
        // Rate limiting by client IP
        String clientIp = ctx.ip();
        if (!loginRateLimiter.isAllowed(clientIp)) {
            ctx.header("Retry-After", "900"); // 15 min
            ctx.status(429).json(ApiResponse.error("Too many login attempts. Please try again later."));
            return;
        }

        AuthDto.LoginRequest req = ctx.bodyAsClass(AuthDto.LoginRequest.class);

        if (req.username() == null || req.username().isBlank() ||
                req.password() == null || req.password().isBlank()) {
            ctx.status(400).json(ApiResponse.error("Username/email and password are required"));
            return;
        }

        User user = authService.authenticate(req.username(), req.password());

        if (user == null) {
            LOG.warn("Failed login attempt for identifier: {} from IP: {}", req.username(), clientIp);
            ctx.status(401).json(ApiResponse.error("Invalid username/email or password"));
            return;
        }

        Integer instId = (user.getInstitution() != null) ? user.getInstitution().getId() : null;
        String instName = (user.getInstitution() != null) ? user.getInstitution().getName() : null;
        String instLevel = (user.getInstitution() != null) ? user.getInstitution().getLevel() : null;
        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), instId, instName);
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole(), instId, instName);

        LOG.info("User '{}' logged in successfully (role: {})", user.getUsername(), user.getRole());
        ctx.json(ApiResponse.ok(new AuthDto.LoginResponse(accessToken, refreshToken,
                new AuthDto.UserInfo(
                        user.getId(), user.getUsername(), user.getRole(),
                        user.getFirstName(), user.getLastName(), user.getEmail(),
                        instId, instName, instLevel))));
    }

    /**
     * POST /api/auth/refresh
     * Body: { "refreshToken": "..." }
     * Returns: new access token + new refresh token
     */
    private void refreshToken(Context ctx) {
        AuthDto.RefreshRequest req = ctx.bodyAsClass(AuthDto.RefreshRequest.class);

        if (req.refreshToken() == null || req.refreshToken().isBlank()) {
            ctx.status(400).json(ApiResponse.error("Refresh token is required"));
            return;
        }

        DecodedJWT jwt = JwtUtil.verifyToken(req.refreshToken());
        if (jwt == null) {
            ctx.status(401).json(ApiResponse.error("Invalid or expired refresh token"));
            return;
        }

        if (!JwtUtil.isRefreshToken(jwt)) {
            ctx.status(400).json(ApiResponse.error("Provided token is not a refresh token"));
            return;
        }

        // Generate new token pair
        int userId = JwtUtil.getUserId(jwt);
        String username = JwtUtil.getUsername(jwt);
        String role = JwtUtil.getRole(jwt);
        Integer instId = JwtUtil.getInstitutionId(jwt);
        String instName = jwt.getClaim("institutionName").asString();

        String newAccessToken = JwtUtil.generateToken(userId, username, role, instId, instName);
        String newRefreshToken = JwtUtil.generateRefreshToken(userId, username, role, instId, instName);

        LOG.info("Token refreshed for user: {}", username);
        ctx.json(ApiResponse.ok(new AuthDto.TokenResponse(newAccessToken, newRefreshToken)));
    }
}
