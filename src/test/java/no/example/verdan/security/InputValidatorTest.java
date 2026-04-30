package no.example.verdan.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InputValidator.
 * These are pure logic tests – no database or server needed.
 */
class InputValidatorTest {

    // ===================== isNotBlank =====================

    @Nested
    @DisplayName("isNotBlank")
    class IsNotBlank {
        @Test
        void validString() {
            assertTrue(InputValidator.isNotBlank("hello"));
        }

        @Test
        void nullValue() {
            assertFalse(InputValidator.isNotBlank(null));
        }

        @Test
        void emptyString() {
            assertFalse(InputValidator.isNotBlank(""));
        }

        @Test
        void blankSpaces() {
            assertFalse(InputValidator.isNotBlank("   "));
        }
    }

    // ===================== isValidEmail =====================

    @Nested
    @DisplayName("isValidEmail")
    class IsValidEmail {
        @Test
        void validEmail() {
            assertTrue(InputValidator.isValidEmail("user@example.com"));
        }

        @Test
        void validWithDots() {
            assertTrue(InputValidator.isValidEmail("first.last@school.no"));
        }

        @Test
        void validWithPlus() {
            assertTrue(InputValidator.isValidEmail("user+tag@gmail.com"));
        }

        @Test
        void missingAt() {
            assertFalse(InputValidator.isValidEmail("userexample.com"));
        }

        @Test
        void missingDomain() {
            assertFalse(InputValidator.isValidEmail("user@"));
        }

        @Test
        void missingTld() {
            assertFalse(InputValidator.isValidEmail("user@example"));
        }

        @Test
        void nullEmail() {
            assertFalse(InputValidator.isValidEmail(null));
        }

        @Test
        void emptyEmail() {
            assertFalse(InputValidator.isValidEmail(""));
        }
    }

    // ===================== isValidPhone =====================

    @Nested
    @DisplayName("isValidPhone")
    class IsValidPhone {
        @Test
        void validNorwegian() {
            assertTrue(InputValidator.isValidPhone("45454545"));
        }

        @Test
        void validWithPlus() {
            assertTrue(InputValidator.isValidPhone("+47 123 45 678"));
        }

        @Test
        void validWithDash() {
            assertTrue(InputValidator.isValidPhone("123-456-7890"));
        }

        @Test
        void nullIsAllowed() {
            assertTrue(InputValidator.isValidPhone(null));
        }

        @Test
        void blankIsAllowed() {
            assertTrue(InputValidator.isValidPhone(""));
        }

        @Test
        void tooShort() {
            assertFalse(InputValidator.isValidPhone("12"));
        }

        @Test
        void withLetters() {
            assertFalse(InputValidator.isValidPhone("abc12345"));
        }
    }

    // ===================== isValidUsername =====================

    @Nested
    @DisplayName("isValidUsername")
    class IsValidUsername {
        @Test
        void validSimple() {
            assertTrue(InputValidator.isValidUsername("admin"));
        }

        @Test
        void validWithDot() {
            assertTrue(InputValidator.isValidUsername("first.last"));
        }

        @Test
        void validWithDash() {
            assertTrue(InputValidator.isValidUsername("user-name"));
        }

        @Test
        void validWithUnderscore() {
            assertTrue(InputValidator.isValidUsername("user_name"));
        }

        @Test
        void tooShort() {
            assertFalse(InputValidator.isValidUsername("a"));
        }

        @Test
        void nullUsername() {
            assertFalse(InputValidator.isValidUsername(null));
        }

        @Test
        void withSpaces() {
            assertFalse(InputValidator.isValidUsername("user name"));
        }

        @Test
        void withSpecialChars() {
            assertFalse(InputValidator.isValidUsername("user@name"));
        }
    }

    // ===================== isValidRole =====================

    @Nested
    @DisplayName("isValidRole")
    class IsValidRole {
        @Test
        void institutionAdmin() {
            assertTrue(InputValidator.isValidRole("INSTITUTION_ADMIN"));
        }

        @Test
        void teacher() {
            assertTrue(InputValidator.isValidRole("TEACHER"));
        }

        @Test
        void student() {
            assertTrue(InputValidator.isValidRole("STUDENT"));
        }

        @Test
        void caseInsensitive() {
            assertTrue(InputValidator.isValidRole("institution_admin"));
        }

