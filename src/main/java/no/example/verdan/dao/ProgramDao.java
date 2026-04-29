package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.Program;
import no.example.verdan.model.Subject;
import no.example.verdan.util.HibernateUtil;

public class ProgramDao extends BaseDao<Program> {

    public ProgramDao() {
        super(Program.class);
    }

    /** Find all programs for an institution. */
    public List<Program> findByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT DISTINCT p FROM Program p LEFT JOIN FETCH p.subjects WHERE p.institution.id = :instId ORDER BY p.name",
                    Program.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find a program by name within an institution (for uniqueness checks). */
    public Program findByName(String name, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Program> res = em.createQuery(
                    "SELECT p FROM Program p WHERE LOWER(p.name) = LOWER(:name) AND p.institution.id = :instId",
                    Program.class)
                .setParameter("name", name.trim())
                .setParameter("instId", institutionId)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /** Find a program by ID with subjects eagerly loaded. */
    public Program findWithSubjects(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Program> res = em.createQuery(
                    "SELECT p FROM Program p LEFT JOIN FETCH p.subjects WHERE p.id = :id",
                    Program.class)
                .setParameter("id", id)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /** Add a subject to a program. */
    public void addSubject(int programId, int subjectId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createNativeQuery("INSERT IGNORE INTO program_subjects (program_id, subject_id) VALUES (:pid, :sid)")
                .setParameter("pid", programId)
                .setParameter("sid", subjectId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Remove a subject from a program. */
    public void removeSubject(int programId, int subjectId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createNativeQuery("DELETE FROM program_subjects WHERE program_id = :pid AND subject_id = :sid")
                .setParameter("pid", programId)
                .setParameter("sid", subjectId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Delete a program and all its subject-links (not the subjects themselves). */
    public void deleteWithLinks(int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            // Remove all join-table entries
            em.createNativeQuery("DELETE FROM program_subjects WHERE program_id = :pid")
                .setParameter("pid", programId)
                .executeUpdate();
            // Remove the program itself
            Program p = em.find(Program.class, programId);
            if (p != null) em.remove(p);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}
