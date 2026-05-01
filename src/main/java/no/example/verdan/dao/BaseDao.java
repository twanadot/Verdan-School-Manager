package no.example.verdan.dao;

import java.util.List;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.util.HibernateUtil;

public abstract class BaseDao<T> {
  private final Class<T> type;
  protected BaseDao(Class<T> type){ this.type = type; }

  public T find(int id){
    EntityManager em = HibernateUtil.emf().createEntityManager();
    try { return em.find(type, id); } finally { em.close(); }
  }

  public List<T> findAll() {
    EntityManager em = HibernateUtil.emf().createEntityManager();
    try {
      return em.createQuery("from " + type.getSimpleName(), type).getResultList();
    } finally {
      em.close();
    }
  }

  public List<T> findAll(int institutionId) {
    EntityManager em = HibernateUtil.emf().createEntityManager();
    try {
      return em.createQuery("select e from " + type.getSimpleName() + " e join fetch e.institution where e.institution.id = :instId", type)
          .setParameter("instId", institutionId)
          .getResultList();
    } finally {
      em.close();
    }
  }

  public T find(int id, int institutionId) {
    EntityManager em = HibernateUtil.emf().createEntityManager();
    try {
      return em.createQuery("select e from " + type.getSimpleName() + " e where e.id = :id and e.institution.id = :instId", type)
          .setParameter("id", id)
          .setParameter("instId", institutionId)
          .getSingleResult();
    } catch (Exception e) {
      return null;
    } finally {
      em.close();
    }
  }

  public void save(T entity){ withTx(em -> em.persist(entity)); }
  public void update(T entity){ withTx(em -> em.merge(entity)); }
  public void delete(T entity){ withTx(em -> em.remove(em.contains(entity) ? entity : em.merge(entity))); }

  private void withTx(Consumer<EntityManager> work){
    EntityManager em = HibernateUtil.emf().createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try { tx.begin(); work.accept(em); tx.commit(); }
    catch(RuntimeException ex){ if(tx.isActive()) tx.rollback(); throw ex; }
    finally { em.close(); }
  }
}
