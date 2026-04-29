package no.example.verdan.auth;

import org.mindrot.jbcrypt.BCrypt;

import no.example.verdan.dao.UserDao;
import no.example.verdan.model.User;

public class AuthService {
    private final UserDao users = new UserDao();

    /**
     * Authenticate a user by username OR email + password.
     * Tries username first, then falls back to email lookup.
     */
    public User authenticate(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        // Try username first
        var list = users.findByUsername(identifier);

        // If not found by username, try email
        if (list.isEmpty()) {
            list = users.findByEmail(identifier);
        }

        if (list.isEmpty()) return null;
        User u = list.get(0);
        if (u.getPassword() == null) return null;
        return BCrypt.checkpw(password, u.getPassword()) ? u : null;
    }

    public String hash(String raw) { return BCrypt.hashpw(raw, BCrypt.gensalt(12)); }
}