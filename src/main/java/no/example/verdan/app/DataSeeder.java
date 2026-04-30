package no.example.verdan.app;

import no.example.verdan.auth.AuthService;
import no.example.verdan.dao.AttendanceDao;
import no.example.verdan.dao.BookingDao;
import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.dao.RoomDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.SubjectDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.model.Attendance;
import no.example.verdan.model.Booking;
import no.example.verdan.model.BookingStatus;
import no.example.verdan.model.Grade;
import no.example.verdan.model.Institution;
import no.example.verdan.model.Room;
import no.example.verdan.model.Subject;
import no.example.verdan.model.SubjectAssignment;
import no.example.verdan.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds the database with initial data matching the production AWS RDS state.
 * Only runs if tables are empty (safe to run multiple times).
 */
public class DataSeeder {

    private final AuthService auth = new AuthService();

    public void seed() {
        InstitutionDao instDao = new InstitutionDao();

        // --- 1. INSTITUTIONS ---
        if (instDao.findAll().isEmpty()) {
            System.out.println("Seeding Institutions...");
            seedInstitutions(instDao);
        }

        List<Institution> institutions = instDao.findAll();

        Institution gokstad = findByName(institutions, "Gokstad akademiet");
        Institution usn = findByName(institutions, "USN Vestfold");
        Institution svgs = findByName(institutions, "Sandefjord Videregående Skole");
        Institution ranvik = findByName(institutions, "Ranvik Ungdomsskole");
        Institution innlandet = findByName(institutions, "Universitetet i Innlandet");

        // --- 2. USERS ---
        UserDao userDao = new UserDao();
        if (userDao.findAll().size() < 5) {
            System.out.println("Seeding Users...");
            seedUsers(userDao, gokstad, usn, svgs, ranvik, innlandet);
        }

        // --- 3. ROOMS ---
        RoomDao roomDao = new RoomDao();
        if (roomDao.findAll().isEmpty()) {
            System.out.println("Seeding Rooms...");
            seedRooms(roomDao, gokstad, usn, svgs, ranvik, innlandet);
        }

        // --- 4. SUBJECTS ---
        SubjectDao subjectDao = new SubjectDao();
        if (subjectDao.findAll().isEmpty()) {
            System.out.println("Seeding Subjects...");
            seedSubjects(subjectDao, gokstad, usn, svgs, ranvik, innlandet);
        }

        // --- 5. SUBJECT ASSIGNMENTS ---
        SubjectAssignmentDao saDao = new SubjectAssignmentDao();
        if (saDao.findAll().isEmpty()) {
            System.out.println("Seeding Subject Assignments...");
            seedSubjectAssignments(saDao, gokstad, usn, svgs, ranvik);
        }

        // --- 6. GRADES ---
        GradeDao gradeDao = new GradeDao();
        if (gradeDao.findAll().isEmpty()) {
            System.out.println("Seeding Grades...");
            seedGrades(gradeDao, userDao, usn);
        }

        // --- 7. ATTENDANCE ---
        AttendanceDao attendanceDao = new AttendanceDao();
        if (attendanceDao.findAll().isEmpty()) {
            System.out.println("Seeding Attendance...");
            seedAttendance(attendanceDao, userDao, usn);
        }

        // --- 8. BOOKINGS ---
        BookingDao bookingDao = new BookingDao();
        if (bookingDao.findAll().isEmpty()) {
            System.out.println("Seeding Bookings...");
            seedBookings(bookingDao, roomDao, gokstad);
        }

        // --- 9. FIX VGS ATTENDANCE LIMITS (migration) ---
        fixVgsAttendanceLimits(instDao);
    }

