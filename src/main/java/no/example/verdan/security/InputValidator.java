package no.example.verdan.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized input validation utility.
 * Validates user input to prevent injection attacks and ensure data integrity.
 */
public class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-() ]{4,20}$");

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{2,50}$");

    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{2,20}$");

    private static final Pattern SUBJECT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{2,20}$");

    /**
     * Validate that a string is not null or blank.
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Validate an email address format.
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate a phone number format.
     */
    public static boolean isValidPhone(String phone) {
        return phone == null || phone.isBlank() || PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Validate a username format (alphanumeric, dots, underscores, hyphens).
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Validate a role value.
     */
    public static boolean isValidRole(String role) {
        return role != null && (role.equalsIgnoreCase("SUPER_ADMIN") ||
                role.equalsIgnoreCase("INSTITUTION_ADMIN") ||
                role.equalsIgnoreCase("TEACHER") ||
                role.equalsIgnoreCase("STUDENT"));
    }

    /**
     * Validate a room code format.
     */
    public static boolean isValidRoomCode(String code) {
        return code != null && ROOM_CODE_PATTERN.matcher(code).matches();
    }

    /**
     * Validate a subject code format.
     */
    public static boolean isValidSubjectCode(String code) {
        return code != null && SUBJECT_CODE_PATTERN.matcher(code).matches();
    }

    /**
     * Validate string length is within bounds.
     */
    public static boolean isValidLength(String value, int min, int max) {
        if (value == null)
            return min == 0;
        return value.length() >= min && value.length() <= max;
    }

    /**
     * Sanitize a string by trimming and removing control characters.
     * Helps prevent XSS in API responses.
     */
    public static String sanitize(String input) {
        if (input == null)
            return null;
        return input.trim().replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
    }

    /**
     * Validate user creation/update input and return list of errors.
     */
    public static List<String> validateUser(String username, String role, String email, String phone) {
        List<String> errors = new ArrayList<>();

        if (!isValidUsername(username)) {
            errors.add("Username must be 2-50 characters (letters, digits, dots, underscores, hyphens).");
        }
        if (!isValidRole(role)) {
            errors.add("Role must be SUPER_ADMIN, INSTITUTION_ADMIN, TEACHER, or STUDENT.");
        }
        if (email != null && !email.isBlank() && !isValidEmail(email)) {
            errors.add("Invalid email format.");
        }
        if (!isValidPhone(phone)) {
            errors.add("Invalid phone number format.");
        }

        return errors;
    }

    /**
     * Validate password strength.
     * Requirements: min 8 characters, at least one uppercase letter,
     * one digit, and one special character.
     *
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < 8) {
            errors.add("Password must be at least 8 characters.");
            return errors;
        }
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter.");
        }
        if (!Pattern.compile("[0-9]").matcher(password).find()) {
            errors.add("Password must contain at least one digit.");
        }
        if (!Pattern.compile("[^A-Za-z0-9]").matcher(password).find()) {
            errors.add("Password must contain at least one special character.");
        }
        return errors;
    }
}
