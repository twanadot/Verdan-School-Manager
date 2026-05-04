package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.User;
import no.example.verdan.util.HibernateUtil;

public class UserDao extends BaseDao<User> {

    public UserDao() {
        super(User.class);
    }

    /**
     * Find users by username (case-insensitive) - GLOBAL (for login).
     */
    public List<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where lower(u.username) = :u",
                    User.class)
                    .setParameter("u", username.toLowerCase())
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Find user by email (case-insensitive) - GLOBAL (for login).
     */
    public List<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where lower(u.email) = :e",
                    User.class)
                    .setParameter("e", email.toLowerCase())
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Find users by username (case-insensitive) within an institution.
     */
    public List<User> findByUsername(String username, int institutionId) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where lower(u.username) = :u and u.institution.id = :instId",
                    User.class)
                    .setParameter("u", username.toLowerCase())
                    .setParameter("instId", institutionId)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Find users by role (e.g., STUDENT, TEACHER).
     */
    public List<User> findAllByRole(String role, int institutionId) {
        if (role == null || role.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where upper(u.role) = :r and u.institution.id = :instId",
                    User.class)
                    .setParameter("r", role.toUpperCase())
                    .setParameter("instId", institutionId)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Find users within an institution with server-side pagination.
     * Uses JPQL setFirstResult/setMaxResults for efficient database-level paging.
     *
     * @param institutionId the institution to query
     * @param page          zero-indexed page number
     * @param size          number of results per page
     * @return paginated list of users
     */
    public List<User> findAllPaginated(int institutionId, int page, int size) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where u.institution.id = :instId order by u.id",
                    User.class)
                    .setParameter("instId", institutionId)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Count total users in an institution (for pagination metadata).
     */
    public long countByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select count(u) from User u where u.institution.id = :instId",
                    Long.class)
                    .setParameter("instId", institutionId)
                    .getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * Temporary method: Returns all students.
     * (Subject filtering is not directly supported by the current DB schema).
     */
    public List<User> findStudentsBySubjectName(String subjectName, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "from User u where upper(u.role) = 'STUDENT' and u.institution.id = :instId",
                    User.class)
                    .setParameter("instId", institutionId)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Check if this is the main "admin" user.
     */
    private boolean isMainAdmin(User u) {
        return u != null
                && u.getUsername() != null
                && u.getUsername().equalsIgnoreCase("admin");
    }

    /**
     * Count number of ADMIN users.
     */
    private long countAdmins(EntityManager em, int institutionId) {
        return em.createQuery(
                "select count(u) from User u where upper(u.role) = 'ADMIN' and u.institution.id = :instId",
                Long.class)
                .setParameter("instId", institutionId)
                .getSingleResult();
    }

    /**
     * Update user with safety checks for the main ADMIN user.
     * Also preserves the password if not provided in the update object.
     */
    @Override
    public void update(User u) {
        if (u == null || u.getId() == null) {
            throw new IllegalArgumentException("User or ID is null");
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            User existing = em.find(User.class, u.getId());
            if (existing == null) {
                throw new IllegalStateException("User not found in database.");
            }

            // Security check for main ADMIN
            if (isMainAdmin(existing)) {
                String oldUsername = existing.getUsername();
                String newUsername = u.getUsername();

                if (newUsername == null || !oldUsername.equals(newUsername)) {
                    throw new IllegalStateException("Main ADMIN user cannot change username.");
                }

                String oldRole = existing.getRole() == null ? "" : existing.getRole();
                String newRole = u.getRole() == null ? "" : u.getRole();

                if (!oldRole.equalsIgnoreCase(newRole)) {
                    throw new IllegalStateException("Main ADMIN user cannot change role.");
                }
            }

            // SAFE UPDATE: Only update fields, keep existing password if new is null/empty
            existing.setFirstName(u.getFirstName());
            existing.setLastName(u.getLastName());
            existing.setEmail(u.getEmail());
            existing.setPhone(u.getPhone());
            existing.setRole(u.getRole());
            existing.setUsername(u.getUsername());
            existing.setGender(u.getGender());

            if (u.getPassword() != null && !u.getPassword().isBlank()) {
                existing.setPassword(u.getPassword());
            }

            if (u.getInstitution() != null) {
                existing.setInstitution(u.getInstitution());
            }

            em.merge(existing);
            tx.commit();

        } catch (RuntimeException ex) {
            if (tx.isActive())
                tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * Delete user and all related data (grades, attendance, etc).
     * Prevents deletion of the main ADMIN or the last ADMIN user.
     */
    public void deleteUserAndRelations(User user) {
        if (user == null || user.getId() == null)
            return;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            User managed = em.find(User.class, user.getId());
            if (managed == null) {
                tx.commit();
                return;
            }

            // 1) Block deleting main admin
            if (isMainAdmin(managed)) {
                throw new IllegalStateException("Main ADMIN user cannot be deleted.");
            }

            // 2) Block deleting the last ADMIN
            if (managed.getRole() != null && managed.getRole().equalsIgnoreCase("ADMIN")) {
                int instId = managed.getInstitution() != null ? managed.getInstitution().getId() : -1;
                long adminCount = countAdmins(em, instId);
                if (adminCount <= 1) {
                    throw new IllegalStateException("Cannot delete the last ADMIN user.");
                }
            }

            // 3) Delete relations (Clean up database)

            // Attendance (student)
            em.createQuery("delete from Attendance a where a.student = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Grades (as student)
            em.createQuery("delete from Grade g where g.student = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Grades (as teacher)
            if (managed.getUsername() != null) {
                em.createQuery("delete from Grade g where lower(g.teacherUsername) = :uname")
                        .setParameter("uname", managed.getUsername().toLowerCase())
                        .executeUpdate();
            }

            // SubjectAssignment (as student or teacher)
            if (managed.getUsername() != null) {
                em.createQuery("delete from SubjectAssignment sa where lower(sa.username) = :uname")
                        .setParameter("uname", managed.getUsername().toLowerCase())
                        .executeUpdate();
            }

            // Bookings (as creator)
            if (managed.getUsername() != null) {
                em.createQuery("delete from Booking b where b.createdBy = :uname")
                        .setParameter("uname", managed.getUsername())
                        .executeUpdate();
            }

            // ProgramMember (student/teacher memberships)
            em.createQuery("delete from ProgramMember pm where pm.user = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Chat: delete reactions by this user
            em.createQuery("delete from ChatReaction cr where cr.user = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Chat: delete reactions ON this user's messages (others' reactions)
            em.createQuery("delete from ChatReaction cr where cr.message.sender = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Chat: delete attachments on this user's messages
            em.createQuery("delete from ChatAttachment ca where ca.message.sender = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Chat: delete messages (now safe — reactions/attachments removed)
            em.createQuery("delete from ChatMessage cm where cm.sender = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Chat memberships
            em.createQuery("delete from ChatMember cm where cm.user = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // User status
            em.createQuery("delete from UserStatus us where us.user = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // Admission applications (as student)
            em.createQuery("delete from Application a where a.student = :u")
                    .setParameter("u", managed)
                    .executeUpdate();

            // 4) Delete the user
            em.remove(managed);

            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive())
                tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}