    /**
     * Ensure all programs in VGS institutions have attendance tracking enabled
     * with the mandatory 10% absence limit (90% attendance).
     * This runs every startup to fix any existing or newly created programs.
     */
    private void fixVgsAttendanceLimits(InstitutionDao instDao) {
        no.example.verdan.dao.ProgramDao programDao = new no.example.verdan.dao.ProgramDao();
        List<Institution> allInst = instDao.findAll();
        int fixed = 0;
        for (Institution inst : allInst) {
            if ("VGS".equalsIgnoreCase(inst.getLevel())) {
                List<no.example.verdan.model.Program> programs = programDao.findByInstitution(inst.getId());
                for (no.example.verdan.model.Program p : programs) {
                    if (!p.isAttendanceRequired() || p.getMinAttendancePct() == null || p.getMinAttendancePct() != 90) {
                        p.setAttendanceRequired(true);
                        p.setMinAttendancePct(90);
                        programDao.update(p);
                        fixed++;
                    }
                }
            }
        }
        if (fixed > 0) {
            System.out.println("Fixed VGS attendance limits for " + fixed + " programs");
        }
    }

    // ===================== INSTITUTIONS =====================
    private void seedInstitutions(InstitutionDao dao) {

        Institution gokstad = new Institution("Gokstad akademiet");
        gokstad.setLocation("Sandefjord");
        gokstad.setLevel("FAGSKOLE");
        dao.save(gokstad);

        Institution usn = new Institution("USN Vestfold");
        usn.setLocation("Horten, Vestfold");
        usn.setLevel("UNIVERSITET");
        dao.save(usn);

        Institution svgs = new Institution("Sandefjord Videregående Skole");
        svgs.setLocation("Sandefjord");
        svgs.setLevel("VGS");
        dao.save(svgs);

        Institution ranvik = new Institution("Ranvik Ungdomsskole");
        ranvik.setLocation("Sandefjord");
        ranvik.setLevel("UNGDOMSSKOLE");
        dao.save(ranvik);

        Institution innlandet = new Institution("Universitetet i Innlandet");
        innlandet.setLocation("Hamar");
        innlandet.setLevel("UNIVERSITET");
        dao.save(innlandet);
    }

