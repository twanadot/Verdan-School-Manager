package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.util.HibernateUtil;

public class ProgramMemberDao extends BaseDao<ProgramMember> {

    public ProgramMemberDao() {
        super(ProgramMember.class);
    }

    /** Find all members of a program. */
    public List<ProgramMember> findByProgram(int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.user WHERE pm.program.id = :pid ORDER BY pm.role, pm.user.lastName",
                    ProgramMember.class)
                .setParameter("pid", programId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find all programs a user belongs to. */
    public List<ProgramMember> findByUser(int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.program p LEFT JOIN FETCH p.institution WHERE pm.user.id = :uid",
                    ProgramMember.class)
                .setParameter("uid", userId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Check if user is already a member of a program. */
    public ProgramMember findByProgramAndUser(int programId, int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<ProgramMember> res = em.createQuery(
                    "SELECT pm FROM ProgramMember pm WHERE pm.program.id = :pid AND pm.user.id = :uid",
                    ProgramMember.class)
                .setParameter("pid", programId)
                .setParameter("uid", userId)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /** Find all active (non-graduated) members by institution and year level. Used for promotion. */
    public List<ProgramMember> findActiveByInstitutionAndYear(int institutionId, String yearLevel) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.user JOIN FETCH pm.program " +
                    "WHERE pm.program.institution.id = :instId AND pm.yearLevel = :year AND pm.graduated = false",
                    ProgramMember.class)
                .setParameter("instId", institutionId)
                .setParameter("year", yearLevel)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find all graduated members by institution and year level. Used for undo promotion. */
    public List<ProgramMember> findGraduatedByInstitutionAndYear(int institutionId, String yearLevel) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.user JOIN FETCH pm.program " +
                    "WHERE pm.program.institution.id = :instId AND pm.yearLevel = :year AND pm.graduated = true",
                    ProgramMember.class)
                .setParameter("instId", institutionId)
                .setParameter("year", yearLevel)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find all active members in a program (non-graduated). */
    public List<ProgramMember> findActiveByProgram(int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.user WHERE pm.program.id = :pid AND pm.graduated = false ORDER BY pm.role, pm.user.lastName",
                    ProgramMember.class)
                .setParameter("pid", programId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Delete membership. */
    public void deleteMembership(int programId, int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM ProgramMember pm WHERE pm.program.id = :pid AND pm.user.id = :uid")
                .setParameter("pid", programId)
                .setParameter("uid", userId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Delete all memberships for a program (used when deleting a program). */
    public void deleteAllForProgram(int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM ProgramMember pm WHERE pm.program.id = :pid")
                .setParameter("pid", programId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Find all graduated members across all programs in an institution. */
    public List<ProgramMember> findGraduatedByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT pm FROM ProgramMember pm JOIN FETCH pm.user JOIN FETCH pm.program " +
                    "WHERE pm.program.institution.id = :instId AND pm.graduated = true " +
                    "ORDER BY pm.user.lastName, pm.user.firstName",
                    ProgramMember.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }
}
