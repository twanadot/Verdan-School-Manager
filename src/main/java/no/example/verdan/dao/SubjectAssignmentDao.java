package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import no.example.verdan.model.SubjectAssignment;
import no.example.verdan.model.User;
import no.example.verdan.util.HibernateUtil;

public class SubjectAssignmentDao extends BaseDao<SubjectAssignment> {

    public SubjectAssignmentDao() {
        super(SubjectAssignment.class);
    }

    // -------------------------------------------------------------------------
    //  Lookups
    // -------------------------------------------------------------------------

    /**
     * All subject codes (subject) a STUDENT is registered in.
     */
    public List<String> subjectsForStudent(String username, int institutionId) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct sa.subject " +
                    "from SubjectAssignment sa " +
                    "where upper(sa.role) = 'STUDENT' " +
                    "  and lower(sa.username) = lower(:u) " +
                    "  and sa.institution.id = :instId",
                    String.class)
                .setParameter("u", username)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Check if a student is assigned to a subject via program membership.
     * A student in program X has access to all subjects linked to program X.
     */
    public boolean isStudentAssignedViaProgram(String username, String subjectCode, int institutionId) {
        if (username == null || username.isBlank() || subjectCode == null || subjectCode.isBlank()) {
            return false;
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long count = em.createQuery(
                    "select count(ps) " +
                    "from ProgramMember pm " +
                    "  join pm.program p " +
                    "  join p.subjects ps " +
                    "where lower(pm.user.username) = lower(:u) " +
                    "  and pm.graduated = false " +
                    "  and p.institution.id = :instId " +
                    "  and upper(ps.code) = upper(:sub)",
                    Long.class)
                .setParameter("u", username)
                .setParameter("instId", institutionId)
                .setParameter("sub", subjectCode)
                .getSingleResult();
            return count != null && count > 0;
        } finally {
            em.close();
        }
    }

    /**
     * All subject codes (subject) a TEACHER is registered in.
     */
    public List<String> subjectsForTeacher(String teacherUsername, int institutionId) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct sa.subject " +
                    "from SubjectAssignment sa " +
                    "where upper(sa.role) = 'TEACHER' " +
                    "  and lower(sa.username) = lower(:u) " +
                    "  and sa.institution.id = :instId",
                    String.class)
                .setParameter("u", teacherUsername)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * All students (User) for subjects where the given teacher is TEACHER.
     */
    public List<User> studentsForTeacher(String teacherUsername, int institutionId) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct u " +
                    "from SubjectAssignment sas, SubjectAssignment tas, User u " +
                    "where upper(sas.role) = 'STUDENT' " +
                    "  and upper(tas.role) = 'TEACHER' " +
                    "  and sas.subject = tas.subject " +
                    "  and lower(tas.username) = lower(:t) " +
                    "  and sas.institution.id = :instId " +
                    "  and tas.institution.id = :instId " +
                    "  and lower(sas.username) = lower(u.username)",
                    User.class)
                .setParameter("t", teacherUsername)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * All students (User) registered in a given subject (subjectCode).
     */
    public List<User> studentsForSubject(String subjectCode, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct u " +
                    "from SubjectAssignment sa, User u " +
                    "where upper(sa.role) = 'STUDENT' " +
                    "  and sa.subject = :sub " +
                    "  and sa.institution.id = :instId " +
                    "  and lower(sa.username) = lower(u.username) " +
                    "  and exists (" +
                    "    select 1 from ProgramMember pm " +
                    "    where pm.user.id = u.id and pm.graduated = false" +
                    "  )",
                    User.class)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * All teachers (User) registered in a given subject (subjectCode).
     */
    public List<User> teachersForSubject(String subjectCode, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct u " +
                    "from SubjectAssignment sa, User u " +
                    "where upper(sa.role) = 'TEACHER' " +
                    "  and sa.subject = :sub " +
                    "  and sa.institution.id = :instId " +
                    "  and lower(sa.username) = lower(u.username)",
                    User.class)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * All students in ONE subject, for ONE specific teacher.
     */
    public List<User> studentsForSubjectAndTeacher(String subjectCode, String teacherUsername, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()
                || teacherUsername == null || teacherUsername.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct u " +
                    "from SubjectAssignment sas, SubjectAssignment tas, User u " +
                    "where upper(sas.role) = 'STUDENT' " +
                    "  and upper(tas.role) = 'TEACHER' " +
                    "  and sas.subject = tas.subject " +
                    "  and sas.subject = :sub " +
                    "  and sas.institution.id = :instId " +
                    "  and tas.institution.id = :instId " +
                    "  and lower(tas.username) = lower(:t) " +
                    "  and lower(sas.username) = lower(u.username) " +
                    "  and exists (" +
                    "    select 1 from ProgramMember pm " +
                    "    where pm.user.id = u.id and pm.graduated = false" +
                    "  )",
                    User.class)
                .setParameter("sub", subjectCode)
                .setParameter("t", teacherUsername)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    // -------------------------------------------------------------------------
    //  Assignment / Removal (used by SubjectController)
    // -------------------------------------------------------------------------

    /** Add a STUDENT to a subject (subjectCode). */
    public void assignStudentToSubject(String username, String subjectCode, int institutionId) {
        assignUserToSubject(username, subjectCode, "STUDENT", institutionId);
    }

    /** Add a TEACHER to a subject (subjectCode). */
    public void assignTeacherToSubject(String username, String subjectCode, int institutionId) {
        assignUserToSubject(username, subjectCode, "TEACHER", institutionId);
    }

    /** Common helper method to link user to subject. */
    private void assignUserToSubject(String username, String subjectCode, String role, int institutionId) {
        if (username == null || username.isBlank()
                || subjectCode == null || subjectCode.isBlank()
                || role == null || role.isBlank()) {
            return;
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();

            Long count = em.createQuery(
                    "select count(sa) " +
                    "from SubjectAssignment sa " +
                    "where lower(sa.username) = lower(:u) " +
                    "  and sa.subject = :sub " +
                    "  and upper(sa.role) = :r " +
                    "  and sa.institution.id = :instId",
                    Long.class)
                .setParameter("u", username)
                .setParameter("sub", subjectCode)
                .setParameter("r", role.toUpperCase())
                .setParameter("instId", institutionId)
                .getSingleResult();

            if (count == null || count == 0L) {
                SubjectAssignment sa = new SubjectAssignment();
                sa.setUsername(username);
                sa.setSubject(subjectCode);
                sa.setRole(role.toUpperCase());
                
                no.example.verdan.model.Institution inst = new no.example.verdan.model.Institution();
                inst.setId(institutionId);
                sa.setInstitution(inst);
                
                em.persist(sa);
            }

            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * Remove ALL links between a user and a subject
     * (regardless of whether the user is STUDENT or TEACHER in this subject).
     */
    public void removeAssignmentsForUserAndSubject(String username, String subjectCode, int institutionId) {
        if (username == null || username.isBlank()
                || subjectCode == null || subjectCode.isBlank()) {
            return;
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();

            em.createQuery(
                    "delete from SubjectAssignment sa " +
                    "where lower(sa.username) = lower(:u) " +
                    "  and sa.subject = :sub " +
                    "  and sa.institution.id = :instId")
              .setParameter("u", username)
              .setParameter("sub", subjectCode)
              .setParameter("instId", institutionId)
              .executeUpdate();

            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * delete all subject row for a subject).
     */
    public void deleteAssignmentsForSubject(String subjectCode, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return;
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            em.getTransaction().begin();

            em.createQuery(
                    "delete from SubjectAssignment sa " +
                    "where sa.subject = :sub " +
                    "  and sa.institution.id = :instId")
              .setParameter("sub", subjectCode)
              .setParameter("instId", institutionId)
              .executeUpdate();

            em.getTransaction().commit();
        } catch (Exception ex) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw ex;
        } finally {
            em.close();
        }
    }
}
