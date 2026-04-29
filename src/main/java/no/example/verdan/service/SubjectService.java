package no.example.verdan.service;

import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.SubjectDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.SubjectDto;
import no.example.verdan.dto.UserDto;
import no.example.verdan.model.Program;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.Subject;
import no.example.verdan.model.User;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Service layer for subject management.
 */
public class SubjectService {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectService.class);
    private static final Set<String> VALID_LEVELS = Set.of("UNGDOMSSKOLE", "VGS", "FAGSKOLE", "UNIVERSITET");
    private final SubjectDao subjectDao;
    private final SubjectAssignmentDao assignmentDao;
    private final UserDao userDao;
    private final ProgramMemberDao memberDao;
    private final ProgramDao programDao;

    public SubjectService() {
        this(new SubjectDao(), new SubjectAssignmentDao(), new UserDao(), new ProgramMemberDao(), new ProgramDao());
    }

    public SubjectService(SubjectDao subjectDao, SubjectAssignmentDao assignmentDao,
                          UserDao userDao, ProgramMemberDao memberDao, ProgramDao programDao) {
        this.subjectDao = subjectDao;
        this.assignmentDao = assignmentDao;
        this.userDao = userDao;
        this.memberDao = memberDao;
        this.programDao = programDao;
    }

    /** Get all subjects. */
    public List<SubjectDto.Response> getAllSubjects(int institutionId, boolean isSuperAdmin) {
        return getAllSubjects(institutionId, isSuperAdmin, null, null);
    }

    /** Get all subjects, filtered by role. Students/Teachers only see their assigned subjects. */
    public List<SubjectDto.Response> getAllSubjects(int institutionId, boolean isSuperAdmin, String role, String username) {
        if (isSuperAdmin) {
            return subjectDao.findAll().stream().map(this::toResponse).toList();
        }

        List<Subject> subjects = subjectDao.findAll(institutionId);

        // Students only see subjects from programs they are enrolled in
        if ("STUDENT".equalsIgnoreCase(role) && username != null) {
            // Get the student's program memberships to find which subjects they should see
            List<User> students = userDao.findByUsername(username);
            User student = students.isEmpty() ? null : students.get(0);
            if (student != null) {
                List<ProgramMember> memberships = memberDao.findByUser(student.getId());
                java.util.Set<Integer> mySubjectIds = new java.util.HashSet<>();
                for (ProgramMember pm : memberships) {
                    if (pm.isGraduated()) continue; // skip graduated programs
                    Program prog = programDao.findWithSubjects(pm.getProgram().getId());
                    if (prog != null && prog.getInstitution() != null && prog.getInstitution().getId() == institutionId) {
                        for (Subject s : prog.getSubjects()) {
                            mySubjectIds.add(s.getId());
                        }
                    }
                }
                subjects = subjects.stream()
                    .filter(s -> mySubjectIds.contains(s.getId()))
                    .toList();
            }
        }

        // Teachers only see subjects they are assigned to
        if ("TEACHER".equalsIgnoreCase(role) && username != null) {
            java.util.Set<String> assignedCodes = new java.util.HashSet<>(
                    assignmentDao.subjectsForTeacher(username, institutionId));
            subjects = subjects.stream()
                    .filter(s -> assignedCodes.contains(s.getCode()))
                    .toList();
        }

        return subjects.stream().map(this::toResponse).toList();
    }

    /** Get subject by ID. */
    public SubjectDto.Response getSubjectById(int id, int institutionId, boolean isSuperAdmin) {
        Subject subject = isSuperAdmin ? subjectDao.find(id) : subjectDao.find(id, institutionId);
        if (subject == null) throw new NotFoundException("Subject not found or access denied");
        return toResponse(subject);
    }

    /** Search subjects by name/code. */
    public List<SubjectDto.Response> searchSubjects(String query, int institutionId) {
        return subjectDao.search(query, institutionId).stream().map(this::toResponse).toList();
    }

    public SubjectDto.Response createSubject(SubjectDto.Request req, int institutionId, boolean isSuperAdmin) {
        if (!InputValidator.isNotBlank(req.code()) || !InputValidator.isValidSubjectCode(req.code()))
            throw new ValidationException("Invalid subject code (2-20 alphanumeric characters)");
        if (!InputValidator.isNotBlank(req.name()))
            throw new ValidationException("Subject name is required");
        
        if (subjectDao.findByCode(req.code(), institutionId) != null)
            throw new ConflictException("Subject code already exists in this institution");

        // Resolve institution and inherit its level
        int targetInstId = (req.institutionId() != null && isSuperAdmin) ? req.institutionId() : institutionId;
        no.example.verdan.dao.InstitutionDao instDao = new no.example.verdan.dao.InstitutionDao();
        no.example.verdan.model.Institution inst = instDao.find(targetInstId);
        if (inst == null) throw new ValidationException("Institution not found");
        
        String level = inst.getLevel();
        if (level == null || level.isBlank()) level = "UNIVERSITET";

        Subject subject = new Subject();
        subject.setCode(InputValidator.sanitize(req.code()).toUpperCase());
        subject.setName(InputValidator.sanitize(req.name()));
        subject.setDescription(InputValidator.sanitize(req.description()));
        subject.setLevel(level);
        subject.setInstitution(inst);
        if (req.program() != null && !req.program().isBlank()) {
            subject.setProgram(InputValidator.sanitize(req.program()));
        }
        if (req.yearLevel() != null && !req.yearLevel().isBlank()) {
            subject.setYearLevel(req.yearLevel().trim());
        }

        subjectDao.save(subject);
        LOG.info("Subject created: {} ({}) [{}] in institution {}", subject.getCode(), subject.getName(), level, inst.getName());

        if (req.teacherUsername() != null && !req.teacherUsername().isBlank()) {
            try {
                // Ensure the teacher belongs to the same institution
                List<User> list = new UserDao().findByUsername(req.teacherUsername(), institutionId);
                if (!list.isEmpty()) {
                    assignUserToSubject(subject.getCode(), req.teacherUsername(), "TEACHER", institutionId);
                } else {
                    LOG.warn("Teacher {} not found in institution {}, skipping assignment", req.teacherUsername(), institutionId);
                }
            } catch (Exception e) {
                LOG.error("Failed to assign teacher {} during subject creation for {}", req.teacherUsername(), subject.getCode(), e);
            }
        }

        return toResponse(subject);
    }

    /** Update a subject. */
    public SubjectDto.Response updateSubject(int id, SubjectDto.Request req, int institutionId, boolean isSuperAdmin) {
        Subject existing = isSuperAdmin ? subjectDao.find(id) : subjectDao.find(id, institutionId);
        if (existing == null) throw new NotFoundException("Subject not found or access denied");

        if (req.name() != null) existing.setName(InputValidator.sanitize(req.name()));
        if (req.description() != null) existing.setDescription(InputValidator.sanitize(req.description()));
        if (req.program() != null) existing.setProgram(InputValidator.sanitize(req.program()));
        if (req.yearLevel() != null) existing.setYearLevel(req.yearLevel().trim());
        
        // If institution changes, update level to match
        if (req.institutionId() != null && isSuperAdmin) {
            no.example.verdan.dao.InstitutionDao instDao = new no.example.verdan.dao.InstitutionDao();
            no.example.verdan.model.Institution newInst = instDao.find(req.institutionId());
            if (newInst != null) {
                existing.setInstitution(newInst);
                existing.setLevel(newInst.getLevel());
            }
        }

        subjectDao.update(existing);
        LOG.info("Subject updated: {} (ID: {})", existing.getCode(), id);
        return toResponse(existing);
    }

    /** Delete a subject. Cleans up program links and member assignments first. */
    public void deleteSubject(int id, int institutionId, boolean isSuperAdmin) {
        Subject subject = isSuperAdmin ? subjectDao.find(id) : subjectDao.find(id, institutionId);
        if (subject == null) throw new NotFoundException("Subject not found or access denied");

        // Remove subject from all programs (program_subjects FK)
        ProgramDao programDao = new ProgramDao();
        int effInstId = subject.getInstitution() != null ? subject.getInstitution().getId() : institutionId;
        List<no.example.verdan.model.Program> programs = programDao.findByInstitution(effInstId);
        for (var p : programs) {
            if (p.getSubjects().stream().anyMatch(s -> s.getId() == id)) {
                programDao.removeSubject(p.getId(), id);
            }
        }

        // Remove all subject assignments (students/teachers)
        assignmentDao.deleteAssignmentsForSubject(subject.getCode(), effInstId);

        subjectDao.delete(subject);
        LOG.info("Subject deleted: {} (ID: {}) in institution {}", subject.getCode(), id, institutionId);
    }

    private SubjectDto.Response toResponse(Subject s) {
        Integer instId = s.getInstitution() != null ? s.getInstitution().getId() : null;
        String instName = s.getInstitution() != null ? s.getInstitution().getName() : "Default";
        return new SubjectDto.Response(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getLevel(),
                instId, instName, s.getProgram(), s.getYearLevel());
    }

    /** Get all members (students + teachers) for a subject by its code. */
    public SubjectDto.MembersResponse getMembersForSubject(String code, int institutionId) {
        // We might want to verify the subject exists in the institution first
        if (subjectDao.findByCode(code, institutionId) == null) {
            throw new NotFoundException("Subject not found in your institution");
        }
        var students = assignmentDao.studentsForSubject(code, institutionId).stream().map(this::userToResponse).toList();
        var teachers = assignmentDao.teachersForSubject(code, institutionId).stream().map(this::userToResponse).toList();
        return new SubjectDto.MembersResponse(students, teachers);
    }

    /** Assign a user (student or teacher) to a subject. */
    public void assignUserToSubject(String code, String username, String role, int institutionId) {
        if (username == null || username.isBlank()) throw new ValidationException("Username is required");
        if (!"STUDENT".equalsIgnoreCase(role) && !"TEACHER".equalsIgnoreCase(role))
            throw new ValidationException("Role must be STUDENT or TEACHER");
        
        // Verify user belongs to institution
        List<User> list = new UserDao().findByUsername(username, institutionId);
        if (list.isEmpty()) throw new NotFoundException("User not found in your institution");

        if ("STUDENT".equalsIgnoreCase(role)) {
            assignmentDao.assignStudentToSubject(username, code, institutionId);
        } else {
            assignmentDao.assignTeacherToSubject(username, code, institutionId);
        }
        LOG.info("Assigned {} ({}) to subject {} in institution {}", username, role, code, institutionId);
    }

    /** Remove a user from a subject (any role). */
    public void removeUserFromSubject(String code, String username, int institutionId) {
         // Verify user belongs to institution
        List<User> list = new UserDao().findByUsername(username, institutionId);
        if (list.isEmpty()) throw new NotFoundException("User not found in your institution");

        assignmentDao.removeAssignmentsForUserAndSubject(username, code, institutionId);
        LOG.info("Removed {} from subject {} in institution {}", username, code, institutionId);
    }

    private UserDto.Response userToResponse(User u) {
        Integer instId = u.getInstitution() != null ? u.getInstitution().getId() : null;
        String instName = u.getInstitution() != null ? u.getInstitution().getName() : "Default";
        String birthStr = u.getBirthDate() != null ? u.getBirthDate().toString() : null;
        return new UserDto.Response(u.getId(), u.getUsername(), u.getRole(),
                u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                u.getGender(), birthStr, instId, instName);
    }
}
