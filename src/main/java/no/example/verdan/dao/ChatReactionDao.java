package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.ChatReaction;
import no.example.verdan.util.HibernateUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatReactionDao extends BaseDao<ChatReaction> {

    public ChatReactionDao() { super(ChatReaction.class); }

    /** Find all reactions for a message. */
    public List<ChatReaction> findByMessage(int messageId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT r FROM ChatReaction r JOIN FETCH r.user WHERE r.message.id = :messageId", ChatReaction.class)
                .setParameter("messageId", messageId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Batch-fetch all reactions for multiple messages in ONE query.
     * Returns a map of messageId → list of reactions.
     * This eliminates the N+1 problem when loading messages for a room.
     */
    public Map<Integer, List<ChatReaction>> findByMessages(List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Collections.emptyMap();

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<ChatReaction> all = em.createQuery(
                "SELECT r FROM ChatReaction r " +
                "JOIN FETCH r.user " +
                "WHERE r.message.id IN :ids", ChatReaction.class)
                .setParameter("ids", messageIds)
                .getResultList();

            return all.stream().collect(Collectors.groupingBy(r -> r.getMessage().getId()));
        } finally {
            em.close();
        }
    }

    /** Toggle a reaction: if exists remove it, if not add it. Returns true if added. */
    public boolean toggle(int messageId, int userId, String emoji) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<ChatReaction> existing = em.createQuery(
                "SELECT r FROM ChatReaction r WHERE r.message.id = :mid AND r.user.id = :uid AND r.emoji = :emoji", ChatReaction.class)
                .setParameter("mid", messageId)
                .setParameter("uid", userId)
                .setParameter("emoji", emoji)
                .getResultList();

            boolean added;
            if (!existing.isEmpty()) {
                em.remove(em.contains(existing.get(0)) ? existing.get(0) : em.merge(existing.get(0)));
                added = false;
            } else {
                ChatReaction r = new ChatReaction();
                r.setMessage(em.getReference(no.example.verdan.model.ChatMessage.class, messageId));
                r.setUser(em.getReference(no.example.verdan.model.User.class, userId));
                r.setEmoji(emoji);
                em.persist(r);
                added = true;
            }
            tx.commit();
            return added;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}