        @Test
        void invalidRole() {
            assertFalse(InputValidator.isValidRole("SUPERADMIN"));
        }

        @Test
        void nullRole() {
            assertFalse(InputValidator.isValidRole(null));
        }

        @Test
        void emptyRole() {
            assertFalse(InputValidator.isValidRole(""));
        }
    }

    // ===================== isValidSubjectCode =====================

    @Nested
    @DisplayName("isValidSubjectCode")
    class IsValidSubjectCode {
        @Test
        void validCode() {
            assertTrue(InputValidator.isValidSubjectCode("MAT101"));
        }

        @Test
        void validLettersOnly() {
            assertTrue(InputValidator.isValidSubjectCode("MATH"));
        }

        @Test
        void tooShort() {
            assertFalse(InputValidator.isValidSubjectCode("M"));
        }

        @Test
        void withSpaces() {
            assertFalse(InputValidator.isValidSubjectCode("MAT 101"));
        }

        @Test
        void withHyphen() {
            assertTrue(InputValidator.isValidSubjectCode("MAT-101"));
        }

        @Test
        void withSpecialChars() {
            assertFalse(InputValidator.isValidSubjectCode("MAT@101"));
        }

        @Test
        void nullCode() {
            assertFalse(InputValidator.isValidSubjectCode(null));
        }
    }

    // ===================== isValidLength =====================

    @Nested
    @DisplayName("isValidLength")
    class IsValidLength {
        @Test
        void withinBounds() {
            assertTrue(InputValidator.isValidLength("hello", 1, 10));
        }

        @Test
        void exactMin() {
            assertTrue(InputValidator.isValidLength("ab", 2, 10));
        }

        @Test
        void exactMax() {
            assertTrue(InputValidator.isValidLength("1234567890", 1, 10));
        }

        @Test
        void tooShort() {
            assertFalse(InputValidator.isValidLength("a", 2, 10));
        }

        @Test
        void tooLong() {
            assertFalse(InputValidator.isValidLength("12345678901", 1, 10));
        }

        @Test
        void nullWithMinZero() {
            assertTrue(InputValidator.isValidLength(null, 0, 10));
        }

        @Test
        void nullWithMinOne() {
            assertFalse(InputValidator.isValidLength(null, 1, 10));
        }
    }

    // ===================== sanitize =====================

    @Nested
    @DisplayName("sanitize")
    class Sanitize {
        @Test
        void normalString() {
            assertEquals("hello", InputValidator.sanitize("hello"));
        }

        @Test
        void trimWhitespace() {
            assertEquals("hello", InputValidator.sanitize("  hello  "));
        }

        @Test
        void nullInput() {
            assertNull(InputValidator.sanitize(null));
        }

        @Test
        void preserveNewlines() {
            assertEquals("line1\nline2", InputValidator.sanitize("line1\nline2"));
        }

        @Test
        void removeControlChars() {
            assertEquals("clean", InputValidator.sanitize("cle\u0000an"));
        }

        @Test
        void xssAttempt() {
            String result = InputValidator.sanitize("<script>alert('xss')</script>");
            // sanitize removes control chars, not HTML tags
            assertNotNull(result);
        }
    }

    // ===================== validateUser =====================

    @Nested
    @DisplayName("validateUser")
    class ValidateUser {
        @Test
        void validUser() {
            List<String> errors = InputValidator.validateUser("admin", "INSTITUTION_ADMIN", "admin@test.com", "12345678");
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        void invalidUsername() {
            List<String> errors = InputValidator.validateUser("a", "INSTITUTION_ADMIN", "admin@test.com", "12345678");
            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("Username"));
        }

        @Test
        void invalidRole() {
            List<String> errors = InputValidator.validateUser("admin", "SUPERUSER", "admin@test.com", "12345678");
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("Role")));
        }

        @Test
        void invalidEmail() {
            List<String> errors = InputValidator.validateUser("admin", "ADMIN", "not-an-email", "12345678");
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("email")));
        }

        @Test
        void multipleErrors() {
            List<String> errors = InputValidator.validateUser("a", "INVALID", "bad", "x");
            assertTrue(errors.size() >= 3, "Expected multiple errors");
        }

        @Test
        void nullEmailAllowed() {
            List<String> errors = InputValidator.validateUser("admin", "INSTITUTION_ADMIN", null, null);
            assertTrue(errors.isEmpty());
        }
    }
}