    // ===================== USERS =====================
    private void seedUsers(UserDao dao, Institution gokstad,
            Institution usn, Institution svgs, Institution ranvik, Institution innlandet) {

        // Super Admin
        ensureUser(dao, "admin", "admin123", "SUPER_ADMIN", null, null, null, null, null);

        // Institution Admin
        ensureUser(dao, "inst-admin", "123456", "INSTITUTION_ADMIN", "inst-admin", "inst-admin", null, null, gokstad);

        // Teachers
        ensureUser(dao, "teacher", "teacher123", "TEACHER", "teacher", "teacher", "454545454", "teacher@gmail.com",
                gokstad);
        ensureUser(dao, "anne.larsen", "password123", "TEACHER", "Anne", "Larsen", "48910233", "anne.larsen@school.no",
                gokstad);
        ensureUser(dao, "bjorn.k", "password123", "TEACHER", "Bjorn", "Kristoffersen", "92344110", "bjorn.k@school.no",
                usn);
        ensureUser(dao, "camilla.h", "password123", "TEACHER", "Camilla", "Hagen", "41255090", "camilla.h@school.no",
                svgs);
        ensureUser(dao, "daniel.n", "password123", "TEACHER", "Daniel", "Nilsen", "95832177", "daniel.n@school.no",
                ranvik);
        ensureUser(dao, "eva.s", "password123", "TEACHER", "Eva", "Solberg", "47800233", "eva.s@school.no", gokstad);
        ensureUser(dao, "fredrik.a", "password123", "TEACHER", "Fredrik", "Aas", "90774429", "fredrik.a@school.no",
                svgs);
        ensureUser(dao, "guro.m", "password123", "TEACHER", "Guro", "Myhre", "99213344", "guro.m@school.no", ranvik);
        ensureUser(dao, "henrik.o", "password123", "TEACHER", "Henrik", "Ostby", "48499122", "henrik.o@school.no", usn);
        ensureUser(dao, "ingrid.t", "password123", "TEACHER", "Ingrid", "Thorvaldsen", "91155287", "ingrid.t@school.no",
                innlandet);
        ensureUser(dao, "jonas.v", "password123", "TEACHER", "Jonas", "Vik", "45123998", "jonas.v@school.no", svgs);

        // Students
        ensureUser(dao, "student", "student123", "STUDENT", "student", "student", "4545454545", "student@gmail.com",
                gokstad);
        ensureUser(dao, "maria.o", "password123", "STUDENT", "Maria", "Olsen", "48622990", "maria.o@student.no",
                gokstad);
        ensureUser(dao, "thomas.b", "password123", "STUDENT", "Thomas", "Berg", "90912233", "thomas.b@student.no",
                ranvik);
        ensureUser(dao, "selma.e", "password123", "STUDENT", "Selma", "Eriksen", "40455121", "selma.e@student.no",
                svgs);
        ensureUser(dao, "marius.h", "password123", "STUDENT", "Marius", "Holm", "99812234", "marius.h@student.no",
                svgs);
        ensureUser(dao, "sofie.b", "password123", "STUDENT", "Sofie", "Bakke", "93412980", "sofie.b@student.no",
                innlandet);
        ensureUser(dao, "tobias.a", "password123", "STUDENT", "Tobias", "Andersen", "48992211", "tobias.a@student.no",
                usn);
        ensureUser(dao, "julie.a", "password123", "STUDENT", "Julie", "Antonsen", "99021133", "julie.a@student.no",
                gokstad);
        ensureUser(dao, "emil.h", "password123", "STUDENT", "Emil", "Hansen", "40012298", "emil.h@student.no", ranvik);
        ensureUser(dao, "nora.l", "password123", "STUDENT", "Nora", "Lie", "47820043", "nora.l@student.no", svgs);
        ensureUser(dao, "kasper.m", "password123", "STUDENT", "Kasper", "Moe", "47655291", "kasper.m@student.no",
                innlandet);
        ensureUser(dao, "helene.s", "password123", "STUDENT", "Helene", "Sjoberg", "41451288", "helene.s@student.no",
                innlandet);
        ensureUser(dao, "oliver.f", "password123", "STUDENT", "Oliver", "Frydenlund", "90321192", "oliver.f@student.no",
                usn);
        ensureUser(dao, "kristin.r", "password123", "STUDENT", "Kristin", "Ruud", "48099227", "kristin.r@student.no",
                usn);
        ensureUser(dao, "adrian.k", "password123", "STUDENT", "Adrian", "Knutsen", "95541220", "adrian.k@student.no",
                gokstad);
        ensureUser(dao, "emma.l", "password123", "STUDENT", "Emma", "Lunde", "48920110", "emma.l@student.no", gokstad);
        ensureUser(dao, "ari93", "password123", "STUDENT", "Aridani Dahl", "Guerra", "47631022",
                "ari_dani_93@hotmail.com", usn);
    }

    // ===================== ROOMS =====================
    private void seedRooms(RoomDao dao, Institution gokstad, Institution usn,
            Institution svgs, Institution ranvik, Institution innlandet) {
        saveRoom(dao, "R101", "Classroom", 28, ranvik);
        saveRoom(dao, "R102", "Classroom", 30, gokstad);
        saveRoom(dao, "R201", "Group Room", 6, usn);
        saveRoom(dao, "R202", "Group Room", 8, usn);
        saveRoom(dao, "R203", "Group Room", 10, svgs);
        saveRoom(dao, "A301", "Amphitheater", 120, svgs);
        saveRoom(dao, "A302", "Amphitheater", 80, ranvik);
        saveRoom(dao, "R303", "Classroom", 24, ranvik);
        saveRoom(dao, "R304", "Classroom", 32, innlandet);
        saveRoom(dao, "R305", "Classroom", 20, innlandet);
        saveRoom(dao, "2003", "class", 20, gokstad);
        saveRoom(dao, "F303", "Classroom", 30, gokstad);
    }

