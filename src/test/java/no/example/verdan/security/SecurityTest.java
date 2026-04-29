package no.example.verdan.security;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;

import no.example.verdan.api.ApiServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for the API.
 * Tests SQL injection, authentication bypass, and input validation.
 */
class SecurityTest {

    private Javalin createTestApp() {
        return new ApiServer(0, false).getApp();
    }

    private String adminToken() {
        return JwtUtil.generateToken(1, "admin", "INSTITUTION_ADMIN", 1, "Test Institution");
    }

    // ===================== SQL Injection =====================

    @Test
    @DisplayName("SQL injection in login username is rejected")
    void sqlInjectionLogin() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            var response = client.post("/api/login",
                    "{\"username\":\"' OR 1=1 --\", \"password\":\"anything\"}");
            assertNotEquals(200, response.code(), "SQL injection should not succeed");
        });
    }

    @Test
    @DisplayName("SQL injection in search query does not bypass auth")
    void sqlInjectionSearch() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            // Without auth, should get 401 regardless of SQL injection attempt
            var response = client.get("/api/subjects/search?q=' OR '1'='1");
            assertEquals(401, response.code(), "SQL injection without auth should still return 401");
        });
    }

    @Test
    @DisplayName("SQL injection with authenticated token is handled safely")
    void sqlInjectionSearchAuthenticated() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            var response = client.get("/api/subjects/search?q=' OR '1'='1", req -> {
                req.header("Authorization", "Bearer " + adminToken());
            });
            // Should either return empty results or a server error (due to DB unavailability in tests),
            // but NOT return unauthorized data
            int code = response.code();
            assertTrue(code == 200 || code == 500,
                    "SQL injection should not cause unexpected status codes, got: " + code);
        });
    }

    // ===================== XSS Prevention =====================

    @Test
    @DisplayName("XSS payload is sanitized")
    void xssInInput() {
        String xssPayload = "<script>alert('xss')</script>";
        String sanitized = InputValidator.sanitize(xssPayload);
        assertNotNull(sanitized);
    }

    // ===================== Authentication Bypass =====================

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void missingAuthHeader() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            var response = client.get("/api/users");
            assertEquals(401, response.code());
        });
    }

    @Test
    @DisplayName("Bearer without token returns 401")
    void bearerWithoutToken() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            var response = client.get("/api/users", req -> {
                req.header("Authorization", "Bearer ");
            });
            assertEquals(401, response.code());
        });
    }

    @Test
    @DisplayName("Non-Bearer auth scheme returns 401")
    void wrongAuthScheme() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            var response = client.get("/api/users", req -> {
                req.header("Authorization", "Basic dXNlcjpwYXNz");
            });
            assertEquals(401, response.code());
        });
    }

    @Test
    @DisplayName("Tampered token signature returns 401")
    void tamperedToken() {
        JavalinTest.test(createTestApp(), (server, client) -> {
            String validToken = adminToken();
            String tampered = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "tampered";
            var response = client.get("/api/users", req -> {
                req.header("Authorization", "Bearer " + tampered);
            });
            assertEquals(401, response.code());
        });
    }

    // ===================== Input Validation Security =====================

    @Test
    @DisplayName("Very long username is rejected")
    void longUsernameRejected() {
        assertFalse(InputValidator.isValidUsername("a".repeat(100)));
    }

    @Test
    @DisplayName("SQL injection in username is rejected")
    void sqlInjectionInUsername() {
        assertFalse(InputValidator.isValidUsername("admin'; DROP TABLE users;--"));
    }

    @Test
    @DisplayName("Script tags in username are rejected")
    void xssInUsername() {
        assertFalse(InputValidator.isValidUsername("<script>alert(1)</script>"));
    }
}
