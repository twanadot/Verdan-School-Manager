package no.example.verdan.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.Institution;
import no.example.verdan.util.HibernateUtil;

public class InstitutionDao extends BaseDao<Institution> {
    public InstitutionDao() {
        super(Institution.class);
    }

    /** Find all active institutions (excludes soft-deleted). */
    public List<Institution> findAllActive() {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery("from Institution i where i.active = true order by i.name", Institution.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /** Find an active institution by exact name (for uniqueness checks). */
    public Institution findByName(String name) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            var list = em.createQuery("from Institution i where i.name = :name and i.active = true", Institution.class)
                    .setParameter("name", name.trim())
                    .getResultList();
            return list.isEmpty() ? null : list.get(0);
        } finally {
            em.close();
        }
    }

    /** Soft-delete: mark as inactive instead of removing from DB. */
    public void softDelete(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Institution inst = em.find(Institution.class, id);
            if (inst == null) {
                throw new IllegalArgumentException("Institution not found");
            }
            inst.setActive(false);
            em.merge(inst);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Update institution name/location/level/ownership. */
    public Institution updateInstitution(int id, String name, String location, String level, String ownership) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Institution inst = em.find(Institution.class, id);
            if (inst == null) {
                throw new IllegalArgumentException("Institution not found");
            }
            if (name != null && !name.isBlank()) {
                inst.setName(name.trim());
            }
            if (location != null) {
                inst.setLocation(location.trim());
            }
            if (level != null) {
                inst.setLevel(level.trim());
            }
            if (ownership != null) {
                inst.setOwnership(ownership.trim().toUpperCase());
            }
            em.merge(inst);
            tx.commit();
            return inst;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Find all inactive (soft-deleted) institutions. */
    public List<Institution> findAllInactive() {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery("from Institution i where i.active = false order by i.name", Institution.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    /** Reactivate a soft-deleted institution. */
    public void reactivate(int id) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Institution inst = em.find(Institution.class, id);
            if (inst == null) {
                throw new IllegalArgumentException("Institution not found");
            }
            inst.setActive(true);
            em.merge(inst);
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}

