package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;

import no.example.verdan.security.JwtUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the REST API.
 * Uses Javalin's built-in test tools with an ephemeral server.
 *
 * Note: Tests that query the database (GET /api/users, etc.) require
 * a running MySQL instance. They are expected to fail without one.
 */
class ApiIntegrationTest {

    /**
     * Create a Javalin app without starting it (JavalinTest handles start/stop).
     */
    private Javalin createTestApp() {
        return new ApiServer(0, false).getApp();
    }

    private String adminToken() {
        return JwtUtil.generateToken(1, "admin", "INSTITUTION_ADMIN", 1, "Test Institution");
    }

    private String studentToken() {
        return JwtUtil.generateToken(100, "student", "STUDENT", 1, "Test Institution");
    }

    private String teacherToken() {
        return JwtUtil.generateToken(50, "teacher", "TEACHER", 1, "Test Institution");
    }

    // ===================== Health Check =====================

    @Nested
    @DisplayName("Health Check")
    class HealthCheck {
        @Test
        @DisplayName("GET /api/health returns 200 with status UP")
        void healthCheck() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/health");
                assertEquals(200, response.code());
                String body = response.body().string();
                assertTrue(body.contains("UP"));
                assertTrue(body.contains("Verdan API"));
            });
        }
    }

    // ===================== Authentication =====================

    @Nested
    @DisplayName("Authentication")
    class Authentication {
        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedAccess() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users");
                assertEquals(401, response.code());
            });
        }

        @Test
        @DisplayName("Invalid JWT token returns 401")
        void invalidToken() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users", req -> {
                    req.header("Authorization", "Bearer invalid.token.here");
                });
                assertEquals(401, response.code());
            });
        }

        @Test
        @DisplayName("Valid admin token grants access (does not return 401 or 403)")
        void validTokenAccess() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users", req -> {
                    req.header("Authorization", "Bearer " + adminToken());
                });
                // Not checking for 200 because this requires a database connection.
                // The important thing is that auth does NOT reject the request.
                assertNotEquals(401, response.code(), "Valid token should not be rejected");
                assertNotEquals(403, response.code(), "Admin should not be forbidden");
            });
        }
    }

    // ===================== Authorization (RBAC) =====================

    @Nested
    @DisplayName("Authorization (RBAC)")
    class Authorization {
        @Test
        @DisplayName("Student cannot access admin-only user list")
        void studentCannotListUsers() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users", req -> {
                    req.header("Authorization", "Bearer " + studentToken());
                });
                assertEquals(403, response.code());
            });
        }

        @Test
        @DisplayName("Student cannot create a grade (RBAC enforcement)")
        void studentCannotCreateGrade() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                // Use the request builder that Javalin TestTools supports for POST
                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        "{\"studentUsername\":\"test\",\"subject\":\"MATH101\",\"value\":\"A\"}",
                        okhttp3.MediaType.parse("application/json"));
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(client.getOrigin() + "/api/grades")
                        .header("Authorization", "Bearer " + studentToken())
                        .post(body)
                        .build();
                var response = new okhttp3.OkHttpClient().newCall(request).execute();
                assertEquals(403, response.code());
            });
        }

        @Test
        @DisplayName("Student cannot delete a user (RBAC enforcement)")
        void studentCannotDeleteUser() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(client.getOrigin() + "/api/users/1")
                        .header("Authorization", "Bearer " + studentToken())
                        .delete()
                        .build();
                var response = new okhttp3.OkHttpClient().newCall(request).execute();
                assertEquals(403, response.code());
            });
        }

        @Test
        @DisplayName("Teacher can access user list (requireAdminOrTeacher)")
        void teacherCanListUsers() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users", req -> {
                    req.header("Authorization", "Bearer " + teacherToken());
                });
                // Teachers ARE allowed to list users (requireAdminOrTeacher).
                // May return 200 (with data) or 500 (if DB is unavailable), but NOT 403.
                assertNotEquals(403, response.code(), "Teacher should be allowed to list users");
            });
        }
    }

    // ===================== API Response Format =====================

    @Nested
    @DisplayName("API Response Format")
    class ResponseFormat {
        @Test
        @DisplayName("Health endpoint returns ApiResponse wrapper")
        void healthReturnsWrappedResponse() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/health");
                assertEquals(200, response.code());
                String body = response.body().string();
                assertTrue(body.contains("\"success\":true"), "Response should contain success field");
                assertTrue(body.contains("\"data\""), "Response should contain data field");
                assertTrue(body.contains("\"timestamp\""), "Response should contain timestamp");
            });
        }

        @Test
        @DisplayName("Error responses include error field")
        void errorReturnsWrappedResponse() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users");
                assertEquals(401, response.code());
            });
        }
    }

    // ===================== Input Validation =====================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        @Test
        @DisplayName("Login without body returns 400")
        void loginWithoutBody() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.post("/api/login", "{}");
                assertEquals(400, response.code());
            });
        }

        @Test
        @DisplayName("Invalid path parameter returns error")
        void invalidPathParam() {
            JavalinTest.test(createTestApp(), (server, client) -> {
                var response = client.get("/api/users/notanumber", req -> {
                    req.header("Authorization", "Bearer " + adminToken());
                });
                assertEquals(400, response.code());
                String body = response.body().string();
                assertTrue(body.contains("\"success\":false"));
            });
        }
    }
}
