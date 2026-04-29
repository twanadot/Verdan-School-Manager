package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.*;
import no.example.verdan.util.HibernateUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * DAO for all portal entities: announcements, comments, folders, files, submissions.
 */
public class PortalDao {

    // ── Announcements ──

    public PortalAnnouncement findAnnouncement(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try { return em.find(PortalAnnouncement.class, id); } finally { em.close(); }
    }

    /** Find with eager-loaded author and program (for response mapping). */
    public PortalAnnouncement findAnnouncementFull(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<PortalAnnouncement> results = em.createQuery(
                "SELECT a FROM PortalAnnouncement a LEFT JOIN FETCH a.author LEFT JOIN FETCH a.program WHERE a.id = :id",
                PortalAnnouncement.class)
                .setParameter("id", id)
                .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } finally { em.close(); }
    }

    public List<PortalAnnouncement> findAnnouncementsByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT a FROM PortalAnnouncement a LEFT JOIN FETCH a.author LEFT JOIN FETCH a.program " +
                "WHERE a.institution.id = :instId ORDER BY a.pinned DESC, a.createdAt DESC",
                PortalAnnouncement.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveAnnouncement(PortalAnnouncement a) { withTx(em -> em.persist(a)); }
    public void updateAnnouncement(PortalAnnouncement a) { withTx(em -> em.merge(a)); }
    public void deleteAnnouncement(PortalAnnouncement a) { withTx(em -> em.remove(em.contains(a) ? a : em.merge(a))); }

    // ── Comments ──

    public PortalComment findComment(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try { return em.find(PortalComment.class, id); } finally { em.close(); }
    }

    public List<PortalComment> findCommentsByAnnouncement(int announcementId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT c FROM PortalComment c JOIN FETCH c.author WHERE c.announcement.id = :aId ORDER BY c.createdAt ASC",
                PortalComment.class)
                .setParameter("aId", announcementId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveComment(PortalComment c) { withTx(em -> em.persist(c)); }
    public void deleteComment(PortalComment c) { withTx(em -> em.remove(em.contains(c) ? c : em.merge(c))); }

    // ── Folders ──

    public PortalFolder findFolder(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try { return em.find(PortalFolder.class, id); } finally { em.close(); }
    }

    /** Find with eager-loaded createdBy and program (for response mapping). */
    public PortalFolder findFolderFull(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<PortalFolder> results = em.createQuery(
                "SELECT f FROM PortalFolder f LEFT JOIN FETCH f.createdBy LEFT JOIN FETCH f.program WHERE f.id = :id",
                PortalFolder.class)
                .setParameter("id", id)
                .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } finally { em.close(); }
    }

    public List<PortalFolder> findFoldersByInstitution(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT f FROM PortalFolder f LEFT JOIN FETCH f.createdBy LEFT JOIN FETCH f.program " +
                "WHERE f.institution.id = :instId ORDER BY f.sortOrder ASC, f.createdAt DESC",
                PortalFolder.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveFolder(PortalFolder f) { withTx(em -> em.persist(f)); }
    public void updateFolder(PortalFolder f) { withTx(em -> em.merge(f)); }
    public void deleteFolder(PortalFolder f) { withTx(em -> em.remove(em.contains(f) ? f : em.merge(f))); }

    // ── Files ──

    public PortalFile findFile(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try { return em.find(PortalFile.class, id); } finally { em.close(); }
    }

    public List<PortalFile> findFilesByFolder(int folderId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT f FROM PortalFile f JOIN FETCH f.uploadedBy LEFT JOIN FETCH f.folder WHERE f.folder.id = :fId ORDER BY f.uploadedAt ASC",
                PortalFile.class)
                .setParameter("fId", folderId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveFile(PortalFile f) { withTx(em -> em.persist(f)); }
    public void deleteFile(PortalFile f) { withTx(em -> em.remove(em.contains(f) ? f : em.merge(f))); }

    // ── Submissions ──

    public PortalSubmission findSubmission(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try { return em.find(PortalSubmission.class, id); } finally { em.close(); }
    }

    public List<PortalSubmission> findSubmissionsByFolder(int folderId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT s FROM PortalSubmission s JOIN FETCH s.student JOIN FETCH s.folder WHERE s.folder.id = :fId ORDER BY s.submittedAt DESC",
                PortalSubmission.class)
                .setParameter("fId", folderId)
                .getResultList();
        } finally { em.close(); }
    }

    public List<PortalSubmission> findSubmissionsByStudent(int studentId, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT s FROM PortalSubmission s JOIN FETCH s.folder f JOIN FETCH s.student " +
                "WHERE s.student.id = :sId AND f.institution.id = :instId ORDER BY s.submittedAt DESC",
                PortalSubmission.class)
                .setParameter("sId", studentId)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally { em.close(); }
    }

    public void saveSubmission(PortalSubmission s) { withTx(em -> em.persist(s)); }
    public void updateSubmission(PortalSubmission s) { withTx(em -> em.merge(s)); }

    // ── Transaction helper ──

    private void withTx(Consumer<EntityManager> work) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try { tx.begin(); work.accept(em); tx.commit(); }
        catch (RuntimeException ex) { if (tx.isActive()) tx.rollback(); throw ex; }
        finally { em.close(); }
    }
}
