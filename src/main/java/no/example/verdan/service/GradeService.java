package no.example.verdan.service;

import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.SubjectDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.AttendanceDto;
import no.example.verdan.dto.GradeDto;
import no.example.verdan.model.Grade;
import no.example.verdan.model.Subject;
import no.example.verdan.model.User;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service layer for grade management.
 */
public class GradeService {

    private static final Logger LOG = LoggerFactory.getLogger(GradeService.class);
    private final GradeDao gradeDao;
    private final UserDao userDao;
    private final SubjectDao subjectDao;
    private final SubjectAssignmentDao assignmentDao;

    public GradeService() {
        this(new GradeDao(), new UserDao(), new SubjectDao(), new SubjectAssignmentDao());
    }

    public GradeService(GradeDao gradeDao, UserDao userDao, SubjectDao subjectDao, SubjectAssignmentDao assignmentDao) {
        this.gradeDao = gradeDao;
        this.userDao = userDao;
        this.subjectDao = subjectDao;
        this.assignmentDao = assignmentDao;
    }

    /** Get grades based on user role. */
    public List<GradeDto.Response> getGrades(String role, String username, int institutionId, boolean isSuperAdmin) {
        List<Grade> grades;
        if (isSuperAdmin) {
            grades = gradeDao.findAll();
        } else if ("STUDENT".equalsIgnoreCase(role)) {
            grades = gradeDao.findByStudentUsername(username, institutionId);
        } else if ("TEACHER".equalsIgnoreCase(role)) {
            grades = gradeDao.findForTeacherWithActiveAssignments(username, institutionId);
        } else {
            grades = gradeDao.findAllGrades(institutionId);
        }
        // Enforce IV status in real-time based on current absence data
        enforceIV(grades, institutionId);
        return grades.stream().map(this::toResponse).toList();
    }

    /** Get grade by ID. */
    public GradeDto.Response getGradeById(int id, int institutionId, boolean isSuperAdmin) {
        Grade grade = isSuperAdmin ? gradeDao.find(id) : gradeDao.find(id, institutionId);
        if (grade == null) throw new NotFoundException("Grade not found or access denied");
        return toResponse(grade);
    }

    /** Get grades for a specific student. */
    public List<GradeDto.Response> getGradesByStudent(String username, int institutionId) {
        List<Grade> grades = gradeDao.findByStudentUsername(username, institutionId);
        enforceIV(grades, institutionId);
        return grades.stream().map(this::toResponse).toList();
    }

