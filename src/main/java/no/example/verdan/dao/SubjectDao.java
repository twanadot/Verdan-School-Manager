package no.example.verdan.dao;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import no.example.verdan.model.Subject;
import no.example.verdan.util.HibernateUtil;

public class SubjectDao extends BaseDao<Subject> {

    public SubjectDao() {
        super(Subject.class);
    }

    public List<Subject> search(String query, int institutionId) {
        if (query == null || query.isBlank()) {
            return findAll(institutionId);
        }
        String q = "%" + query.toLowerCase().trim() + "%";
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT s FROM Subject s " +
                    "WHERE (LOWER(s.name)       LIKE :q " +
                    "   OR LOWER(s.code)        LIKE :q " +
                    "   OR LOWER(s.description) LIKE :q) " +
                    "  AND s.institution.id = :instId",
                    Subject.class)
                .setParameter("q", q)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find subject by code. */
    public Subject findByCode(String code, int institutionId) {
        if (code == null || code.isBlank()) return null;
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Subject> res = em.createQuery(
                    "SELECT s FROM Subject s WHERE LOWER(s.code) = LOWER(:c) AND s.institution.id = :instId",
                    Subject.class)
                .setParameter("c", code.trim())
                .setParameter("instId", institutionId)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /** Global find by code (for internal use if needed) */
    public Subject findByCode(String code) {
        if (code == null || code.isBlank()) return null;
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Subject> res = em.createQuery(
                    "SELECT s FROM Subject s WHERE LOWER(s.code) = LOWER(:c)",
                    Subject.class)
                .setParameter("c", code.trim())
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /** find subjects from list. */
    public List<Subject> findByCodes(List<String> codes, int institutionId) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.toLowerCase())
                .toList();

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "SELECT s FROM Subject s " +
                    "WHERE LOWER(s.code) IN :codes AND s.institution.id = :instId",
                    Subject.class)
                .setParameter("codes", normalized)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }
}
