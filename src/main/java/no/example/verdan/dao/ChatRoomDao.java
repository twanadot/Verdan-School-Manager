package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import no.example.verdan.model.ChatRoom;
import no.example.verdan.model.ChatMember;
import no.example.verdan.model.User;
import no.example.verdan.util.HibernateUtil;

import java.util.List;

public class ChatRoomDao extends BaseDao<ChatRoom> {

    public ChatRoomDao() { super(ChatRoom.class); }

    /** Find all rooms a user is a member of (excluding hidden ones), ordered by most recent activity. */
    public List<ChatRoom> findRoomsForUser(int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT DISTINCT r FROM ChatRoom r JOIN r.members m " +
                "WHERE m.user.id = :userId AND m.hidden = false " +
                "ORDER BY r.createdAt DESC", ChatRoom.class)
                .setParameter("userId", userId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Hide or unhide a room for a specific user. */
    public void setRoomHidden(int roomId, int userId, boolean hidden) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("UPDATE ChatMember m SET m.hidden = :hidden WHERE m.chatRoom.id = :roomId AND m.user.id = :userId")
                .setParameter("hidden", hidden)
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

    /** Find an existing direct (1:1) room between two users. */
    public ChatRoom findDirectRoom(int userId1, int userId2) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT r FROM ChatRoom r " +
                "WHERE r.isGroup = false " +
                "AND (SELECT COUNT(m) FROM ChatMember m WHERE m.chatRoom = r) = 2 " +
                "AND EXISTS (SELECT m1 FROM ChatMember m1 WHERE m1.chatRoom = r AND m1.user.id = :u1) " +
                "AND EXISTS (SELECT m2 FROM ChatMember m2 WHERE m2.chatRoom = r AND m2.user.id = :u2)", ChatRoom.class)
                .setParameter("u1", userId1)
                .setParameter("u2", userId2)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }

    /** Create a room and add members in one transaction. */
    public ChatRoom createWithMembers(ChatRoom room, List<User> memberUsers) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(room);
            for (User u : memberUsers) {
                ChatMember member = new ChatMember(room, u);
                em.persist(member);
            }
            tx.commit();
            return room;
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Add a single member to a room. */
    public void addMember(ChatRoom room, User user) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(new ChatMember(room, user));
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /** Remove a member from a room. */
    public void removeMember(int roomId, int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM ChatMember m WHERE m.chatRoom.id = :roomId AND m.user.id = :userId")
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

    /** Check if a user is a member of a room. */
    public boolean isMember(int roomId, int userId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long count = em.createQuery(
                "SELECT COUNT(m) FROM ChatMember m WHERE m.chatRoom.id = :roomId AND m.user.id = :userId", Long.class)
                .setParameter("roomId", roomId)
                .setParameter("userId", userId)
                .getSingleResult();
            return count != null && count > 0;
        } finally {
            em.close();
        }
    }

    /** Get members of a room. */
    public List<ChatMember> getMembers(int roomId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "SELECT m FROM ChatMember m WHERE m.chatRoom.id = :roomId", ChatMember.class)
                .setParameter("roomId", roomId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** Delete a room and all its data (members, messages, reactions, attachments). */
    public void deleteRoom(int roomId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            // Delete reactions on messages in this room
            em.createQuery("DELETE FROM ChatReaction r WHERE r.message.id IN (SELECT m.id FROM ChatMessage m WHERE m.chatRoom.id = :roomId)")
                .setParameter("roomId", roomId).executeUpdate();
            // Delete attachments on messages in this room
            em.createQuery("DELETE FROM ChatAttachment a WHERE a.message.id IN (SELECT m.id FROM ChatMessage m WHERE m.chatRoom.id = :roomId)")
                .setParameter("roomId", roomId).executeUpdate();
            // Delete messages
            em.createQuery("DELETE FROM ChatMessage m WHERE m.chatRoom.id = :roomId")
                .setParameter("roomId", roomId).executeUpdate();
            // Delete members
            em.createQuery("DELETE FROM ChatMember m WHERE m.chatRoom.id = :roomId")
                .setParameter("roomId", roomId).executeUpdate();
            // Delete room
            em.createQuery("DELETE FROM ChatRoom r WHERE r.id = :roomId")
                .setParameter("roomId", roomId).executeUpdate();
            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}
