package no.example.verdan.service;

import no.example.verdan.dao.BookingDao;
import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.RoomDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.BookingDto;
import no.example.verdan.model.Booking;
import no.example.verdan.model.Program;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.Room;
import no.example.verdan.model.User;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Service layer for booking management.
 */
public class BookingService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingService.class);
    private final BookingDao bookingDao;
    private final RoomDao roomDao;
    private final SubjectAssignmentDao assignmentDao;
    private final ProgramMemberDao memberDao;
    private final ProgramDao programDao;
    private final UserDao userDao;

    public BookingService() {
        this(new BookingDao(), new RoomDao(), new SubjectAssignmentDao(),
             new ProgramMemberDao(), new ProgramDao(), new UserDao());
    }

    public BookingService(BookingDao bookingDao, RoomDao roomDao, SubjectAssignmentDao assignmentDao,
                          ProgramMemberDao memberDao, ProgramDao programDao, UserDao userDao) {
        this.bookingDao = bookingDao;
        this.roomDao = roomDao;
        this.assignmentDao = assignmentDao;
        this.memberDao = memberDao;
        this.programDao = programDao;
        this.userDao = userDao;
    }

    /** Get all bookings based on user role. Students see only bookings for their class. */
    public List<BookingDto.Response> getBookingsForUser(String role, String username, int institutionId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return bookingDao.findAll().stream().map(this::toResponse).toList();
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            // Find the student's active program IDs
            List<User> users = userDao.findByUsername(username);
            if (!users.isEmpty()) {
                List<ProgramMember> memberships = memberDao.findByUser(users.get(0).getId());
                java.util.Set<Integer> myProgramIds = new java.util.HashSet<>();
                for (ProgramMember pm : memberships) {
                    if (!pm.isGraduated() && "STUDENT".equalsIgnoreCase(pm.getRole())) {
                        myProgramIds.add(pm.getProgram().getId());
                    }
                }
                return bookingDao.findAllWithRooms(institutionId).stream()
                    .filter(b -> {
                        // Student only sees bookings linked to their class (program)
                        if (b.getProgram() == null) return false;
                        return myProgramIds.contains(b.getProgram().getId());
                    })
                    .map(this::toResponse).toList();
            }
            return List.of();
        }
        return bookingDao.findAllWithRooms(institutionId).stream().map(this::toResponse).toList();
    }

    /** Get booking by ID. */
    public BookingDto.Response getBookingById(int id, int institutionId, boolean isSuperAdmin) {
        Booking booking = isSuperAdmin ? bookingDao.find(id) : bookingDao.findByIdWithRooms(id, institutionId);
        if (booking == null) throw new NotFoundException("Booking ikke funnet");
        return toResponse(booking);
    }

    /** Create a booking. */
    public BookingDto.Response createBooking(BookingDto.Request req, String createdBy, int institutionId) {
        if (req.roomId() == null || req.roomId() <= 0)
            throw new ValidationException("Du må velge et rom");
        if (req.startDateTime() == null || req.endDateTime() == null)
            throw new ValidationException("Start- og sluttidspunkt er påkrevd");
        if (req.startDateTime().isAfter(req.endDateTime()))
            throw new ValidationException("Starttidspunkt må være før sluttidspunkt");
        if (!InputValidator.isNotBlank(req.subject()))
            throw new ValidationException("Du må velge et fag");

        Room room = roomDao.find(req.roomId(), institutionId);
        if (room == null) throw new NotFoundException("Rommet ble ikke funnet i din institusjon");

        Booking booking = new Booking();
        booking.setStartDateTime(req.startDateTime());
        booking.setEndDateTime(req.endDateTime());
        booking.setDescription(InputValidator.sanitize(req.description()));
        booking.setSubject(InputValidator.sanitize(req.subject()));
        booking.setCreatedBy(createdBy);
        
        // Link to institution
        no.example.verdan.model.Institution inst = new no.example.verdan.model.Institution();
        inst.setId(institutionId);
        booking.setInstitution(inst);

        // Link to program/class if provided
        if (req.programId() != null && req.programId() > 0) {
            Program program = programDao.findWithSubjects(req.programId());
            if (program != null) {
                booking.setProgram(program);
            }
        }

        boolean saved = bookingDao.saveConfirmedIfAvailable(booking, req.roomId(), institutionId);
        if (!saved)
            throw new ConflictException("Rommet er allerede booket på dette tidspunktet");

        LOG.info("Booking created: room={}, subject={}, by={} in institution {}", room.getRoomNumber(), req.subject(), createdBy, institutionId);
        return toResponse(booking);
    }

    /** Update an existing booking. */
    public BookingDto.Response updateBooking(int id, BookingDto.Request req, boolean series, int institutionId, boolean isSuperAdmin) {
        Booking booking = isSuperAdmin ? bookingDao.find(id) : bookingDao.findByIdWithRooms(id, institutionId);
        if (booking == null) throw new NotFoundException("Booking ikke funnet");
        if (req.startDateTime() == null || req.endDateTime() == null)
            throw new ValidationException("Start- og sluttidspunkt er påkrevd");
        if (!req.startDateTime().isBefore(req.endDateTime()))
            throw new ValidationException("Starttidspunkt må være før sluttidspunkt");

        Integer roomId = booking.getRooms().isEmpty() ? null : booking.getRooms().iterator().next().getId();
        String oldSubject = booking.getSubject();

        LocalDateTime newStart = req.startDateTime();
        LocalDateTime newEnd = req.endDateTime();

        // Check for room conflicts (excluding this booking itself)
        if (roomId != null) {
            List<Booking> conflicts = bookingDao.findForRoomBetween(roomId, newStart, newEnd, institutionId);
            conflicts.removeIf(b -> b.getId().equals(booking.getId()));
            if (!conflicts.isEmpty()) {
                throw new ConflictException("Rommet er allerede booket på dette tidspunktet");
            }
        }

        booking.setStartDateTime(newStart);
        booking.setEndDateTime(newEnd);
        bookingDao.update(booking);

        if (series && roomId != null) {
            LocalTime newStartTime = newStart.toLocalTime();
            LocalTime newEndTime = newEnd.toLocalTime();

            Integer programId = booking.getProgram() != null ? booking.getProgram().getId() : null;
            List<Booking> all = bookingDao.findForRoomSubjectAndProgram(roomId, oldSubject, programId, institutionId);
            for (Booking b : all) {
                if (b.getId().equals(booking.getId())) continue;
                if (b.getStartDateTime() == null || b.getEndDateTime() == null) continue;

                LocalDate d = b.getStartDateTime().toLocalDate();
                LocalDateTime seriesNewStart = LocalDateTime.of(d, newStartTime);
                LocalDateTime seriesNewEnd = LocalDateTime.of(d, newEndTime);

                // Check conflicts for each series booking too
                List<Booking> seriesConflicts = bookingDao.findForRoomBetween(roomId, seriesNewStart, seriesNewEnd, institutionId);
                seriesConflicts.removeIf(c -> c.getId().equals(b.getId()));
                if (!seriesConflicts.isEmpty()) continue; // Skip conflicting days silently

                b.setStartDateTime(seriesNewStart);
                b.setEndDateTime(seriesNewEnd);
                bookingDao.update(b);
            }
        }
        
        LOG.info("Booking updated: ID={}, series={} in institution {}", id, series, institutionId);
        return toResponse(booking);
    }

    /** Toggle booking between CONFIRMED and CANCELLED (avspasering/day off). */
    public BookingDto.Response toggleCancel(int id, int institutionId, boolean isSuperAdmin) {
        Booking booking = isSuperAdmin ? bookingDao.find(id) : bookingDao.findByIdWithRooms(id, institutionId);
        if (booking == null) throw new NotFoundException("Booking ikke funnet");

        if (booking.getStatus() == no.example.verdan.model.BookingStatus.CANCELLED) {
            booking.setStatus(no.example.verdan.model.BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(no.example.verdan.model.BookingStatus.CANCELLED);
        }
        bookingDao.update(booking);
        LOG.info("Booking {} toggled to {} in institution {}", id, booking.getStatus(), institutionId);
        return toResponse(booking);
    }

    /** Delete a booking. */
    public void deleteBooking(int id, boolean series, int institutionId, boolean isSuperAdmin) {
        Booking booking = isSuperAdmin ? bookingDao.find(id) : bookingDao.findByIdWithRooms(id, institutionId);
        if (booking == null) throw new NotFoundException("Booking ikke funnet");

        if (series) {
            String subject = booking.getSubject();
            LocalTime start = booking.getStartDateTime() != null ? booking.getStartDateTime().toLocalTime() : null;
            LocalTime end = booking.getEndDateTime() != null ? booking.getEndDateTime().toLocalTime() : null;
            Integer roomId = booking.getRooms().isEmpty() ? null : booking.getRooms().iterator().next().getId();

            if (roomId != null && start != null && end != null) {
                LocalDateTime from = booking.getStartDateTime().minusMonths(6);
                LocalDateTime to = booking.getStartDateTime().plusMonths(6);
                List<Booking> list = bookingDao.findForRoomBetween(roomId, from, to, institutionId);

                List<Integer> idsToDelete = new java.util.ArrayList<>();
                Integer programId = booking.getProgram() != null ? booking.getProgram().getId() : null;
                for (Booking b : list) {
                    if (subject.equalsIgnoreCase(b.getSubject()) &&
                        b.getStartDateTime() != null && b.getStartDateTime().toLocalTime().equals(start) &&
                        b.getEndDateTime() != null && b.getEndDateTime().toLocalTime().equals(end) &&
                        (programId == null || (b.getProgram() != null && programId.equals(b.getProgram().getId())))) {
                        idsToDelete.add(b.getId());
                    }
                }
                bookingDao.deleteBookingsBatch(idsToDelete);
                LOG.info("Deleted {} bookings for series corresponding to Booking ID={} in institution {}", idsToDelete.size(), id, institutionId);
                return;
            }
        }
        
        bookingDao.deleteBooking(booking);
        LOG.info("Booking deleted: ID={}, series={} in institution {}", id, series, institutionId);
    }

    private BookingDto.Response toResponse(Booking b) {
        List<String> roomNumbers = b.getRooms() != null
                ? b.getRooms().stream().map(Room::getRoomNumber).toList()
                : List.of();
        Integer programId = b.getProgram() != null ? b.getProgram().getId() : null;
        String programName = b.getProgram() != null ? b.getProgram().getName() : null;
        return new BookingDto.Response(b.getId(), roomNumbers,
                b.getStartDateTime(), b.getEndDateTime(),
                b.getStatus() != null ? b.getStatus().name() : null,
                b.getDescription(), b.getCreatedBy(), b.getSubject(),
                programId, programName);
    }
}
