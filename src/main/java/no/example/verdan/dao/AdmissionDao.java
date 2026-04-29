package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.AdmissionPeriod;
import no.example.verdan.model.AdmissionRequirement;
import no.example.verdan.model.Application;
import no.example.verdan.util.HibernateUtil;

public class AdmissionDao extends BaseDao<AdmissionPeriod> {

    public AdmissionDao() {
        super(AdmissionPeriod.class);
    }

    // ── Admission Periods ──

    /** Find all periods for an institution (as receiving institution). */
    public List<AdmissionPeriod> findByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT ap FROM AdmissionPeriod ap WHERE ap.institution.id = :instId ORDER BY ap.startDate DESC",
                    AdmissionPeriod.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find open periods that accept students from a specific level. */
    public List<AdmissionPeriod> findOpenByFromLevel(String fromLevel) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT ap FROM AdmissionPeriod ap JOIN FETCH ap.institution " +
                    "WHERE ap.fromLevel = :fl AND ap.status = 'OPEN' ORDER BY ap.endDate ASC",
                    AdmissionPeriod.class)
                .setParameter("fl", fromLevel)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find ALL open periods across all institutions (for the portal). */
    public List<AdmissionPeriod> findAllOpen() {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT ap FROM AdmissionPeriod ap JOIN FETCH ap.institution " +
                    "WHERE ap.status = 'OPEN' ORDER BY ap.institution.name ASC, ap.endDate ASC",
                    AdmissionPeriod.class)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find ALL requirements across all open periods (for the portal). */
    public List<AdmissionRequirement> findAllOpenRequirements() {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT ar FROM AdmissionRequirement ar " +
                    "JOIN FETCH ar.program JOIN FETCH ar.period JOIN FETCH ar.period.institution " +
                    "WHERE ar.period.status = 'OPEN' " +
                    "ORDER BY ar.period.institution.name, ar.program.name",
                    AdmissionRequirement.class)
                .getResultList();
        } finally { em.close(); }
    }

    // ── Requirements ──

    /** Find all requirements for a period. */
    public List<AdmissionRequirement> findRequirements(int periodId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT ar FROM AdmissionRequirement ar JOIN FETCH ar.program WHERE ar.period.id = :pid",
                    AdmissionRequirement.class)
                .setParameter("pid", periodId)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find requirement for a specific program in a period. */
    public AdmissionRequirement findRequirement(int periodId, int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<AdmissionRequirement> res = em.createQuery(
                    "SELECT ar FROM AdmissionRequirement ar WHERE ar.period.id = :pid AND ar.program.id = :progId",
                    AdmissionRequirement.class)
                .setParameter("pid", periodId)
                .setParameter("progId", programId)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally { em.close(); }
    }

    public void saveRequirement(AdmissionRequirement req) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (req.getId() == 0) em.persist(req); else em.merge(req);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }

    // ── Applications ──

    /** Find all applications by a student across all periods. */
    public List<Application> findApplicationsByStudent(int studentId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT a FROM Application a JOIN FETCH a.period JOIN FETCH a.program " +
                    "WHERE a.student.id = :sid ORDER BY a.period.id DESC, a.priority ASC",
                    Application.class)
                .setParameter("sid", studentId)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find all applications for a specific period. */
    public List<Application> findApplicationsByPeriod(int periodId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT a FROM Application a JOIN FETCH a.student JOIN FETCH a.program " +
                    "WHERE a.period.id = :pid ORDER BY a.program.id, a.priority, a.gpaSnapshot DESC",
                    Application.class)
                .setParameter("pid", periodId)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find applications for a specific program in a period. */
    public List<Application> findApplicationsByPeriodAndProgram(int periodId, int programId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT a FROM Application a JOIN FETCH a.student " +
                    "WHERE a.period.id = :pid AND a.program.id = :progId " +
                    "ORDER BY a.priority ASC, a.gpaSnapshot DESC",
                    Application.class)
                .setParameter("pid", periodId)
                .setParameter("progId", programId)
                .getResultList();
        } finally { em.close(); }
    }

    /** Find applications by a student in a specific period. */
    public List<Application> findStudentApplicationsInPeriod(int studentId, int periodId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT a FROM Application a JOIN FETCH a.program " +
                    "WHERE a.student.id = :sid AND a.period.id = :pid ORDER BY a.priority ASC",
                    Application.class)
                .setParameter("sid", studentId)
                .setParameter("pid", periodId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveApplication(Application app) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (app.getId() == 0) em.persist(app); else em.merge(app);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }

    /** Delete a single application (e.g. cleaning up withdrawn apps before reapply). */
    public void deleteApplication(Application app) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Application managed = em.find(Application.class, app.getId());
            if (managed != null) em.remove(managed);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }

    /** Batch update applications (used during processing). */
    public void updateApplications(List<Application> apps) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            for (Application a : apps) { em.merge(a); }
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }

    /** Delete all applications for a period. */
    public void deleteApplicationsForPeriod(int periodId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM Application a WHERE a.period.id = :pid")
                .setParameter("pid", periodId).executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }

    /** Delete all requirements for a period. */
    public void deleteRequirementsForPeriod(int periodId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM AdmissionRequirement ar WHERE ar.period.id = :pid")
                .setParameter("pid", periodId).executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback(); throw ex;
        } finally { em.close(); }
    }
}