    /** Get average grades per subject. */
    public List<Map<String, Object>> getAverages(int institutionId) {
        return gradeDao.avgGradePerSubject(institutionId).stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("subject", row[0]);
            map.put("average", row[1]);
            return map;
        }).toList();
    }

    public GradeDto.Response createGrade(GradeDto.Request req, String creatorUsername, String creatorRole, int institutionId) {
        if (!InputValidator.isNotBlank(req.studentUsername()))
            throw new ValidationException("Student username is required");
        if (!InputValidator.isNotBlank(req.subject()))
            throw new ValidationException("Subject code is required");
        if (!InputValidator.isNotBlank(req.value()))
            throw new ValidationException("Grade value is required");

        // Validate grade value against subject level
        Subject subjectEntity = subjectDao.findByCode(req.subject(), institutionId);
        if (subjectEntity != null) {
            validateGradeForLevel(req.value(), subjectEntity.getLevel());
        } else if (gradeDao.parseGradeToDouble(req.value()) == null) {
            throw new ValidationException("Invalid grade value. Use A-F or 1-6.");
        }

        // Block grade if student exceeds absence limit in this subject
        if (isAbsenceLimitExceeded(req.studentUsername(), req.subject(), institutionId)) {
            throw new ValidationException(
                "Kan ikke sette karakter – eleven har overskredet fraværsgrensen i " + req.subject() +
                ". Fravær må reduseres eller gjøres gyldig først.");
        }

        List<User> students = userDao.findByUsername(req.studentUsername(), institutionId);
        if (students.isEmpty())
            throw new NotFoundException("Student not found in your institution");

        // Verify that the student is actually assigned to this subject (directly or via program)
        List<String> assignedSubjects = assignmentDao.subjectsForStudent(req.studentUsername(), institutionId);
        boolean isAssigned = assignedSubjects.stream().anyMatch(s -> s.equalsIgnoreCase(req.subject()));
        if (!isAssigned) {
            // Also check if the student has access via program membership
            isAssigned = assignmentDao.isStudentAssignedViaProgram(req.studentUsername(), req.subject(), institutionId);
        }
        if (!isAssigned) {
            throw new ValidationException("Student is not assigned to subject " + req.subject());
        }

        if (gradeDao.hasGradeForStudentInSubject(req.studentUsername(), req.subject(), institutionId))
            throw new ConflictException("Student already has a grade in this subject");

        // If created by an admin, attribute the grade to the subject's assigned teacher
        String finalTeacherUsername = creatorUsername;
        if (!"TEACHER".equalsIgnoreCase(creatorRole) && !"STUDENT".equalsIgnoreCase(creatorRole)) {
            List<User> teachers = assignmentDao.teachersForSubject(req.subject(), institutionId);
            if (!teachers.isEmpty()) {
                finalTeacherUsername = teachers.get(0).getUsername();
            }
        }

        Grade grade = new Grade();
        grade.setStudent(students.get(0));
        grade.setSubject(InputValidator.sanitize(req.subject()));
        grade.setValue(InputValidator.sanitize(req.value()));
        grade.setTeacherUsername(finalTeacherUsername);
        grade.setDateGiven(LocalDate.now());

        // Determine yearLevel: from request, subject, or student's program
        String yearLevel = req.yearLevel();
        if (yearLevel == null || yearLevel.isBlank()) {
            // Try from subject entity
            if (subjectEntity != null && subjectEntity.getYearLevel() != null) {
                yearLevel = subjectEntity.getYearLevel();
            }
        }
        if (yearLevel == null || yearLevel.isBlank()) {
            // Try from student's current program membership
            var memberDao = new no.example.verdan.dao.ProgramMemberDao();
            var memberships = memberDao.findByUser(students.get(0).getId());
            for (var pm : memberships) {
                if (!pm.isGraduated() && pm.getProgram().getInstitution().getId() == institutionId) {
                    String progName = pm.getProgram().getName();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(progName);
                    if (m.find()) {
                        yearLevel = m.group(1);
                        break;
                    }
                }
            }
        }
        if (yearLevel != null && !yearLevel.isBlank()) {
            grade.setYearLevel(yearLevel.trim());
        }
        
        // Link to institution
        no.example.verdan.model.Institution inst = new no.example.verdan.model.Institution();
        inst.setId(req.institutionId() != null ? req.institutionId() : institutionId);
        grade.setInstitution(inst);

        gradeDao.save(grade);
        LOG.info("Grade created: student={}, subject={}, value={}, teacher={} in institution {}", req.studentUsername(), req.subject(), req.value(), finalTeacherUsername, institutionId);
        return toResponse(grade);
    }

    /** Update a grade. */
    public GradeDto.Response updateGrade(int id, GradeDto.Request req, int institutionId, boolean isSuperAdmin) {
        Grade existing = isSuperAdmin ? gradeDao.find(id) : gradeDao.find(id, institutionId);
        if (existing == null) throw new NotFoundException("Grade not found or access denied");

        // Handle retake (privatist): clear IV status and set new grade
        if (req.retake() != null && req.retake()) {
            existing.setRetake(true);
            existing.setOriginalValue(null); // No longer needed
            if (req.value() != null) {
                // Validate the new grade value
                int instId = existing.getInstitution() != null ? existing.getInstitution().getId() : institutionId;
                Subject subjectEntity = subjectDao.findByCode(existing.getSubject(), instId);
                if (subjectEntity != null) {
                    validateGradeForLevel(req.value(), subjectEntity.getLevel());
                }
                existing.setValue(InputValidator.sanitize(req.value()));
            }
            existing.setDateGiven(LocalDate.now());
            gradeDao.update(existing);
            LOG.info("Retake (privatist) grade set: ID={}, subject={}, value={}", id, existing.getSubject(), existing.getValue());
            return toResponse(existing);
        }

        if (req.value() != null) {
            // Validate against subject level
            int instId = existing.getInstitution() != null ? existing.getInstitution().getId() : institutionId;
            Subject subjectEntity = subjectDao.findByCode(existing.getSubject(), instId);
            if (subjectEntity != null) {
                validateGradeForLevel(req.value(), subjectEntity.getLevel());
            } else if (gradeDao.parseGradeToDouble(req.value()) == null) {
                throw new ValidationException("Invalid grade value");
            }
            existing.setValue(InputValidator.sanitize(req.value()));
        }

        // Allow changing institution
        if (req.institutionId() != null) {
            no.example.verdan.model.Institution newInst = new no.example.verdan.model.Institution();
            newInst.setId(req.institutionId());
            existing.setInstitution(newInst);
        }

        // Allow updating yearLevel
        if (req.yearLevel() != null) {
            existing.setYearLevel(req.yearLevel().isBlank() ? null : req.yearLevel().trim());
        }

        existing.setDateGiven(LocalDate.now());
        gradeDao.update(existing);
        LOG.info("Grade updated: ID={} yearLevel={} in institution {}", id, existing.getYearLevel(), institutionId);
        return toResponse(existing);
    }

    /** Delete a grade. */
    public void deleteGrade(int id, int institutionId, boolean isSuperAdmin) {
        Grade grade = isSuperAdmin ? gradeDao.find(id) : gradeDao.find(id, institutionId);
        if (grade == null) throw new NotFoundException("Grade not found or access denied");
        gradeDao.delete(grade);
        LOG.info("Grade deleted: ID={} in institution {}", id, institutionId);
    }

    private GradeDto.Response toResponse(Grade g) {
        String studentUsername = g.getStudent() != null ? g.getStudent().getUsername() : null;
        String studentName = g.getStudent() != null
                ? (g.getStudent().getFirstName() + " " + g.getStudent().getLastName()).trim()
                : null;
        Integer instId = g.getInstitution() != null ? g.getInstitution().getId() : null;
        String instName = g.getInstitution() != null ? g.getInstitution().getName() : "Default";
        boolean isIV = "IV".equalsIgnoreCase(g.getValue());
        return new GradeDto.Response(g.getId(), studentUsername, studentName,
                g.getSubject(), g.getValue(), g.getDateGiven(), g.getTeacherUsername(),
                instId, instName, g.getYearLevel(),
                g.getOriginalValue(), isIV, g.isRetake());
    }

    /**
     * Check if a student has exceeded the absence limit for a subject.
     * Uses the AttendanceService to calculate per-subject stats.
     */
    private boolean isAbsenceLimitExceeded(String username, String subjectCode, int institutionId) {
        try {
            AttendanceService attendanceService = new AttendanceService();
            List<AttendanceDto.SubjectAbsenceStats> stats = attendanceService.getSubjectAbsenceStats(username, institutionId);
            return stats.stream()
                .filter(s -> s.subjectCode().equalsIgnoreCase(subjectCode))
                .anyMatch(AttendanceDto.SubjectAbsenceStats::overLimit);
        } catch (Exception e) {
            LOG.warn("Could not check absence limit for {}/{}: {}", username, subjectCode, e.getMessage());
            return false; // If check fails, allow grade to be set
        }
    }

    /**
     * Enforce IV (Ikke Vurdert) status on a list of grades in real-time.
     * For each grade, checks the student's current absence in that subject.
     * - If over limit and grade is not IV → set to IV (preserving original in originalValue).
     * - If under limit and grade is IV with originalValue → restore original grade.
     * This runs on every grade retrieval so the data is always consistent.
     */
    private void enforceIV(List<Grade> grades, int institutionId) {
        if (grades == null || grades.isEmpty()) return;

        try {
            AttendanceService attendanceService = new AttendanceService();

            // Group grades by student to avoid redundant absence lookups
            Map<String, List<Grade>> byStudent = new java.util.LinkedHashMap<>();
            for (Grade g : grades) {
                String username = g.getStudent() != null ? g.getStudent().getUsername() : null;
                if (username != null) {
                    byStudent.computeIfAbsent(username, k -> new java.util.ArrayList<>()).add(g);
                }
            }

            for (var entry : byStudent.entrySet()) {
                String username = entry.getKey();
                List<Grade> studentGrades = entry.getValue();

                // Get all absence stats for this student
                List<AttendanceDto.SubjectAbsenceStats> stats;
                try {
                    stats = attendanceService.getSubjectAbsenceStats(username, institutionId);
                } catch (Exception e) {
                    continue; // Skip this student if stats fail
                }

                // Build a quick lookup: subjectCode -> overLimit
                Map<String, Boolean> overLimitMap = new java.util.HashMap<>();
                for (var s : stats) {
                    if (s.maxAbsencePercent() != null) {
                        overLimitMap.put(s.subjectCode().toUpperCase(), s.overLimit());
                    }
                }

                for (Grade g : studentGrades) {
                    // Skip retake (privatist) grades — they are exempt from absence limits
                    if (g.isRetake()) continue;

                    String subjectKey = g.getSubject() != null ? g.getSubject().toUpperCase() : "";
                    Boolean isOverLimit = overLimitMap.get(subjectKey);

                    if (isOverLimit == null) continue; // No limit configured for this subject

                    if (isOverLimit && !"IV".equalsIgnoreCase(g.getValue())) {
                        // EXCEEDED: Apply IV — preserve original grade
                        LOG.info("IV enforced: student={}, subject={}, original={}", username, g.getSubject(), g.getValue());
                        g.setOriginalValue(g.getValue());
                        g.setValue("IV");
                        gradeDao.update(g);
                    } else if (!isOverLimit && "IV".equalsIgnoreCase(g.getValue()) && g.getOriginalValue() != null) {
                        // UNDER LIMIT: Restore from IV
                        LOG.info("IV restored: student={}, subject={}, restored={}", username, g.getSubject(), g.getOriginalValue());
                        g.setValue(g.getOriginalValue());
                        g.setOriginalValue(null);
                        gradeDao.update(g);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("enforceIV failed: {}", e.getMessage());
        }
    }

    /**
     * Validates that the grade value matches the expected scale for the subject level.
     * UNGDOMSSKOLE / VGS → numeric 1-6
     * FAGSKOLE / UNIVERSITET → letter A-F
     */
    private void validateGradeForLevel(String value, String level) {
        if (level == null) return;
        boolean isNumericLevel = "UNGDOMSSKOLE".equalsIgnoreCase(level) || "VGS".equalsIgnoreCase(level);
        String v = value.trim().toUpperCase();

        if (isNumericLevel) {
            try {
                int num = Integer.parseInt(v);
                if (num < 1 || num > 6)
                    throw new ValidationException("Grade must be 1-6 for " + level);
            } catch (NumberFormatException e) {
                throw new ValidationException("Grade must be numeric (1-6) for " + level + ". Got: " + value);
            }
        } else {
            if (!v.matches("[A-F]"))
                throw new ValidationException("Grade must be A-F for " + level + ". Got: " + value);
        }
    }

    // ========================================================================
    //  Education History (cross-institution)
    // ========================================================================

    private static final Map<String, String> LEVEL_LABELS = Map.of(
        "UNGDOMSSKOLE", "Ungdomsskole",
        "VGS", "Videregående",
        "FAGSKOLE", "Fagskole",
        "UNIVERSITET", "Universitet/Høyskole"
    );

    private static final List<String> LEVEL_ORDER = List.of(
        "UNGDOMSSKOLE", "VGS", "FAGSKOLE", "UNIVERSITET"
    );

    /**
     * Get a student's entire education history — all grades grouped by institution level.
     * Used in the GradesPage to show ungdomsskole → VGS → Fagskole/Universitet.
     */
    public List<GradeDto.EducationLevel> getEducationHistory(String username) {
        List<Grade> allGrades = gradeDao.findAllByStudentUsername(username);

        // Group by institution
        Map<Integer, List<Grade>> byInst = new java.util.LinkedHashMap<>();
        for (Grade g : allGrades) {
            byInst.computeIfAbsent(g.getInstitution().getId(), k -> new java.util.ArrayList<>()).add(g);
        }

        // Enforce IV for each institution's grades
        for (var entry : byInst.entrySet()) {
            enforceIV(entry.getValue(), entry.getKey());
        }

        List<GradeDto.EducationLevel> levels = new java.util.ArrayList<>();
        for (var entry : byInst.entrySet()) {
            List<Grade> grades = entry.getValue();
            if (grades.isEmpty()) continue;

            var inst = grades.get(0).getInstitution();
            String level = inst.getLevel() != null ? inst.getLevel() : "UNKNOWN";
            String label = LEVEL_LABELS.getOrDefault(level, level);

            // Calculate average and pass status
            double sum = 0;
            int count = 0;
            boolean allPassing = true;
            // In ungdomsskole, ALL students pass regardless of grades — there is no "ikke bestått"
            boolean isUngdomsskole = "UNGDOMSSKOLE".equalsIgnoreCase(level);
            for (Grade g : grades) {
                // IV = Ikke Vurdert (blocked by absence) → always counts as not passing
                if ("IV".equalsIgnoreCase(g.getValue())) {
                    if (!isUngdomsskole) allPassing = false;
                    continue;
                }
                Double val = gradeDao.parseGradeToDouble(g.getValue());
                if (val != null) {
                    sum += val;
                    count++;
                    if (!isUngdomsskole && val < 2.0) allPassing = false;
                }
            }
            double avg = count > 0 ? Math.round((sum / count) * 100.0) / 100.0 : 0.0;

            List<GradeDto.Response> gradeResponses = grades.stream()
                .map(g -> toResponse(g))
                .toList();

            levels.add(new GradeDto.EducationLevel(
                level, label, inst.getName(), inst.getId(),
                gradeResponses, avg, allPassing
            ));
        }

        // Sort by education level order
        levels.sort((a, b) -> {
            int ia = LEVEL_ORDER.indexOf(a.level());
            int ib = LEVEL_ORDER.indexOf(b.level());
            return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
        });

        return levels;
    }
}
