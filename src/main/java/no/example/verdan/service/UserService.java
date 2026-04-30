package no.example.verdan.service;

import no.example.verdan.auth.AuthService;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.UserDto;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.User;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Service layer for user management.
 * Handles business logic between the API controllers and the data access layer.
 */
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    private final UserDao userDao;
    private final AuthService authService;
    private final ProgramMemberDao memberDao;

    /** Roles that INSTITUTION_ADMIN is allowed to create/assign. */
    private static final Set<String> INSTITUTION_ADMIN_ALLOWED_ROLES = Set.of("INSTITUTION_ADMIN", "TEACHER", "STUDENT");

    public UserService() {
        this(new UserDao(), new AuthService(), new ProgramMemberDao());
    }

    /** Constructor injection for testing. */
    public UserService(UserDao userDao, AuthService authService, ProgramMemberDao memberDao) {
        this.userDao = userDao;
        this.authService = authService;
        this.memberDao = memberDao;
    }

    /** Get all users, optionally filtered by role. Teachers only see students in their programs. */
    public List<UserDto.Response> getAllUsers(String roleFilter, int institutionId, boolean isSuperAdmin,
                                              String callerRole, String callerUsername) {
        List<User> users;
        if (isSuperAdmin) {
            users = userDao.findAll();
            users = users.stream()
                    .filter(u -> "INSTITUTION_ADMIN".equalsIgnoreCase(u.getRole()))
                    .toList();
        } else {
            if (roleFilter != null && !roleFilter.isBlank()) {
                users = userDao.findAllByRole(roleFilter, institutionId);
            } else {
                users = userDao.findAll(institutionId);
            }
        }

        // Teachers only see students in their own programs/classes
        if ("TEACHER".equalsIgnoreCase(callerRole) && callerUsername != null) {
            List<User> teacherUsers = userDao.findByUsername(callerUsername);
            if (!teacherUsers.isEmpty()) {
                // Get program IDs the teacher belongs to
                List<ProgramMember> teacherMemberships = memberDao.findByUser(teacherUsers.get(0).getId());
                java.util.Set<Integer> teacherProgramIds = new java.util.HashSet<>();
                for (ProgramMember pm : teacherMemberships) {
                    if ("TEACHER".equalsIgnoreCase(pm.getRole())) {
                        teacherProgramIds.add(pm.getProgram().getId());
                    }
                }

                // Get all student user IDs in those programs
                java.util.Set<Integer> allowedStudentIds = new java.util.HashSet<>();
                for (int programId : teacherProgramIds) {
                    List<ProgramMember> members = memberDao.findActiveByProgram(programId);
                    for (ProgramMember pm : members) {
                        if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                            allowedStudentIds.add(pm.getUser().getId());
                        }
                    }
                }

                // Filter: keep non-students as-is, but only keep students in teacher's programs
                users = users.stream().filter(u -> {
                    if ("STUDENT".equalsIgnoreCase(u.getRole())) {
                        return allowedStudentIds.contains(u.getId());
                    }
                    return true;
                }).toList();
            }
        }

        return users.stream().map(this::toResponse).toList();
    }

    /** Get a single user by ID. */
    public UserDto.Response getUserById(int id, int institutionId, boolean isSuperAdmin) {
        User user = isSuperAdmin ? userDao.find(id) : userDao.find(id, institutionId);
        if (user == null) return null;
        return toResponse(user);
    }

    /** Create a new user. Returns the created user response.
     * @param callerRole the role of the user making the request (for permission checks)
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if username already exists or role not allowed
     */
    public UserDto.Response createUser(UserDto.CreateRequest req, int institutionId, boolean isSuperAdmin, String callerRole) {
        // Auto-generate username from first + last name
        String generatedUsername;
        if (req.username() != null && !req.username().isBlank()) {
            generatedUsername = InputValidator.sanitize(req.username()).toLowerCase().replaceAll("\\s+", "");
        } else if (req.firstName() != null && req.lastName() != null
                   && !req.firstName().isBlank() && !req.lastName().isBlank()) {
            String base = (req.firstName().trim() + "." + req.lastName().trim())
                    .toLowerCase().replaceAll("\\s+", "");
            generatedUsername = InputValidator.sanitize(base);
        } else {
            throw new ValidationException(List.of("firstName and lastName are required to generate a username."));
        }

        // Ensure uniqueness by appending a number if necessary
        String finalUsername = generatedUsername;
        int counter = 1;
        while (!userDao.findByUsername(finalUsername).isEmpty()) {
            finalUsername = generatedUsername + counter;
            counter++;
        }

        // Validate input
        List<String> errors = InputValidator.validateUser(
                finalUsername, req.role(), req.email(), req.phone());

        errors.addAll(InputValidator.validatePassword(req.password()));

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // --- Role permission enforcement ---
        String targetRole = req.role().toUpperCase();

        if ("INSTITUTION_ADMIN".equalsIgnoreCase(callerRole)) {
            // INSTITUTION_ADMIN can only create TEACHER and STUDENT
            if (!INSTITUTION_ADMIN_ALLOWED_ROLES.contains(targetRole)) {
                throw new IllegalStateException(
                    "Institution Admin can only create TEACHER or STUDENT users.");
            }
        }

        // Only SUPER_ADMIN can create SUPER_ADMIN users
        if ("SUPER_ADMIN".equals(targetRole)
                && !"SUPER_ADMIN".equalsIgnoreCase(callerRole)) {
            throw new IllegalStateException(
                "Only Super Admin can create SUPER_ADMIN users.");
        }

        // Validate gender if provided
        String gender = null;
        if (req.gender() != null && !req.gender().isBlank()) {
            gender = req.gender().toUpperCase();
            if (!"MALE".equals(gender) && !"FEMALE".equals(gender)) {
                throw new ValidationException(List.of("Gender must be MALE or FEMALE"));
            }
        }

        User user = new User();
        user.setUsername(finalUsername);
        user.setPassword(authService.hash(req.password()));
        user.setRole(targetRole);
        user.setFirstName(InputValidator.sanitize(req.firstName()));
        user.setLastName(InputValidator.sanitize(req.lastName()));
        user.setEmail(InputValidator.sanitize(req.email()));
        user.setPhone(InputValidator.sanitize(req.phone()));
        user.setGender(gender);

        // Parse birthDate
        if (req.birthDate() != null && !req.birthDate().isBlank()) {
            try {
                user.setBirthDate(LocalDate.parse(req.birthDate()));
            } catch (Exception e) {
                throw new ValidationException(List.of("Invalid birthDate format. Use yyyy-MM-dd."));
            }
        }
        
        // Link to institution
        no.example.verdan.model.Institution inst = new no.example.verdan.model.Institution();
        inst.setId(req.institutionId() != null && isSuperAdmin ? req.institutionId() : institutionId);
        user.setInstitution(inst);

        userDao.save(user);
        LOG.info("User created: {} ({}) in institution {} by {}", user.getUsername(), user.getRole(), inst.getId(), callerRole);
        return toResponse(user);
    }

    /** Backward-compatible overload for existing callers. */
    public UserDto.Response createUser(UserDto.CreateRequest req, int institutionId, boolean isSuperAdmin) {
        return createUser(req, institutionId, isSuperAdmin, isSuperAdmin ? "SUPER_ADMIN" : "INSTITUTION_ADMIN");
    }

    /** Update an existing user.
     * @param callerRole the role of the user making the request (for permission checks)
     * @throws IllegalArgumentException if user not found
     * @throws ValidationException if validation fails
     */
    public UserDto.Response updateUser(int id, UserDto.UpdateRequest req, int institutionId, boolean isSuperAdmin, String callerRole) {
        User existing = isSuperAdmin ? userDao.find(id) : userDao.find(id, institutionId);
        if (existing == null) {
            throw new IllegalArgumentException("User not found or access denied");
        }

        List<String> errors = InputValidator.validateUser(
                req.username() != null ? req.username() : existing.getUsername(),
                req.role() != null ? req.role() : existing.getRole(),
                req.email(), req.phone());

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // --- Role permission enforcement on update ---
        if (req.role() != null) {
            String newRole = req.role().toUpperCase();

            if ("INSTITUTION_ADMIN".equalsIgnoreCase(callerRole)) {
                // INSTITUTION_ADMIN can assign INSTITUTION_ADMIN, TEACHER, STUDENT
                if (!INSTITUTION_ADMIN_ALLOWED_ROLES.contains(newRole)) {
                    throw new IllegalStateException(
                        "Institution Admin can only assign INSTITUTION_ADMIN, TEACHER or STUDENT roles.");
                }
                // INSTITUTION_ADMIN cannot modify SUPER_ADMIN users
                if ("SUPER_ADMIN".equalsIgnoreCase(existing.getRole())) {
                    throw new IllegalStateException(
                        "Institution Admin cannot modify Super Admin users.");
                }
            }
        }

        if (req.username() != null)
            existing.setUsername(InputValidator.sanitize(req.username()));
        if (req.role() != null)
            existing.setRole(req.role().toUpperCase());
        if (req.firstName() != null)
            existing.setFirstName(InputValidator.sanitize(req.firstName()));
        if (req.lastName() != null)
            existing.setLastName(InputValidator.sanitize(req.lastName()));
        if (req.email() != null)
            existing.setEmail(InputValidator.sanitize(req.email()));
        if (req.phone() != null)
            existing.setPhone(InputValidator.sanitize(req.phone()));
        if (req.gender() != null) {
            String g = req.gender().toUpperCase();
            if ("MALE".equals(g) || "FEMALE".equals(g)) {
                existing.setGender(g);
            }
        }
        if (req.birthDate() != null && !req.birthDate().isBlank()) {
            try {
                existing.setBirthDate(LocalDate.parse(req.birthDate()));
            } catch (Exception e) {
                throw new ValidationException(List.of("Invalid birthDate format. Use yyyy-MM-dd."));
            }
        }
        if (req.password() != null && !req.password().isBlank()) {
            existing.setPassword(authService.hash(req.password()));
        }
        if (req.institutionId() != null && isSuperAdmin) {
            no.example.verdan.model.Institution newInst = new no.example.verdan.model.Institution();
            newInst.setId(req.institutionId());
            existing.setInstitution(newInst);
        }

        userDao.update(existing);
        LOG.info("User updated: {} (ID: {}) by {} (institution: {}, super: {})", existing.getUsername(), id, callerRole, institutionId, isSuperAdmin);
        return toResponse(existing);
    }

    /** Backward-compatible overload for existing callers. */
    public UserDto.Response updateUser(int id, UserDto.UpdateRequest req, int institutionId, boolean isSuperAdmin) {
        return updateUser(id, req, institutionId, isSuperAdmin, isSuperAdmin ? "SUPER_ADMIN" : "INSTITUTION_ADMIN");
    }

    /** Delete a user and all related data. */
    public void deleteUser(int id, int currentUserId, int institutionId, boolean isSuperAdmin) {
        if (id == currentUserId) {
            throw new IllegalStateException("You cannot delete your own account.");
        }

        User user = isSuperAdmin ? userDao.find(id) : userDao.find(id, institutionId);
        if (user == null) {
            throw new IllegalArgumentException("User not found or access denied");
        }
        userDao.deleteUserAndRelations(user);
        LOG.info("User deleted: {} (ID: {}) by institution {} (Super: {})", user.getUsername(), id, institutionId, isSuperAdmin);
    }

    /** Convert User entity to response DTO. */
    private UserDto.Response toResponse(User u) {
        Integer instId = u.getInstitution() != null ? u.getInstitution().getId() : null;
        String instName = u.getInstitution() != null ? u.getInstitution().getName() : "Default";
        String birthStr = u.getBirthDate() != null ? u.getBirthDate().toString() : null;
        return new UserDto.Response(
                u.getId(), u.getUsername(), u.getRole(),
                u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                u.getGender(), birthStr, instId, instName);
    }
}
