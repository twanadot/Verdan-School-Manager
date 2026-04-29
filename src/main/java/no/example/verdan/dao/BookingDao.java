package no.example.verdan.dao;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import no.example.verdan.model.Booking;
import no.example.verdan.model.BookingStatus;
import no.example.verdan.model.Room;
import no.example.verdan.util.HibernateUtil;

public class BookingDao extends BaseDao<Booking> {

    public BookingDao() {
        super(Booking.class);
    }

    /**
     * Eagerly fetches all bookings and their assigned rooms to avoid LazyInitializationException.
     */
    public List<Booking> findAllWithRooms(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct b from Booking b left join fetch b.rooms " +
                    "where b.institution.id = :instId", Booking.class)
                     .setParameter("instId", institutionId)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Eagerly fetches a single booking and its assigned rooms.
     */
    public Booking findByIdWithRooms(int id, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select b from Booking b left join fetch b.rooms " +
                    "where b.id = :id and b.institution.id = :instId", Booking.class)
                     .setParameter("id", id)
                     .setParameter("instId", institutionId)
                     .getResultStream().findFirst().orElse(null);
        } finally {
            em.close();
        }
    }

    /**
     * Find all bookings for a given room that overlap with a time interval.
     *
     * Overlap logic:
     * booking.start < end
     * AND
     * booking.end   > start
     */
    public List<Booking> findForRoomBetween(Integer roomId,
                                            LocalDateTime start,
                                            LocalDateTime end,
                                            int institutionId) {
        if (roomId == null || start == null || end == null) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct b " +
                    "from Booking b join b.rooms r " +
                    "where r.id = :roomId " +
                    "  and b.startDateTime < :end " +
                    "  and b.endDateTime   > :start " +
                    "  and b.institution.id = :instId",
                    Booking.class
            )
            .setParameter("roomId", roomId)
            .setParameter("start", start)
            .setParameter("end",   end)
            .setParameter("instId", institutionId)
            .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Find all bookings for a given room and subject code (regardless of date/time).
     */
    public List<Booking> findForRoomAndSubject(Integer roomId, String subjectCode, int institutionId) {
        if (roomId == null || subjectCode == null || subjectCode.isBlank()) {
            return List.of();
        }

        String subjLower = subjectCode.trim().toLowerCase();

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct b " +
                    "from Booking b join b.rooms r " +
                    "where r.id = :roomId " +
                    "  and lower(b.subject) = :subj " +
                    "  and b.institution.id = :instId",
                    Booking.class
            )
            .setParameter("roomId", roomId)
            .setParameter("subj", subjLower)
            .setParameter("instId", institutionId)
            .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Save booking with status CONFIRMED if the room is available.
     *
     * @return true  → save OK
     * false → conflict (booking not saved)
     */
    public boolean saveConfirmedIfAvailable(Booking booking, Integer mainRoomId, int institutionId) {
        if (booking == null) {
            throw new IllegalArgumentException("booking is null");
        }
        if (mainRoomId == null) {
            throw new IllegalArgumentException("mainRoomId is null");
        }
        if (booking.getStartDateTime() == null || booking.getEndDateTime() == null) {
            throw new IllegalArgumentException("start/end datetime must be set on booking");
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            // Check if there is an overlapping booking in this room
            List<Booking> conflicts = em.createQuery(
                    "select distinct b " +
                    "from Booking b join b.rooms r " +
                    "where r.id = :roomId " +
                    "  and b.startDateTime < :end " +
                    "  and b.endDateTime   > :start " +
                    "  and b.institution.id = :instId",
                    Booking.class
            )
            .setParameter("roomId", mainRoomId)
            .setParameter("start", booking.getStartDateTime())
            .setParameter("end",   booking.getEndDateTime())
            .setParameter("instId", institutionId)
            .getResultList();

            if (!conflicts.isEmpty()) {
                tx.rollback();
                return false;
            }

            // Set institution on booking before persisting
            if (booking.getInstitution() == null) {
                no.example.verdan.model.Institution inst = em.find(no.example.verdan.model.Institution.class, institutionId);
                booking.setInstitution(inst);
            }

            // Find the room and add it to booking.rooms
            Room room = em.find(Room.class, mainRoomId);
            if (room == null) {
                tx.rollback();
                throw new IllegalArgumentException("No room found with id " + mainRoomId);
            }

            if (!booking.getRooms().contains(room)) {
                booking.getRooms().add(room);
            }

            booking.setStatus(BookingStatus.CONFIRMED);

            if (booking.getId() == null) {
                em.persist(booking);
            } else {
                booking = em.merge(booking);
            }

            tx.commit();
            return true;

        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * Delete ONE booking safely (also cleans up the many-to-many relationship).
     */
    public void deleteBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Booking managed = em.find(Booking.class, booking.getId());
            if (managed != null) {
                // Clean up join table (booking_rooms)
                managed.getRooms().clear();
                em.remove(managed);
            }

            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * Delete booking based on ID.
     */
    public void deleteBookingById(Integer id) {
        if (id == null) return;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Booking managed = em.find(Booking.class, id);
            if (managed != null) {
                managed.getRooms().clear();
                em.remove(managed);
            }

            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }

    /**
     * Delete ALL bookings associated with a given subject code.
     * Used when a subject is deleted in SubjectController.onDelete().
     */
    public void deleteBookingsForSubject(String subjectCode, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return;
        }

        String codeLower = subjectCode.trim().toLowerCase();

        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            // First fetch all bookings for this code
            List<Booking> list = em.createQuery(
                    "select b from Booking b " +
                    "where lower(b.subject) = :code " +
                    "  and b.institution.id = :instId",
                    Booking.class
            )
            .setParameter("code", codeLower)
            .setParameter("instId", institutionId)
            .getResultList();

            for (Booking b : list) {
                Booking managed = em.find(Booking.class, b.getId());
                if (managed != null) {
                    managed.getRooms().clear(); // cleans up booking_rooms
                    em.remove(managed);
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
}