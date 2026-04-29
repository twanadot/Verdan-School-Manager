package no.example.verdan.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.UUID;

/**
 * Utility class for creating and verifying JSON Web Tokens (JWT).
 * Supports both access tokens (short-lived) and refresh tokens (long-lived).
 */
public class JwtUtil {

    // In production, this should be loaded from environment variables
    private static final String SECRET = System.getenv("JWT_SECRET") != null
            ? System.getenv("JWT_SECRET")
            : "verdan-dev-secret-change-in-production";

    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);
    private static final String ISSUER = "verdan-api";

    /** Access token lifetime: 15 minutes. */
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000;

    /** Refresh token lifetime: 7 days. */
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Generate a short-lived access token.
     */
    public static String generateToken(int userId, String username, String role, Integer institutionId, String institutionName) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("institutionId", institutionId)
                .withClaim("institutionName", institutionName)
                .withClaim("type", "access")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_MS))
                .sign(ALGORITHM);
    }

    /**
     * Generate a long-lived refresh token.
     */
    public static String generateRefreshToken(int userId, String username, String role, Integer institutionId, String institutionName) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("institutionId", institutionId)
                .withClaim("institutionName", institutionName)
                .withClaim("type", "refresh")
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
                .sign(ALGORITHM);
    }

    /**
     * Verify and decode a JWT token (access or refresh).
     *
     * @param token the JWT token string
     * @return decoded JWT if valid, null if invalid
     */
    public static DecodedJWT verifyToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM)
                    .withIssuer(ISSUER)
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /**
     * Check if the token is a refresh token.
     */
    public static boolean isRefreshToken(DecodedJWT jwt) {
        String type = jwt.getClaim("type").asString();
        return "refresh".equals(type);
    }

    /** Extract username from a verified token. */
    public static String getUsername(DecodedJWT jwt) {
        return jwt.getClaim("username").asString();
    }

    /** Extract role from a verified token. */
    public static String getRole(DecodedJWT jwt) {
        return jwt.getClaim("role").asString();
    }

    /** Extract user ID from a verified token. */
    public static int getUserId(DecodedJWT jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

    /** Extract institution ID from a verified token. */
    public static Integer getInstitutionId(DecodedJWT jwt) {
        return jwt.getClaim("institutionId").asInt();
    }
}
