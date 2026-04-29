package no.example.verdan.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.List;

import no.example.verdan.model.Booking;
import no.example.verdan.model.Room;
import no.example.verdan.util.HibernateUtil;

public class RoomDao extends BaseDao<Room> {

    public RoomDao() {
        super(Room.class);
    }

    public Room findByRoomNumber(String number, int institutionId) {
        if (number == null || number.isBlank()) return null;
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Room> res = em.createQuery(
                    "select r from Room r where lower(r.roomNumber) = lower(:n) and r.institution.id = :instId",
                    Room.class)
                .setParameter("n", number.trim())
                .setParameter("instId", institutionId)
                .getResultList();
            return res.isEmpty() ? null : res.get(0);
        } finally {
            em.close();
        }
    }

    /**
     * Delete a room and all bookings that are associated with that room.
     *
     * Logic:
     *  - Find the managed Room by ID
     *  - Find all bookings that contain this room
     *  - For each booking:
     *      * Clear booking.getRooms() to clean up the join table
     *      * Remove the booking entity
     *  - Finally, remove the room itself
     */
    /**
     * Delete a room and all bookings that are associated with that room.
     */
    public void deleteRoomAndBookings(int roomId, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            Room managedRoom = em.find(Room.class, roomId);
            if (managedRoom == null || managedRoom.getInstitution().getId() != institutionId) {
                tx.commit();
                return;
            }

            // Find all bookings that use this room
            List<Booking> bookings = em.createQuery(
                    "select distinct b " +
                    "from Booking b join b.rooms r " +
                    "where r = :room",
                    Booking.class
            )
            .setParameter("room", managedRoom)
            .getResultList();

            // Delete all bookings that are associated with this room
            for (Booking b : bookings) {
                Booking managedBooking = em.find(Booking.class, b.getId());
                if (managedBooking != null) {
                    managedBooking.getRooms().clear();  // clean join table (booking_rooms)
                    em.remove(managedBooking);
                }
            }

            // Finally delete the room
            em.remove(managedRoom);

            tx.commit();
        } catch (RuntimeException ex) {
            if (tx.isActive()) tx.rollback();
            throw ex;
        } finally {
            em.close();
        }
    }
}
