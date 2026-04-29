package no.example.verdan.service;

import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.SubjectDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.ProgramDto;
import no.example.verdan.model.Institution;
import no.example.verdan.model.Program;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.Subject;
import no.example.verdan.model.User;
import no.example.verdan.model.Grade;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service layer for program (linje/degree/fagskolegrad) management.
 */
public class ProgramService {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramService.class);
    private final ProgramDao programDao;
    private final ProgramMemberDao memberDao;
    private final SubjectDao subjectDao;
    private final SubjectAssignmentDao assignmentDao;
    private final InstitutionDao institutionDao;
    private final UserDao userDao;
    private final GradeDao gradeDao;

    public ProgramService() {
        this(new ProgramDao(), new ProgramMemberDao(), new SubjectDao(),
             new SubjectAssignmentDao(), new InstitutionDao(), new UserDao(), new GradeDao());
    }

    public ProgramService(ProgramDao programDao, ProgramMemberDao memberDao,
                          SubjectDao subjectDao, SubjectAssignmentDao assignmentDao,
                          InstitutionDao institutionDao, UserDao userDao, GradeDao gradeDao) {
        this.programDao = programDao;
        this.memberDao = memberDao;
        this.subjectDao = subjectDao;
        this.assignmentDao = assignmentDao;
        this.institutionDao = institutionDao;
        this.userDao = userDao;
        this.gradeDao = gradeDao;
    }

    // ========================================================================
    //  Program CRUD
    // ========================================================================

    public List<ProgramDto.Response> getAllPrograms(int institutionId, boolean isSuperAdmin) {
        return getAllPrograms(institutionId, isSuperAdmin, null, null);
    }

    public List<ProgramDto.Response> getAllPrograms(int institutionId, boolean isSuperAdmin, String role, String username) {
        if (isSuperAdmin) {
            return programDao.findAll().stream().map(this::toResponse).toList();
        }

        List<Program> programs = programDao.findByInstitution(institutionId);

        // Students and teachers only see programs they are enrolled in
        if (("STUDENT".equalsIgnoreCase(role) || "TEACHER".equalsIgnoreCase(role)) && username != null) {
            var userDao = new no.example.verdan.dao.UserDao();
            var userList = userDao.findByUsername(username);
            if (!userList.isEmpty()) {
                int userId = userList.get(0).getId();
                List<ProgramMember> memberships = memberDao.findByUser(userId);
                java.util.Set<Integer> myProgramIds = memberships.stream()
                        .map(pm -> pm.getProgram().getId())
                        .collect(java.util.stream.Collectors.toSet());
                programs = programs.stream()
                        .filter(p -> myProgramIds.contains(p.getId()))
                        .toList();
            }
        }

        return programs.stream().map(this::toResponse).toList();
    }

    public ProgramDto.Response getProgram(int id, int institutionId, boolean isSuperAdmin) {
        Program p = programDao.findWithSubjects(id);
        if (p == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && p.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }
        return toResponse(p);
    }

    public ProgramDto.Response createProgram(ProgramDto.Request req, int institutionId, boolean isSuperAdmin) {
        if (req.name() == null || req.name().isBlank())
            throw new ValidationException("Program name is required");

        int targetInstId = (req.institutionId() != null && isSuperAdmin) ? req.institutionId() : institutionId;
        Institution inst = institutionDao.find(targetInstId);
        if (inst == null) throw new ValidationException("Institution not found");

        if (programDao.findByName(req.name(), targetInstId) != null) {
            throw new ConflictException("A program with this name already exists in this institution");
        }

        Program program = new Program();
        program.setName(InputValidator.sanitize(req.name()));
        program.setDescription(req.description() != null ? InputValidator.sanitize(req.description()) : null);
        program.setInstitution(inst);
        program.setMinGpa(req.minGpa());
        program.setMaxStudents(req.maxStudents());
        program.setPrerequisites(req.prerequisites() != null ? InputValidator.sanitize(req.prerequisites()) : null);

        // Attendance settings — VGS always has 10% limit (90% attendance)
        if ("VGS".equalsIgnoreCase(inst.getLevel())) {
            program.setAttendanceRequired(true);
            program.setMinAttendancePct(90);
        } else {
            program.setAttendanceRequired(req.attendanceRequired() != null && req.attendanceRequired());
            program.setMinAttendancePct(req.minAttendancePct());
        }

        // Program type (only relevant for VGS: STUDIEFORBEREDENDE or YRKESFAG)
        if (req.programType() != null && !req.programType().isBlank()) {
            program.setProgramType(req.programType().toUpperCase());
        }

        programDao.save(program);
        LOG.info("Program created: '{}' in institution '{}'", program.getName(), inst.getName());
        return toResponse(program);
    }

    public ProgramDto.Response updateProgram(int id, ProgramDto.Request req, int institutionId, boolean isSuperAdmin) {
        Program existing = programDao.findWithSubjects(id);
        if (existing == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && existing.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        if (req.name() != null && !req.name().isBlank()) {
            if (!req.name().equalsIgnoreCase(existing.getName())) {
                int instId = existing.getInstitution().getId();
                if (programDao.findByName(req.name(), instId) != null) {
                    throw new ConflictException("A program with this name already exists");
                }
            }
            existing.setName(InputValidator.sanitize(req.name()));
        }
        if (req.description() != null) {
            existing.setDescription(InputValidator.sanitize(req.description()));
        }
        existing.setMinGpa(req.minGpa());
        existing.setMaxStudents(req.maxStudents());
        existing.setPrerequisites(req.prerequisites() != null ? InputValidator.sanitize(req.prerequisites()) : null);

        // Attendance settings — VGS programs always have fixed 10% limit
        String instLevel = existing.getInstitution() != null ? existing.getInstitution().getLevel() : null;
        if ("VGS".equalsIgnoreCase(instLevel)) {
            existing.setAttendanceRequired(true);
            existing.setMinAttendancePct(90);
        } else {
            existing.setAttendanceRequired(req.attendanceRequired() != null && req.attendanceRequired());
            existing.setMinAttendancePct(req.minAttendancePct());
        }

        // Program type
        if (req.programType() != null) {
            existing.setProgramType(req.programType().isBlank() ? null : req.programType().toUpperCase());
        }

        programDao.update(existing);
        LOG.info("Program updated: '{}' (ID: {})", existing.getName(), id);
        return toResponse(existing);
    }

    public void deleteProgram(int id, int institutionId, boolean isSuperAdmin) {
        Program existing = programDao.findWithSubjects(id);
        if (existing == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && existing.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        // Remove all members first
        memberDao.deleteAllForProgram(id);
        // Remove subject links (not the subjects)
        programDao.deleteWithLinks(id);
        LOG.info("Program deleted: '{}' (ID: {})", existing.getName(), id);
    }

    // ========================================================================
    //  Subject linking
    // ========================================================================

    public void addSubjectToProgram(int programId, int subjectId, int institutionId, boolean isSuperAdmin) {
        Program program = programDao.findWithSubjects(programId);
        if (program == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && program.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        Subject subject = subjectDao.find(subjectId);
        if (subject == null) throw new NotFoundException("Subject not found");

        if (subject.getInstitution() != null && subject.getInstitution().getId() != program.getInstitution().getId()) {
            throw new ValidationException("Subject does not belong to the same institution");
        }

        programDao.addSubject(programId, subjectId);

        // Auto-enroll existing program members in this new subject
        List<ProgramMember> members = memberDao.findActiveByProgram(programId);
        int instId = program.getInstitution().getId();
        for (ProgramMember pm : members) {
            String role = pm.getRole().toUpperCase();
            if ("STUDENT".equals(role)) {
                assignmentDao.assignStudentToSubject(pm.getUser().getUsername(), subject.getCode(), instId);
            }
            // Teachers are NOT auto-enrolled — they must be assigned to individual subjects manually
        }

        LOG.info("Subject '{}' added to program '{}' — {} members auto-enrolled",
                subject.getCode(), program.getName(), members.size());
    }

    public void removeSubjectFromProgram(int programId, int subjectId, int institutionId, boolean isSuperAdmin) {
        Program program = programDao.findWithSubjects(programId);
        if (program == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && program.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        programDao.removeSubject(programId, subjectId);
        LOG.info("Subject {} removed from program '{}'", subjectId, program.getName());
    }

    // ========================================================================
    //  Member management
    // ========================================================================

    /** Get all members of a program. */
    public ProgramDto.MembersResponse getMembers(int programId, int institutionId, boolean isSuperAdmin) {
        Program program = programDao.findWithSubjects(programId);
        if (program == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && program.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        List<ProgramMember> all = memberDao.findByProgram(programId);

        // Split into active (non-graduated) and graduated
        List<ProgramDto.MemberResponse> students = all.stream()
            .filter(pm -> "STUDENT".equalsIgnoreCase(pm.getRole()) && !pm.isGraduated())
            .map(this::toMemberResponse)
            .toList();
        List<ProgramDto.MemberResponse> teachers = all.stream()
            .filter(pm -> "TEACHER".equalsIgnoreCase(pm.getRole()))
            .map(this::toMemberResponse)
            .toList();

        return new ProgramDto.MembersResponse(students, teachers, students.size() + teachers.size());
    }

    /**
     * Add a user to a program.
     * Students are automatically enrolled in all subjects within the program.
     * Teachers are NOT auto-enrolled — they must be assigned to individual subjects manually.
     */
    public ProgramDto.MemberResponse addMember(int programId, ProgramDto.MemberRequest req,
                                                int institutionId, boolean isSuperAdmin) {
        Program program = programDao.findWithSubjects(programId);
        if (program == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && program.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        User user = userDao.find(req.userId());
        if (user == null) throw new NotFoundException("User not found");

        // Verify user belongs to same institution
        if (user.getInstitution() != null && user.getInstitution().getId() != program.getInstitution().getId()) {
            throw new ValidationException("User does not belong to the same institution as this program");
        }

        // Check already a member
        if (memberDao.findByProgramAndUser(programId, req.userId()) != null) {
            throw new ConflictException("User is already a member of this program");
        }

        String role = req.role() != null ? req.role().toUpperCase() : "STUDENT";
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            throw new ValidationException("Role must be STUDENT or TEACHER");
        }

        // Students can only be in ONE active program at a time
        if ("STUDENT".equals(role)) {
            List<ProgramMember> existingMemberships = memberDao.findByUser(req.userId());
            for (ProgramMember existing : existingMemberships) {
                if ("STUDENT".equalsIgnoreCase(existing.getRole())
                        && !existing.isGraduated()
                        && existing.getProgram().getInstitution().getId() == program.getInstitution().getId()) {
                    throw new ConflictException(
                        "Eleven er allerede aktiv i programmet \"" + existing.getProgram().getName()
                        + "\" (" + existing.getYearLevel() + "). "
                        + "Fjern eleven derfra før du legger til i et nytt program."
                    );
                }
            }
        }

        ProgramMember member = new ProgramMember(program, user, role, req.yearLevel());
        memberDao.save(member);

        // Auto-enroll in subjects matching the student's year level ONLY (not all subjects)
        // Teachers are NOT auto-enrolled — they must be assigned to individual subjects manually
        int instId = program.getInstitution().getId();
        if ("STUDENT".equals(role)) {
            String yearLevel = req.yearLevel();
            int enrolled = 0;
            for (Subject subject : program.getSubjects()) {
                // Only enroll in subjects that match the student's year level
                if (yearLevel != null && !yearLevel.isBlank()) {
                    if (yearLevel.equals(subject.getYearLevel())) {
                        assignmentDao.assignStudentToSubject(user.getUsername(), subject.getCode(), instId);
                        enrolled++;
                    }
                } else {
                    // No year level specified — enroll in subjects without a year level
                    if (subject.getYearLevel() == null || subject.getYearLevel().isBlank()) {
                        assignmentDao.assignStudentToSubject(user.getUsername(), subject.getCode(), instId);
                        enrolled++;
                    }
                }
            }
            LOG.info("Student '{}' added to program '{}' (year: {}) — enrolled in {} of {} subjects",
                    user.getUsername(), program.getName(), yearLevel, enrolled, program.getSubjects().size());
        } else {
            // Teachers are NOT auto-enrolled in subjects — they must be assigned manually
            // This allows admins to control exactly which subjects each teacher teaches
            LOG.info("Teacher '{}' added to program '{}' — no auto subject assignment (manual assignment required)",
                    user.getUsername(), program.getName());
        }

        return toMemberResponse(member);
    }

    /**
     * Remove a user from a program. Removes their subject assignments for subjects in this program
     * ONLY if they are not in another program that also has those subjects.
     */
    public void removeMember(int programId, int userId, int institutionId, boolean isSuperAdmin) {
        Program program = programDao.findWithSubjects(programId);
        if (program == null) throw new NotFoundException("Program not found");
        if (!isSuperAdmin && program.getInstitution().getId() != institutionId) {
            throw new NotFoundException("Program not found");
        }

        ProgramMember existing = memberDao.findByProgramAndUser(programId, userId);
        if (existing == null) throw new NotFoundException("Member not found in this program");

        User user = existing.getUser();
        int instId = program.getInstitution().getId();

        // Find all other programs this user is in
        List<ProgramMember> otherMemberships = memberDao.findByUser(userId).stream()
            .filter(pm -> pm.getProgram().getId() != programId)
            .toList();

        // Collect subject codes from other programs (these should NOT be removed)
        java.util.Set<String> protectedSubjects = new java.util.HashSet<>();
        for (ProgramMember pm : otherMemberships) {
            Program otherProg = programDao.findWithSubjects(pm.getProgram().getId());
            if (otherProg != null) {
                for (Subject s : otherProg.getSubjects()) {
                    protectedSubjects.add(s.getCode());
                }
            }
        }

        // Remove subject assignments only for subjects unique to this program
        for (Subject subject : program.getSubjects()) {
            if (!protectedSubjects.contains(subject.getCode())) {
                assignmentDao.removeAssignmentsForUserAndSubject(user.getUsername(), subject.getCode(), instId);
            }
        }

        memberDao.deleteMembership(programId, userId);
        LOG.info("User '{}' removed from program '{}'", user.getUsername(), program.getName());
    }

    // ========================================================================
    //  Graduated students
    // ========================================================================

    /** Check if a student is graduated from any of their programs. */
    public boolean isStudentGraduated(int userId) {
        List<ProgramMember> memberships = memberDao.findByUser(userId);
        return memberships.stream()
            .filter(pm -> "STUDENT".equalsIgnoreCase(pm.getRole()))
            .anyMatch(ProgramMember::isGraduated);
    }

    /** Get all graduated students in the institution (excluding archived and transferred). */
    public List<ProgramDto.GraduatedResponse> getGraduatedStudents(int institutionId) {
        List<ProgramMember> graduated = memberDao.findGraduatedByInstitution(institutionId);
        List<ProgramMember> filtered = graduated.stream()
            .filter(pm -> "STUDENT".equalsIgnoreCase(pm.getRole()))
            .filter(pm -> !pm.isArchived())
            .filter(pm -> {
                User u = pm.getUser();
                if (u.getInstitution() == null) return true;
                return u.getInstitution().getId() == institutionId;
            })
            .toList();

        // Re-evaluate diploma eligibility for non-yrkesfag students
        // This catches retake (privatist) grades that were set after graduation
        reEvaluateDiplomaEligibility(filtered);

        return filtered.stream().map(this::toGraduatedResponse).toList();
    }

    /** Get all archived graduated students in the institution. */
    public List<ProgramDto.GraduatedResponse> getArchivedStudents(int institutionId) {
        List<ProgramMember> graduated = memberDao.findGraduatedByInstitution(institutionId);
        return graduated.stream()
            .filter(pm -> "STUDENT".equalsIgnoreCase(pm.getRole()))
            .filter(ProgramMember::isArchived)
            .filter(pm -> {
                User u = pm.getUser();
                if (u.getInstitution() == null) return true;
                return u.getInstitution().getId() == institutionId;
            })
            .map(this::toGraduatedResponse)
            .toList();
    }

    /** Archive a graduated student. */
    public void archiveStudent(int programId, int userId) {
        ProgramMember pm = memberDao.findByProgramAndUser(programId, userId);
        if (pm == null) throw new NotFoundException("Member not found");
        if (!pm.isGraduated()) throw new ValidationException("Only graduated students can be archived");
        pm.setArchived(true);
        memberDao.update(pm);
        LOG.info("Archived graduated student: userId={}, programId={}", userId, programId);
    }

    /** Restore an archived student back to the active graduated list. */
    public void restoreStudent(int programId, int userId) {
        ProgramMember pm = memberDao.findByProgramAndUser(programId, userId);
        if (pm == null) throw new NotFoundException("Member not found");
        pm.setArchived(false);
        memberDao.update(pm);
        LOG.info("Restored archived student: userId={}, programId={}", userId, programId);
    }

    /** Bulk archive all non-archived graduated students in the institution. */
    public int bulkArchiveAll(int institutionId) {
        List<ProgramMember> graduated = memberDao.findGraduatedByInstitution(institutionId);
        int count = 0;
        for (ProgramMember pm : graduated) {
            if (!"STUDENT".equalsIgnoreCase(pm.getRole())) continue;
            if (pm.isArchived()) continue;
            // Skip students who transferred to another institution
            User u = pm.getUser();
            if (u.getInstitution() != null && u.getInstitution().getId() != institutionId) continue;
            pm.setArchived(true);
            memberDao.update(pm);
            count++;
        }
        LOG.info("Bulk archived {} graduated students in institution {}", count, institutionId);
        return count;
    }

    /**
     * Re-evaluate diploma eligibility for graduated students.
     * If a student previously had diplomaEligible=false but now passes all subjects
     * (e.g. after a retake/privatist exam), upgrade them to diplomaEligible=true.
     * Yrkesfag students always get kompetansebevis (not vitnemål), so skip them.
     */
    private void reEvaluateDiplomaEligibility(List<ProgramMember> members) {
        for (ProgramMember pm : members) {
            // Only re-evaluate students who currently DON'T have a diploma
            if (pm.isDiplomaEligible()) continue;

            // Yrkesfag always gets kompetansebevis, not vitnemål — skip
            Program program = programDao.findWithSubjects(pm.getProgram().getId());
            if (program == null) continue;
            if ("YRKESFAG".equalsIgnoreCase(program.getProgramType())) continue;

            // Re-check: do all subjects now have passing grades?
            if (checkAllSubjectsNowPassed(pm, program)) {
                pm.setDiplomaEligible(true);
                memberDao.update(pm);
                LOG.info("Diploma re-evaluated: student '{}' now passes all subjects in '{}' — upgraded to vitnemål",
                    pm.getUser().getUsername(), program.getName());
            }
        }
    }

    /**
     * Check if a graduated student now passes all subjects (including retake grades).
     */
    private boolean checkAllSubjectsNowPassed(ProgramMember pm, Program program) {
        if (program.getSubjects() == null || program.getSubjects().isEmpty()) return true;

        int instId = program.getInstitution().getId();
        String username = pm.getUser().getUsername();
        List<Grade> grades = gradeDao.findByStudentUsername(username, instId);

        Set<String> passedSubjects = new HashSet<>();
        for (Grade g : grades) {
            if ("IV".equalsIgnoreCase(g.getValue())) continue;
            Double val = gradeDao.parseGradeToDouble(g.getValue());
            if (val != null && val >= 2.0) {
                passedSubjects.add(g.getSubject());
            }
        }

        for (Subject subject : program.getSubjects()) {
            if (!passedSubjects.contains(subject.getCode())) {
                return false;
            }
        }
        return true;
    }

    private ProgramDto.GraduatedResponse toGraduatedResponse(ProgramMember pm) {
        User u = pm.getUser();
        return new ProgramDto.GraduatedResponse(
            u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(), u.getEmail(),
            pm.getProgram().getId(), pm.getProgram().getName(),
            pm.getYearLevel(), pm.isDiplomaEligible(),
            pm.getEnrolledAt() != null ? pm.getEnrolledAt().toString() : null,
            pm.getProgram().getProgramType()
        );
    }

    // ========================================================================
    //  Mappers
    // ========================================================================

    private ProgramDto.Response toResponse(Program p) {
        Integer instId = p.getInstitution() != null ? p.getInstitution().getId() : null;
        String instName = p.getInstitution() != null ? p.getInstitution().getName() : "Default";

        List<ProgramDto.SubjectSummary> subjects = p.getSubjects().stream()
            .sorted(Comparator
                .comparing((Subject s) -> s.getYearLevel() == null ? "" : s.getYearLevel())
                .thenComparing(Subject::getCode))
            .map(s -> new ProgramDto.SubjectSummary(s.getId(), s.getCode(), s.getName(), s.getYearLevel()))
            .toList();

        return new ProgramDto.Response(p.getId(), p.getName(), p.getDescription(),
                instId, instName, p.getMinGpa(), p.getMaxStudents(), p.getPrerequisites(),
                p.isAttendanceRequired(), p.getMinAttendancePct(), p.getProgramType(), subjects);
    }

    private ProgramDto.MemberResponse toMemberResponse(ProgramMember pm) {
        User u = pm.getUser();
        return new ProgramDto.MemberResponse(
            pm.getId(), u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
            u.getEmail(), pm.getRole(), pm.getYearLevel(),
            pm.getEnrolledAt() != null ? pm.getEnrolledAt().toString() : null,
            pm.isGraduated()
        );
    }
}
