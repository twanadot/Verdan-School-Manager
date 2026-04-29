package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.UserStatus;
import no.example.verdan.model.User;
import no.example.verdan.util.HibernateUtil;

import java.time.LocalDateTime;
import java.util.List;

public class UserStatusDao {

    /** Update or create heartbeat for a user. */
    public void heartbeat(int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            UserStatus status = em.find(UserStatus.class, userId);
            if (status != null) {
                status.setLastSeen(LocalDateTime.now());
                em.merge(status);
            } else {
                User user = em.find(User.class, userId);
                if (user != null) {
                    status = new UserStatus(user);
                    em.persist(status);
                }
            }
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Get status for a list of user IDs. */
    public List<UserStatus> getStatusForUsers(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT s FROM UserStatus s WHERE s.userId IN :ids", UserStatus.class)
                .setParameter("ids", userIds)
                .getResultList();
        } finally {
            em.close();
        }
    }
}