    // ===================== SUBJECTS =====================
    // Subject level always matches the institution's level
    private void seedSubjects(SubjectDao dao, Institution gokstad, Institution usn,
            Institution svgs, Institution ranvik, Institution innlandet) {
        saveSubject(dao, "MAT101", "Basic Mathematics", gokstad);
        saveSubject(dao, "ENG102", "Academic English", gokstad);
        saveSubject(dao, "PRO103", "Programming 1", gokstad);
        saveSubject(dao, "DAT104", "Databases", usn);
        saveSubject(dao, "HIST105", "Modern History", svgs);
        saveSubject(dao, "SCI106", "Natural Sciences", svgs);
        saveSubject(dao, "GEO107", "Geography", ranvik);
        saveSubject(dao, "SOC108", "Social Studies", ranvik);
        saveSubject(dao, "FYS109", "Physics", innlandet);
        saveSubject(dao, "KJE110", "Chemistry", innlandet);
        saveSubject(dao, "DAT2", "Database 2", usn);
    }

    // ===================== SUBJECT ASSIGNMENTS =====================
    private void seedSubjectAssignments(SubjectAssignmentDao dao, Institution gokstad,
            Institution usn, Institution svgs, Institution ranvik) {
        saveAssignment(dao, "eva.s", "TEACHER", "PRO103", gokstad);
        saveAssignment(dao, "maria.o", "STUDENT", "PRO103", gokstad);
        saveAssignment(dao, "guro.m", "TEACHER", "GEO107", ranvik);
        saveAssignment(dao, "maria.o", "STUDENT", "GEO107", gokstad);
        saveAssignment(dao, "camilla.h", "TEACHER", "MAT101", svgs);
        saveAssignment(dao, "ari93", "STUDENT", "MAT101", usn);
        saveAssignment(dao, "daniel.n", "TEACHER", "DAT2", ranvik);
        saveAssignment(dao, "guro.m", "TEACHER", "DAT2", ranvik);
    }

    // ===================== GRADES =====================
    private void seedGrades(GradeDao dao, UserDao userDao, Institution usn) {
        // ari93 got grade 5 in MAT101 from camilla.h on 2026-04-03
        List<User> ariUsers = userDao.findByUsername("ari93");
        if (!ariUsers.isEmpty()) {
            Grade grade = new Grade(ariUsers.get(0), "MAT101", "5", "camilla.h");
            grade.setDateGiven(LocalDate.of(2026, 4, 3));
            grade.setInstitution(usn);
            dao.save(grade);
        }
    }

    // ===================== ATTENDANCE =====================
    private void seedAttendance(AttendanceDao dao, UserDao userDao, Institution usn) {
        // ari93 was Sick for MAT101 on 2026-04-03
        List<User> ariUsers = userDao.findByUsername("ari93");
        if (!ariUsers.isEmpty()) {
            Attendance att = new Attendance();
            att.setStudent(ariUsers.get(0));
            att.setSubjectCode("MAT101");
            att.setDateOf(LocalDate.of(2026, 4, 3));
            att.setStatus("Sick");
            att.setNote("");
            att.setInstitution(usn);
            dao.save(att);
        }
    }

