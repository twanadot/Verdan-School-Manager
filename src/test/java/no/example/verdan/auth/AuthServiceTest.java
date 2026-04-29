package no.example.verdan.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthService.
 * Tests password hashing and verification logic.
 * Note: authenticate() requires a database, so it is tested
 * in integration tests instead.
 */
class AuthServiceTest {

    private final AuthService authService = new AuthService();

    @Test
    @DisplayName("Hash produces a valid BCrypt hash")
    void hashProducesBcrypt() {
        String hash = authService.hash("password123");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"), "Hash should be BCrypt format");
    }

    @Test
    @DisplayName("Same password produces different hashes (salted)")
    void hashIsSalted() {
        String hash1 = authService.hash("password123");
        String hash2 = authService.hash("password123");

        assertNotEquals(hash1, hash2, "Each hash should have a unique salt");
    }

    @Test
    @DisplayName("BCrypt can verify the generated hash")
    void hashCanBeVerified() {
        String password = "mySecretPass";
        String hash = authService.hash(password);

        assertTrue(BCrypt.checkpw(password, hash));
    }

    @Test
    @DisplayName("Wrong password does not match hash")
    void wrongPasswordFails() {
        String hash = authService.hash("correctPassword");

        assertFalse(BCrypt.checkpw("wrongPassword", hash));
    }

    @Test
    @DisplayName("Hash handles special characters")
    void hashSpecialChars() {
        String password = "p@$$w0rd!#%&*";
        String hash = authService.hash(password);

        assertTrue(BCrypt.checkpw(password, hash));
    }

    @Test
    @DisplayName("Hash handles very long passwords")
    void hashLongPassword() {
        String password = "a".repeat(100);
        String hash = authService.hash(password);

        assertNotNull(hash);
        assertTrue(BCrypt.checkpw(password, hash));
    }

    @Test
    @DisplayName("Hash handles empty string")
    void hashEmptyString() {
        String hash = authService.hash("");

        assertNotNull(hash);
        assertTrue(BCrypt.checkpw("", hash));
    }

    @Test
    @DisplayName("Hash uses 12 rounds (cost factor)")
    void hashUses12Rounds() {
        String hash = authService.hash("test");
        // BCrypt format: $2a$12$...
        assertTrue(hash.startsWith("$2a$12$"), "Expected 12 rounds, got: " + hash.substring(0, 7));
    }
}
