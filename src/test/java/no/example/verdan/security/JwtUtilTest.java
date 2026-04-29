package no.example.verdan.security;

import com.auth0.jwt.interfaces.DecodedJWT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 * Tests JWT generation, verification, and claim extraction.
 */
class JwtUtilTest {

    @Test
    @DisplayName("Generate token and verify it successfully")
    void generateAndVerify() {
        String token = JwtUtil.generateToken(1, "admin", "ADMIN", 1, "Test Institution");

        assertNotNull(token);
        assertFalse(token.isEmpty());

        DecodedJWT decoded = JwtUtil.verifyToken(token);
        assertNotNull(decoded, "Token should be valid");
    }

    @Test
    @DisplayName("Extract username from token")
    void extractUsername() {
        String token = JwtUtil.generateToken(1, "teacher", "TEACHER", 1, "Test Institution");
        DecodedJWT decoded = JwtUtil.verifyToken(token);

        assertEquals("teacher", JwtUtil.getUsername(decoded));
    }

    @Test
    @DisplayName("Extract role from token")
    void extractRole() {
        String token = JwtUtil.generateToken(1, "student", "STUDENT", 1, "Test Institution");
        DecodedJWT decoded = JwtUtil.verifyToken(token);

        assertEquals("STUDENT", JwtUtil.getRole(decoded));
    }

    @Test
    @DisplayName("Extract userId from token")
    void extractUserId() {
        String token = JwtUtil.generateToken(42, "admin", "ADMIN", 1, "Test Institution");
        DecodedJWT decoded = JwtUtil.verifyToken(token);

        assertEquals(42, JwtUtil.getUserId(decoded));
    }

    @Test
    @DisplayName("Invalid token returns null")
    void invalidToken() {
        DecodedJWT decoded = JwtUtil.verifyToken("this.is.not.a.valid.token");
        assertNull(decoded);
    }

    @Test
    @DisplayName("Tampered token returns null")
    void tamperedToken() {
        String token = JwtUtil.generateToken(1, "admin", "ADMIN", 1, "Test Institution");
        // Tamper with the payload
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        DecodedJWT decoded = JwtUtil.verifyToken(tampered);
        assertNull(decoded);
    }

    @Test
    @DisplayName("Empty token returns null")
    void emptyToken() {
        DecodedJWT decoded = JwtUtil.verifyToken("");
        assertNull(decoded);
    }

    @Test
    @DisplayName("Null token returns null")
    void nullToken() {
        DecodedJWT decoded = JwtUtil.verifyToken(null);
        assertNull(decoded);
    }

    @Test
    @DisplayName("Different users get different tokens")
    void differentTokens() {
        String token1 = JwtUtil.generateToken(1, "admin", "ADMIN", 1, "Test Institution");
        String token2 = JwtUtil.generateToken(2, "teacher", "TEACHER", 1, "Test Institution");

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Token contains correct issuer")
    void correctIssuer() {
        String token = JwtUtil.generateToken(1, "admin", "ADMIN", 1, "Test Institution");
        DecodedJWT decoded = JwtUtil.verifyToken(token);

        assertEquals("verdan-api", decoded.getIssuer());
    }
}
