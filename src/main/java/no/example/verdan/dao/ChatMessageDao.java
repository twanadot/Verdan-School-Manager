package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.ChatMessage;
import no.example.verdan.util.HibernateUtil;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageDao extends BaseDao<ChatMessage> {

    public ChatMessageDao() { super(ChatMessage.class); }

    /**
     * Get messages in a room with eager-loaded sender, attachments, and reply-to.
     * Uses JOIN FETCH to eliminate lazy-loading N+1 queries.
     */
    public List<ChatMessage> findByRoom(int roomId, Integer afterId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            String jpql;
            if (afterId != null && afterId > 0) {
                jpql = "SELECT DISTINCT m FROM ChatMessage m " +
                       "JOIN FETCH m.sender s " +
                       "LEFT JOIN FETCH s.institution " +
                       "LEFT JOIN FETCH m.attachments " +
                       "LEFT JOIN FETCH m.replyTo rt " +
                       "LEFT JOIN FETCH rt.sender " +
                       "WHERE m.chatRoom.id = :roomId AND m.id > :afterId " +
                       "ORDER BY m.sentAt ASC";
                return em.createQuery(jpql, ChatMessage.class)
                    .setParameter("roomId", roomId)
                    .setParameter("afterId", afterId)
                    .getResultList();
            }

            jpql = "SELECT DISTINCT m FROM ChatMessage m " +
                   "JOIN FETCH m.sender s " +
                   "LEFT JOIN FETCH s.institution " +
                   "LEFT JOIN FETCH m.attachments " +
                   "LEFT JOIN FETCH m.replyTo rt " +
                   "LEFT JOIN FETCH rt.sender " +
                   "WHERE m.chatRoom.id = :roomId " +
                   "ORDER BY m.sentAt ASC";
            return em.createQuery(jpql, ChatMessage.class)
                .setParameter("roomId", roomId)
                .setMaxResults(100)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Get the latest message in a room (for room list preview). */
    public ChatMessage findLatestInRoom(int roomId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<ChatMessage> results = em.createQuery(
                "SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.chatRoom.id = :roomId ORDER BY m.sentAt DESC", ChatMessage.class)
                .setParameter("roomId", roomId)
                .setMaxResults(1)
                .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } finally {
            em.close();
        }
    }

    /**
     * Bulk-fetch the latest message for multiple rooms in ONE query.
     * Returns a map of roomId → latest ChatMessage.
     */
    public Map<Integer, ChatMessage> findLatestForRooms(List<Integer> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Collections.emptyMap();

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            // Get max message ID per room
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createQuery(
                "SELECT m.chatRoom.id, MAX(m.id) FROM ChatMessage m " +
                "WHERE m.chatRoom.id IN :ids GROUP BY m.chatRoom.id")
                .setParameter("ids", roomIds)
                .getResultList();

            if (rows.isEmpty()) return Collections.emptyMap();

            List<Integer> maxIds = rows.stream().map(r -> ((Number) r[1]).intValue()).toList();
            Map<Integer, Integer> roomToMaxId = new HashMap<>();
            for (Object[] r : rows) {
                roomToMaxId.put(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            }

            // Fetch those messages with sender
            List<ChatMessage> messages = em.createQuery(
                "SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.id IN :ids", ChatMessage.class)
                .setParameter("ids", maxIds)
                .getResultList();

            Map<Integer, ChatMessage> result = new HashMap<>();
            for (ChatMessage m : messages) {
                result.put(m.getChatRoom().getId(), m);
            }
            return result;
        } finally {
            em.close();
        }
    }

    /** Count unread messages in a room for a user (messages after their lastReadAt). */
    public long countUnreadInRoom(int roomId, LocalDateTime lastReadAt) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            if (lastReadAt == null) {
                return em.createQuery(
                    "SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId", Long.class)
                    .setParameter("roomId", roomId)
                    .getSingleResult();
            }
            return em.createQuery(
                "SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.sentAt > :lastRead", Long.class)
                .setParameter("roomId", roomId)
                .setParameter("lastRead", lastReadAt)
                .getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * Bulk count unread messages for multiple rooms in fewer queries.
     * Returns map of roomId → unread count.
     */
    public Map<Integer, Long> countUnreadForRooms(List<Integer> roomIds, Map<Integer, LocalDateTime> lastReadMap) {
        if (roomIds == null || roomIds.isEmpty()) return Collections.emptyMap();

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Map<Integer, Long> result = new HashMap<>();

            // Split into rooms with and without lastReadAt
            List<Integer> withoutRead = roomIds.stream()
                .filter(id -> lastReadMap.get(id) == null).toList();
            List<Integer> withRead = roomIds.stream()
                .filter(id -> lastReadMap.get(id) != null).toList();

            // Rooms without lastReadAt: count ALL messages
            if (!withoutRead.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Object[]> counts = em.createQuery(
                    "SELECT m.chatRoom.id, COUNT(m) FROM ChatMessage m " +
                    "WHERE m.chatRoom.id IN :ids GROUP BY m.chatRoom.id")
                    .setParameter("ids", withoutRead)
                    .getResultList();
                for (Object[] row : counts) {
                    result.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
                }
            }

            // Rooms with lastReadAt: count per room (still need individual timestamps)
            for (int roomId : withRead) {
                LocalDateTime lastRead = lastReadMap.get(roomId);
                Long count = em.createQuery(
                    "SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.sentAt > :lastRead", Long.class)
                    .setParameter("roomId", roomId)
                    .setParameter("lastRead", lastRead)
                    .getSingleResult();
                result.put(roomId, count);
            }

            // Fill missing with 0
            for (int id : roomIds) {
                result.putIfAbsent(id, 0L);
            }

            return result;
        } finally {
            em.close();
        }
    }

    /** Mark read: update lastReadAt for a member in a room. */
    public void markRead(int roomId, int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("UPDATE ChatMember m SET m.lastReadAt = :now WHERE m.chatRoom.id = :roomId AND m.user.id = :userId")
                .setParameter("now", LocalDateTime.now())
                .setParameter("roomId", roomId)
                .setParameter("userId", userId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Soft-delete a message. */
    public void softDelete(int messageId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("UPDATE ChatMessage m SET m.deleted = true, m.content = '' WHERE m.id = :id")
                .setParameter("id", messageId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Edit a message's content. */
    public void editContent(int messageId, String newContent) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("UPDATE ChatMessage m SET m.content = :content, m.editedAt = :now WHERE m.id = :id")
                .setParameter("content", newContent)
                .setParameter("now", LocalDateTime.now())
                .setParameter("id", messageId)
                .executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}