    // ===================== BOOKINGS =====================
    private void seedBookings(BookingDao dao, RoomDao roomDao, Institution gokstad) {
        List<Room> rooms = roomDao.findAll();
        Room f303 = rooms.stream().filter(r -> "F303".equals(r.getRoomNumber())).findFirst().orElse(null);
        Room r2003 = rooms.stream().filter(r -> "2003".equals(r.getRoomNumber())).findFirst().orElse(null);

        if (f303 == null || r2003 == null)
            return;

        // MAT101 bookings: Mon-Thu, 09:00-14:00, Room F303
        saveBooking(dao, "admin", "MAT101", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 7, 9, 0), LocalDateTime.of(2026, 4, 7, 14, 0));
        saveBooking(dao, "admin", "MAT101", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 8, 9, 0), LocalDateTime.of(2026, 4, 8, 14, 0));
        saveBooking(dao, "admin", "MAT101", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 9, 9, 0), LocalDateTime.of(2026, 4, 9, 14, 0));
        saveBooking(dao, "admin", "MAT101", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 10, 9, 0), LocalDateTime.of(2026, 4, 10, 14, 0));

        // ENG102 bookings: Mon-Thu, 09:00-14:00, Room 2003
        saveBooking(dao, "admin", "ENG102", "", gokstad, r2003,
                LocalDateTime.of(2026, 4, 7, 9, 0), LocalDateTime.of(2026, 4, 7, 14, 0));
        saveBooking(dao, "admin", "ENG102", "", gokstad, r2003,
                LocalDateTime.of(2026, 4, 8, 9, 0), LocalDateTime.of(2026, 4, 8, 14, 0));
        saveBooking(dao, "admin", "ENG102", "", gokstad, r2003,
                LocalDateTime.of(2026, 4, 9, 9, 0), LocalDateTime.of(2026, 4, 9, 14, 0));
        saveBooking(dao, "admin", "ENG102", "", gokstad, r2003,
                LocalDateTime.of(2026, 4, 10, 9, 0), LocalDateTime.of(2026, 4, 10, 14, 0));

        // PRO103 bookings: Tue-Thu, 15:00-17/18:00, Room F303
        saveBooking(dao, "admin", "PRO103", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 8, 15, 0), LocalDateTime.of(2026, 4, 8, 17, 0));
        saveBooking(dao, "admin", "PRO103", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 9, 15, 0), LocalDateTime.of(2026, 4, 9, 18, 0));
        saveBooking(dao, "admin", "PRO103", "", gokstad, f303,
                LocalDateTime.of(2026, 4, 10, 15, 0), LocalDateTime.of(2026, 4, 10, 18, 0));
    }

    // ===================== HELPER METHODS =====================

    private void ensureUser(UserDao dao, String username, String password, String role,
            String firstName, String lastName, String phone, String email, Institution inst) {
        if (dao.findByUsername(username).isEmpty()) {
            User u = new User();
            u.setUsername(username);
            u.setPassword(auth.hash(password));
            u.setRole(role);
            u.setFirstName(firstName);
            u.setLastName(lastName);
            u.setPhone(phone);
            u.setEmail(email);
            u.setInstitution(inst);
            dao.save(u);
            System.out.println("  User: " + username + " (" + role + ") -> " + inst.getName());
        }
    }

    private void saveRoom(RoomDao dao, String number, String type, int capacity, Institution inst) {
        Room r = new Room(number, type, capacity);
        r.setInstitution(inst);
        dao.save(r);
    }

    private void saveSubject(SubjectDao dao, String code, String name, Institution inst) {
        Subject s = new Subject(code, name, inst.getLevel());
        s.setInstitution(inst);
        dao.save(s);
    }

    private void saveAssignment(SubjectAssignmentDao dao, String username, String role, String subject,
            Institution inst) {
        SubjectAssignment sa = new SubjectAssignment();
        sa.setUsername(username);
        sa.setRole(role);
        sa.setSubject(subject);
        sa.setInstitution(inst);
        dao.save(sa);
    }

    private void saveBooking(BookingDao dao, String createdBy, String subject, String desc,
            Institution inst, Room room, LocalDateTime start, LocalDateTime end) {
        Booking b = new Booking();
        b.setCreatedBy(createdBy);
        b.setSubject(subject);
        b.setDescription(desc);
        b.setInstitution(inst);
        b.setStartDateTime(start);
        b.setEndDateTime(end);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setRooms(List.of(room));
        dao.save(b);
    }

    private Institution findByName(List<Institution> list, String name) {
        return list.stream()
                .filter(i -> name.equals(i.getName()))
                .findFirst()
                .orElse(list.get(0)); // Fallback to first institution
    }
}