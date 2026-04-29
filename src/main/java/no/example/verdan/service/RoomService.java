package no.example.verdan.service;

import no.example.verdan.dao.RoomDao;
import no.example.verdan.dto.RoomDto;
import no.example.verdan.model.Institution;
import no.example.verdan.model.Room;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service layer for room management.
 */
public class RoomService {

    private static final Logger LOG = LoggerFactory.getLogger(RoomService.class);
    private final RoomDao roomDao;

    public RoomService() {
        this(new RoomDao());
    }

    public RoomService(RoomDao roomDao) {
        this.roomDao = roomDao;
    }

    /** Get all rooms. */
    public List<RoomDto.Response> getAllRooms(int institutionId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return roomDao.findAll().stream().map(this::toResponse).toList();
        }
        return roomDao.findAll(institutionId).stream().map(this::toResponse).toList();
    }

    /** Get room by ID. */
    public RoomDto.Response getRoomById(int id, int institutionId, boolean isSuperAdmin) {
        Room room = roomDao.find(id);
        if (room == null) throw new NotFoundException("Room not found");
        if (!isSuperAdmin && room.getInstitution().getId() != institutionId)
            throw new NotFoundException("Room not found");
        return toResponse(room);
    }

    /** Create a room. */
    public RoomDto.Response createRoom(RoomDto.Request req, int institutionId, boolean isSuperAdmin) {
        if (!InputValidator.isNotBlank(req.roomNumber()))
            throw new ValidationException("Room number is required");
        if (req.capacity() <= 0)
            throw new ValidationException("Capacity must be greater than 0");

        Room room = new Room();
        room.setRoomNumber(InputValidator.sanitize(req.roomNumber()));
        room.setRoomType(InputValidator.sanitize(req.roomType()));
        room.setCapacity(req.capacity());

        Institution inst = new Institution();
        inst.setId(req.institutionId() != null && isSuperAdmin ? req.institutionId() : institutionId);
        room.setInstitution(inst);

        roomDao.save(room);
        LOG.info("Room created: {} ({}) for institution {}", room.getRoomNumber(), room.getRoomType(), inst.getId());
        return toResponse(room);
    }

    /** Update a room. */
    public RoomDto.Response updateRoom(int id, RoomDto.Request req, int institutionId, boolean isSuperAdmin) {
        Room existing = roomDao.find(id);
        if (existing == null) throw new NotFoundException("Room not found");
        if (!isSuperAdmin && existing.getInstitution().getId() != institutionId)
            throw new NotFoundException("Room not found");

        if (req.roomNumber() != null) existing.setRoomNumber(InputValidator.sanitize(req.roomNumber()));
        if (req.roomType() != null) existing.setRoomType(InputValidator.sanitize(req.roomType()));
        if (req.capacity() > 0) existing.setCapacity(req.capacity());

        // Allow changing institution
        if (req.institutionId() != null && isSuperAdmin) {
            Institution newInst = new Institution();
            newInst.setId(req.institutionId());
            existing.setInstitution(newInst);
        }

        roomDao.update(existing);
        LOG.info("Room updated: {} (ID: {})", existing.getRoomNumber(), id);
        return toResponse(existing);
    }

    /** Delete a room and its bookings. */
    public void deleteRoom(int id, int institutionId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            Room room = roomDao.find(id);
            if (room == null) throw new NotFoundException("Room not found");
            roomDao.deleteRoomAndBookings(id, room.getInstitution().getId());
        } else {
            roomDao.deleteRoomAndBookings(id, institutionId);
        }
        LOG.info("Room deleted (ID: {})", id);
    }

    private RoomDto.Response toResponse(Room r) {
        Integer instId = r.getInstitution() != null ? r.getInstitution().getId() : null;
        String instName = r.getInstitution() != null ? r.getInstitution().getName() : "Default";
        return new RoomDto.Response(r.getId(), r.getRoomNumber(), r.getRoomType(), r.getCapacity(), instId, instName);
    }
}
