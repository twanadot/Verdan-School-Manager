package no.example.verdan.service;

import no.example.verdan.dao.AttendanceDao;
import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.SubjectDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.AttendanceDto;
import no.example.verdan.model.*;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Service layer for attendance management.
 */
public class AttendanceService {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceService.class);
    private final AttendanceDao attendanceDao;
    private final UserDao userDao;
    private final ProgramMemberDao memberDao;
    private final ProgramDao programDao;
    private final SubjectDao subjectDao;

    /** Statuses that count as unexcused absence (against the limit). */
    private static final Set<String> UNEXCUSED_STATUSES = Set.of("Absent", "Late", "Sick");

    public AttendanceService() {
        this(new AttendanceDao(), new UserDao(), new ProgramMemberDao(), new ProgramDao(), new SubjectDao());
    }

    public AttendanceService(AttendanceDao attendanceDao, UserDao userDao,
                             ProgramMemberDao memberDao, ProgramDao programDao, SubjectDao subjectDao) {
        this.attendanceDao = attendanceDao;
        this.userDao = userDao;
        this.memberDao = memberDao;
        this.programDao = programDao;
        this.subjectDao = subjectDao;
    }

    /** Get attendance records based on user role. */
    public List<AttendanceDto.Response> getAttendance(String role, String username, int institutionId, boolean isSuperAdmin) {
        List<Attendance> records;
        if (isSuperAdmin) {
            records = attendanceDao.findAll();
        } else if ("STUDENT".equalsIgnoreCase(role)) {
            records = attendanceDao.findByStudentUsername(username, institutionId);
        } else if ("TEACHER".equalsIgnoreCase(role)) {
            records = attendanceDao.findForTeacher(username, institutionId);
        } else {
            records = attendanceDao.findAllWithStudent(institutionId);
        }
        return records.stream().map(this::toResponse).toList();
    }

    /** Get attendance by ID. */
    public AttendanceDto.Response getAttendanceById(int id, int institutionId, boolean isSuperAdmin) {
        Attendance att = isSuperAdmin ? attendanceDao.find(id) : attendanceDao.find(id, institutionId);
        if (att == null) throw new NotFoundException("Attendance record not found or access denied");
        return toResponse(att);
    }

    /** Get attendance for a specific student. */
    public List<AttendanceDto.Response> getAttendanceByStudent(String username, int institutionId) {
        return attendanceDao.findByStudentUsername(username, institutionId).stream()
                .map(this::toResponse).toList();
    }

    /** Get absence rate for a student. */
    public Map<String, Object> getAbsenceRate(String username, int institutionId) {
        double rate = attendanceDao.absenceRateForStudent(username, institutionId);
        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("absenceRate", rate);
        result.put("absencePercent", String.format("%.1f%%", rate * 100));
        return result;
    }

    // ========================================================================
    //  Subject Absence Stats (per-subject attendance tracking with limits)
    // ========================================================================

    /**
     * Get per-subject absence statistics for a student.
     * Returns stats across ALL institutions the student has been enrolled in,
     * enabling a full attendance history grouped by institution level.
     */
    public List<AttendanceDto.SubjectAbsenceStats> getSubjectAbsenceStats(String username, int institutionId) {
        // Find the student user
        User student = userDao.findByUsername(username, institutionId).stream().findFirst().orElse(null);
        if (student == null) return List.of();

        // Get ALL attendance records across all institutions for this student
        List<Attendance> allRecords = attendanceDao.findByStudentUsername(username, institutionId);

        // Group by subject code
        Map<String, List<Attendance>> bySubject = new LinkedHashMap<>();
        for (Attendance a : allRecords) {
            bySubject.computeIfAbsent(a.getSubjectCode(), k -> new ArrayList<>()).add(a);
        }

        // Build maps from program memberships: subjectCode -> maxAbsencePct, subjectCode -> institution info
        Map<String, Integer> subjectLimits = new HashMap<>();
        Map<String, String> subjectInstLevel = new HashMap<>();
        Map<String, String> subjectInstName = new HashMap<>();

        List<ProgramMember> memberships = memberDao.findByUser(student.getId());
        for (ProgramMember pm : memberships) {
            Program program = programDao.findWithSubjects(pm.getProgram().getId());
            if (program == null) continue;

            String instLevel = program.getInstitution() != null ? program.getInstitution().getLevel() : "UNKNOWN";
            String instName = program.getInstitution() != null ? program.getInstitution().getName() : "Unknown";

            for (Subject subject : program.getSubjects()) {
                subjectInstLevel.putIfAbsent(subject.getCode(), instLevel);
                subjectInstName.putIfAbsent(subject.getCode(), instName);
            }

            if (program.isAttendanceRequired() && program.getMinAttendancePct() != null) {
                int maxAbsence = 100 - program.getMinAttendancePct();
                for (Subject subject : program.getSubjects()) {
                    subjectLimits.put(subject.getCode(), maxAbsence);
                }
            }
        }

        // Also include subjects with limits but no attendance records yet
        for (String code : subjectLimits.keySet()) {
            bySubject.putIfAbsent(code, new ArrayList<>());
        }

        List<AttendanceDto.SubjectAbsenceStats> result = new ArrayList<>();
        int schoolDaysElapsed = SCHOOL_DAYS_PER_YEAR;

        for (Map.Entry<String, List<Attendance>> entry : bySubject.entrySet()) {
            String code = entry.getKey();
            List<Attendance> records = entry.getValue();

            // Get subject name and institution info
            Subject subject = subjectDao.findByCode(code, institutionId);
            String subjectName = subject != null ? subject.getName() : code;
            String instLevel = subjectInstLevel.getOrDefault(code,
                subject != null && subject.getInstitution() != null ? subject.getInstitution().getLevel() : "UNKNOWN");
            String instName = subjectInstName.getOrDefault(code,
                subject != null && subject.getInstitution() != null ? subject.getInstitution().getName() : "Ukjent");

            int total = records.size();
            int attended = 0;
            int absentUnexcused = 0;
            int absentExcused = 0;

            for (Attendance a : records) {
                if ("Present".equalsIgnoreCase(a.getStatus())) {
                    attended++;
                } else if ("Excused".equalsIgnoreCase(a.getStatus()) || a.isExcused()) {
                    absentExcused++;
                } else if (UNEXCUSED_STATUSES.contains(a.getStatus())) {
                    absentUnexcused++;
                } else {
                    attended++;
                }
            }

            // Always use school days elapsed as denominator — attendance is assumed present by default
            int effectiveTotal = Math.max(schoolDaysElapsed, 1);
            double absencePercent = absentUnexcused * 100.0 / effectiveTotal;

            Integer maxAbsence = subjectLimits.get(code);
            boolean overLimit = maxAbsence != null && absencePercent > maxAbsence;

            String status;
            if (maxAbsence == null) {
                status = "NO_LIMIT";
            } else if (overLimit) {
                status = "EXCEEDED";
            } else if (absencePercent >= maxAbsence * 0.7) {
                status = "WARNING";
            } else {
                status = "OK";
            }

            result.add(new AttendanceDto.SubjectAbsenceStats(
                code, subjectName, effectiveTotal, effectiveTotal - absentUnexcused - absentExcused, absentUnexcused, absentExcused,
                Math.round(absencePercent * 10.0) / 10.0,
                maxAbsence, overLimit, status,
                instLevel, instName,
                subject != null ? subject.getYearLevel() : null
            ));
        }

        return result;
    }

    /**
     * Fixed number of school days per year (weekdays from ~Aug 19 to ~Jun 20).
     * Used as denominator for absence percentage — attendance is assumed present by default.
     */
    private static final int SCHOOL_DAYS_PER_YEAR = 190;

    // ========================================================================
    //  CRUD
    // ========================================================================

    public AttendanceDto.Response createAttendance(AttendanceDto.Request req, int institutionId) {
        if (!InputValidator.isNotBlank(req.studentUsername()))
            throw new ValidationException("Student username is required");
        if (!InputValidator.isNotBlank(req.status()))
            throw new ValidationException("Status is required (Present/Absent/Sick/Late/Excused)");
        if (!InputValidator.isNotBlank(req.subjectCode()))
            throw new ValidationException("Subject code is required");

        List<User> students = userDao.findByUsername(req.studentUsername(), institutionId);
        if (students.isEmpty())
            throw new NotFoundException("Student not found in your institution");

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();

        Attendance dup = attendanceDao.findByStudentDateSubject(req.studentUsername(), date, req.subjectCode(), institutionId);
        if (dup != null)
            throw new ConflictException("Attendance already registered for this student/date/subject");

        Attendance att = new Attendance();
        att.setStudent(students.get(0));
        att.setDateOf(date);
        att.setStatus(InputValidator.sanitize(req.status()));
        att.setSubjectCode(InputValidator.sanitize(req.subjectCode()));
        att.setNote(InputValidator.sanitize(req.note()));
        att.setExcused(req.excused() != null && req.excused());
        
        // Link to institution
        Institution inst = new Institution();
        inst.setId(req.institutionId() != null ? req.institutionId() : institutionId);
        att.setInstitution(inst);

        attendanceDao.save(att);
        LOG.info("Attendance registered: student={}, subject={}, status={}, excused={} in institution {}",
            req.studentUsername(), req.subjectCode(), req.status(), att.isExcused(), institutionId);

        // Check if this pushes the student over the absence limit → apply IV
        checkAndApplyIV(req.studentUsername(), req.subjectCode(), institutionId);

        return toResponse(att);
    }

    /** Update an attendance record. */
    public AttendanceDto.Response updateAttendance(int id, AttendanceDto.Request req, int institutionId, boolean isSuperAdmin) {
        Attendance existing = isSuperAdmin ? attendanceDao.find(id) : attendanceDao.find(id, institutionId);
        if (existing == null) throw new NotFoundException("Attendance record not found or access denied");

        if (req.status() != null) existing.setStatus(InputValidator.sanitize(req.status()));
        if (req.note() != null) existing.setNote(InputValidator.sanitize(req.note()));
        if (req.date() != null) existing.setDateOf(req.date());
        if (req.excused() != null) existing.setExcused(req.excused());
        if (req.institutionId() != null) {
            Institution newInst = new Institution();
            newInst.setId(req.institutionId());
            existing.setInstitution(newInst);
        }

        attendanceDao.update(existing);
        LOG.info("Attendance updated: ID={}, excused={} in institution {}", id, existing.isExcused(), institutionId);

        // Re-check absence limit → may restore grade from IV or apply IV
        String studentUsername = existing.getStudent() != null ? existing.getStudent().getUsername() : null;
        if (studentUsername != null) {
            checkAndApplyIV(studentUsername, existing.getSubjectCode(), institutionId);
        }

        return toResponse(existing);
    }

    /** Delete an attendance record. */
    public void deleteAttendance(int id, int institutionId, boolean isSuperAdmin) {
        Attendance att = isSuperAdmin ? attendanceDao.find(id) : attendanceDao.find(id, institutionId);
        if (att == null) throw new NotFoundException("Attendance record not found or access denied");
        attendanceDao.delete(att);
        LOG.info("Attendance deleted: ID={} from institution {}", id, institutionId);

        // Re-check absence limit → may restore grade from IV
        String studentUsername = att.getStudent() != null ? att.getStudent().getUsername() : null;
        if (studentUsername != null) {
            checkAndApplyIV(studentUsername, att.getSubjectCode(), institutionId);
        }
    }

    private AttendanceDto.Response toResponse(Attendance a) {
        String studentUsername = a.getStudent() != null ? a.getStudent().getUsername() : null;
        String studentName = a.getStudent() != null
                ? (a.getStudent().getFirstName() + " " + a.getStudent().getLastName()).trim()
                : null;
        Integer instId = a.getInstitution() != null ? a.getInstitution().getId() : null;
        String instName = a.getInstitution() != null ? a.getInstitution().getName() : "Default";
        return new AttendanceDto.Response(a.getId(), studentUsername, studentName,
                a.getDateOf(), a.getStatus(), a.getSubjectCode(), a.getNote(),
                instId, instName, a.isExcused());
    }

    // ========================================================================
    //  IV (Ikke Vurdert) — Automatic Grade Enforcement
    // ========================================================================

    /**
     * Check if a student's absence in a subject exceeds the program's limit.
     * If exceeded → set grade to "IV" (preserving the original in originalValue).
     * If under limit → restore grade from originalValue if it was previously set to IV.
     */
    private void checkAndApplyIV(String studentUsername, String subjectCode, int institutionId) {
        try {
            // Get the current absence stats for this student/subject
            var stats = getSubjectAbsenceStats(studentUsername, institutionId);
            var subjectStat = stats.stream()
                .filter(s -> s.subjectCode().equalsIgnoreCase(subjectCode))
                .findFirst().orElse(null);

            // No stats or no limit configured → nothing to do
            if (subjectStat == null || subjectStat.maxAbsencePercent() == null) return;

            GradeDao gradeDao = new GradeDao();
            Grade grade = gradeDao.findByStudentAndSubject(studentUsername, subjectCode, institutionId);

            if (subjectStat.overLimit()) {
                // EXCEEDED: Apply IV if student has a real (non-IV) grade
                if (grade != null && !"IV".equalsIgnoreCase(grade.getValue())) {
                    grade.setOriginalValue(grade.getValue());
                    grade.setValue("IV");
                    gradeDao.update(grade);
                    LOG.info("IV applied: student={}, subject={}, original={}",
                        studentUsername, subjectCode, grade.getOriginalValue());
                }
            } else {
                // UNDER LIMIT: Restore original grade if it was set to IV
                if (grade != null && "IV".equalsIgnoreCase(grade.getValue()) && grade.getOriginalValue() != null) {
                    grade.setValue(grade.getOriginalValue());
                    grade.setOriginalValue(null);
                    gradeDao.update(grade);
                    LOG.info("Grade restored from IV: student={}, subject={}, value={}",
                        studentUsername, subjectCode, grade.getValue());
                }
            }
        } catch (Exception e) {
            LOG.warn("IV check failed for {}/{}: {}", studentUsername, subjectCode, e.getMessage());
        }
    }
}
