package no.example.verdan.service;

import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.AttendanceDao;
import no.example.verdan.model.Attendance;
import no.example.verdan.model.Grade;
import no.example.verdan.model.Program;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for promoting students to the next year level.
 * Handles automatic progression across all institution types.
 */
public class PromotionService {

    private static final Logger LOG = LoggerFactory.getLogger(PromotionService.class);
    private final ProgramMemberDao memberDao;
    private final ProgramDao programDao;
    private final SubjectAssignmentDao assignmentDao;
    private final GradeDao gradeDao;
    private final AttendanceDao attendanceDao;

    /** Statuses that count as unexcused absence. */
    private static final Set<String> UNEXCUSED_STATUSES = Set.of("Absent", "Late", "Sick");

    /** Levels where diploma/degree is ALWAYS granted (even with failing grades). */
    private static final Set<String> ALWAYS_DIPLOMA = Set.of("UNGDOMSSKOLE");

    /** Year level progression maps per institution level. */
    private static final Map<String, LinkedHashMap<String, String>> PROGRESSION = new HashMap<>();
    /** Final year levels per institution level (students who complete these graduate). */
    private static final Map<String, Set<String>> FINAL_YEARS = new HashMap<>();

    static {
        // Ungdomsskole: 8 → 9 → 10 → graduated
        LinkedHashMap<String, String> ungdom = new LinkedHashMap<>();
        ungdom.put("8", "9");
        ungdom.put("9", "10");
        PROGRESSION.put("UNGDOMSSKOLE", ungdom);
        FINAL_YEARS.put("UNGDOMSSKOLE", Set.of("10"));

        // VGS: VG1 → VG2 (always)
        // VG2 → VG3 only for studieforberedende (programs with VG3 subjects)
        // VG2 is ALSO a final year for yrkesfag (programs without VG3 subjects → kompetansebevis)
        // VG3_PABYGG is for yrkesfag students who apply to påbygg via the portal
        LinkedHashMap<String, String> vgs = new LinkedHashMap<>();
        vgs.put("VG1", "VG2");
        vgs.put("VG2", "VG3"); // Dynamic: skipped for yrkesfag (see isDynamicFinalYear)
        PROGRESSION.put("VGS", vgs);
        FINAL_YEARS.put("VGS", Set.of("VG3", "VG3_PABYGG"));

        // Fagskole: 1 → 2 → graduated
        LinkedHashMap<String, String> fagskole = new LinkedHashMap<>();
        fagskole.put("1", "2");
        PROGRESSION.put("FAGSKOLE", fagskole);
        FINAL_YEARS.put("FAGSKOLE", Set.of("2"));

        // Universitet: BACHELOR_1 → 2 → 3 (grad), MASTER_1 → 2 (grad), PHD_1 → 2 → 3 (grad)
        // Each degree level is separate — students apply via admissions to move between them
        LinkedHashMap<String, String> uni = new LinkedHashMap<>();
        uni.put("BACHELOR_1", "BACHELOR_2");
        uni.put("BACHELOR_2", "BACHELOR_3");
        uni.put("MASTER_1", "MASTER_2");
        uni.put("PHD_1", "PHD_2");
        uni.put("PHD_2", "PHD_3");
        PROGRESSION.put("UNIVERSITET", uni);
        FINAL_YEARS.put("UNIVERSITET", Set.of("BACHELOR_3", "MASTER_2", "PHD_3"));
    }

    public PromotionService() {
        this(new ProgramMemberDao(), new ProgramDao(), new SubjectAssignmentDao(), new GradeDao(), new AttendanceDao());
    }

    public PromotionService(ProgramMemberDao memberDao, ProgramDao programDao,
                            SubjectAssignmentDao assignmentDao, GradeDao gradeDao,
                            AttendanceDao attendanceDao) {
        this.memberDao = memberDao;
        this.programDao = programDao;
        this.assignmentDao = assignmentDao;
        this.gradeDao = gradeDao;
        this.attendanceDao = attendanceDao;
    }

    /**
     * Preview what will happen when students are promoted.
     * Returns a list of actions without executing them.
     */
    public PromotionPreview preview(int institutionId, String instLevel) {
        LinkedHashMap<String, String> progression = PROGRESSION.get(instLevel);
        Set<String> finalYears = FINAL_YEARS.getOrDefault(instLevel, Set.of());

        if (progression == null) {
            return new PromotionPreview(List.of(), List.of(), instLevel, List.of());
        }

        // Load all programs for this institution (needed for target program lookup)
        List<Program> allPrograms = programDao.findByInstitution(institutionId);

        List<PromotionAction> promotions = new ArrayList<>();
        List<PromotionAction> graduations = new ArrayList<>();
        Set<String> missingClasses = new LinkedHashSet<>();

        // Check students at final year levels (will graduate)
        for (String finalYear : finalYears) {
            List<ProgramMember> members = memberDao.findActiveByInstitutionAndYear(institutionId, finalYear);
            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    graduations.add(new PromotionAction(
                        pm.getUser().getId(),
                        pm.getUser().getUsername(),
                        pm.getUser().getFirstName() + " " + pm.getUser().getLastName(),
                        pm.getProgram().getId(),
                        pm.getProgram().getName(),
                        finalYear,
                        null,
                        true,
                        0, null
                    ));
                }
            }
        }

        // Check students who will be promoted to next year
        for (Map.Entry<String, String> entry : progression.entrySet()) {
            String fromYear = entry.getKey();
            String toYear = entry.getValue();
            List<ProgramMember> members = memberDao.findActiveByInstitutionAndYear(institutionId, fromYear);
            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    // Dynamic final year check: ONLY for VGS yrkesfag (VG2 with no VG3)
                    // Other levels (ungdomsskole, fagskole, uni) always follow standard progression
                    if (isDynamicFinalYear(pm.getProgram(), fromYear, toYear, instLevel)) {
                        graduations.add(new PromotionAction(
                            pm.getUser().getId(),
                            pm.getUser().getUsername(),
                            pm.getUser().getFirstName() + " " + pm.getUser().getLastName(),
                            pm.getProgram().getId(),
                            pm.getProgram().getName(),
                            fromYear,
                            null,
                            true,
                            0, null
                        ));
                        continue;
                    }

                    Program targetProgram = findTargetProgram(pm.getProgram(), fromYear, toYear, allPrograms);

                    // Collect missing classes instead of throwing — allows preview to show all students
                    String srcName = pm.getProgram().getName();
                    if (targetProgram == null && srcName.startsWith(fromYear) && srcName.length() > fromYear.length()) {
                        String expectedTarget = toYear + srcName.substring(fromYear.length());
                        missingClasses.add(expectedTarget);
                    }

                    promotions.add(new PromotionAction(
                        pm.getUser().getId(),
                        pm.getUser().getUsername(),
                        pm.getUser().getFirstName() + " " + pm.getUser().getLastName(),
                        pm.getProgram().getId(),
                        pm.getProgram().getName(),
                        fromYear,
                        toYear,
                        false,
                        targetProgram != null ? targetProgram.getId() : pm.getProgram().getId(),
                        targetProgram != null ? targetProgram.getName() : pm.getProgram().getName()
                    ));
                }
            }
        }

        return new PromotionPreview(promotions, graduations, instLevel, List.copyOf(missingClasses));
    }

    /**
     * Execute promotion: advance all active students to their next year level.
     * Students at final year level are marked as graduated.
     */
    public PromotionResult execute(int institutionId, String instLevel) {
        LinkedHashMap<String, String> progression = PROGRESSION.get(instLevel);
        Set<String> finalYears = FINAL_YEARS.getOrDefault(instLevel, Set.of());

        if (progression == null) {
            throw new ValidationException("Ingen progresjon definert for nivå: " + instLevel);
        }

        // Load all programs for target lookup
        List<Program> allPrograms = programDao.findByInstitution(institutionId);

        int promoted = 0;
        int graduatedWithDiploma = 0;
        int graduatedWithoutDiploma = 0;

        // Handle graduations first (final year students)
        for (String finalYear : finalYears) {
            List<ProgramMember> members = memberDao.findActiveByInstitutionAndYear(institutionId, finalYear);
            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    pm.setGraduated(true);

                    // Determine diploma eligibility based on institution level rules
                    boolean eligible;
                    if (ALWAYS_DIPLOMA.contains(instLevel)) {
                        // Ungdomsskole: always gets vitnemål, even with failing grades
                        eligible = true;
                    } else {
                        // VGS, Fagskole, Universitet: must pass ALL subjects in the program
                        eligible = checkAllSubjectsPassed(pm);
                    }
                    pm.setDiplomaEligible(eligible);
                    memberDao.update(pm);

                    if (eligible) {
                        graduatedWithDiploma++;
                        LOG.info("Student '{}' graduated WITH diploma from program '{}'",
                            pm.getUser().getUsername(), pm.getProgram().getName());
                    } else {
                        graduatedWithoutDiploma++;
                        LOG.info("Student '{}' graduated WITHOUT diploma from program '{}' (has failing grades)",
                            pm.getUser().getUsername(), pm.getProgram().getName());
                    }

                    // Remove subject assignments for ALL subjects in this program
                    removeSubjectAssignmentsForGraduate(pm);
                }
            }
        }

        // Process promotions in reverse order (10→graduated before 9→10)
        // to avoid conflicts
        List<Map.Entry<String, String>> entries = new ArrayList<>(progression.entrySet());
        Collections.reverse(entries);

        for (Map.Entry<String, String> entry : entries) {
            String fromYear = entry.getKey();
            String toYear = entry.getValue();
            List<ProgramMember> members = memberDao.findActiveByInstitutionAndYear(institutionId, fromYear);

            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    // Dynamic final year check: ONLY for VGS yrkesfag (VG2 with no VG3)
                    if (isDynamicFinalYear(pm.getProgram(), fromYear, toYear, instLevel)) {
                        pm.setGraduated(true);
                        // Yrkesfag graduates get kompetansebevis, not vitnemål
                        // They can apply to påbygg later if they want studiekompetanse
                        boolean eligible = checkAllSubjectsPassed(pm);
                        pm.setDiplomaEligible(false); // kompetansebevis, not vitnemål
                        memberDao.update(pm);

                        if (eligible) {
                            graduatedWithDiploma++; // counts as graduated (with kompetansebevis)
                            LOG.info("Yrkesfag student '{}' graduated with kompetansebevis from program '{}'",
                                pm.getUser().getUsername(), pm.getProgram().getName());
                        } else {
                            graduatedWithoutDiploma++;
                            LOG.info("Yrkesfag student '{}' graduated WITHOUT kompetansebevis from program '{}' (failing grades)",
                                pm.getUser().getUsername(), pm.getProgram().getName());
                        }

                        // Remove subject assignments for ALL subjects in this program
                        removeSubjectAssignmentsForGraduate(pm);
                        continue;
                    }

                    Program sourceProgram = pm.getProgram();
                    Program targetProgram = findTargetProgram(sourceProgram, fromYear, toYear, allPrograms);

                    // Block if program name implies a class suffix (e.g. "8B") but target doesn't exist (e.g. "9B")
                    String srcName = sourceProgram.getName();
                    if (targetProgram == null && srcName.startsWith(fromYear) && srcName.length() > fromYear.length()) {
                        String expectedTarget = toYear + srcName.substring(fromYear.length());
                        throw new ValidationException(
                            "Kan ikke flytte opp: Klassen '" + expectedTarget + "' er ikke opprettet. Opprett klassen først."
                        );
                    }

                    if (targetProgram != null && targetProgram.getId() != sourceProgram.getId()) {
                        // Move student to different program
                        moveStudentToProgram(pm, targetProgram, toYear);
                        LOG.info("Student '{}' promoted from '{}' ({}) to '{}' ({})",
                            pm.getUser().getUsername(), sourceProgram.getName(), fromYear,
                            targetProgram.getName(), toYear);
                    } else {
                        // Stay in same program, just update year level
                        pm.setYearLevel(toYear);
                        memberDao.update(pm);
                        LOG.info("Student '{}' promoted from {} to {} in program '{}'",
                            pm.getUser().getUsername(), fromYear, toYear, sourceProgram.getName());
                    }

                    // Update subject assignments: remove old year-level subjects, add new ones
                    Program programForSubjects = targetProgram != null ? targetProgram : sourceProgram;
                    updateSubjectAssignmentsForPromotion(
                        pm.getUser().getUsername(),
                        sourceProgram, programForSubjects,
                        fromYear, toYear
                    );

                    promoted++;
                }
            }
        }

        LOG.info("Promotion complete for institution {}: {} promoted, {} graduated with diploma, {} without",
            institutionId, promoted, graduatedWithDiploma, graduatedWithoutDiploma);

        return new PromotionResult(promoted, graduatedWithDiploma + graduatedWithoutDiploma,
            graduatedWithDiploma, graduatedWithoutDiploma, instLevel);
    }

    /**
     * Undo the last promotion: reverse all year-level changes.
     * - Promoted students are moved back to their previous year level.
     * - Graduated students are un-graduated and moved back to their final year level.
     */
    public PromotionResult undoPromotion(int institutionId, String instLevel) {
        LinkedHashMap<String, String> progression = PROGRESSION.get(instLevel);
        Set<String> finalYears = FINAL_YEARS.getOrDefault(instLevel, Set.of());

        if (progression == null) {
            throw new ValidationException("Ingen progresjon definert for nivå: " + instLevel);
        }

        List<Program> allPrograms = programDao.findByInstitution(institutionId);

        // Build reverse map: toYear → fromYear
        LinkedHashMap<String, String> reverseProgression = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : progression.entrySet()) {
            reverseProgression.put(entry.getValue(), entry.getKey());
        }

        int demoted = 0;
        int ungraduated = 0;

        // Track user IDs that were un-graduated so we don't also demote them
        Set<Integer> ungraduatedUserIds = new HashSet<>();

        // Un-graduate students who were graduated (they should be at a final year level with graduated=true)
        for (String finalYear : finalYears) {
            List<ProgramMember> members = memberDao.findGraduatedByInstitutionAndYear(institutionId, finalYear);
            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    pm.setGraduated(false);
                    pm.setDiplomaEligible(false);
                    memberDao.update(pm);
                    ungraduated++;
                    ungraduatedUserIds.add(pm.getUser().getId());
                    LOG.info("Undo: un-graduated student '{}' in program '{}' at year {}",
                        pm.getUser().getUsername(), pm.getProgram().getName(), finalYear);
                }
            }
        }

        // Also un-graduate yrkesfag students who graduated dynamically from VG2
        // (they are graduated=true but at VG2, not at a static final year)
        if ("VGS".equals(instLevel)) {
            for (Map.Entry<String, String> entry : progression.entrySet()) {
                String fromYear = entry.getKey();
                List<ProgramMember> members = memberDao.findGraduatedByInstitutionAndYear(institutionId, fromYear);
                for (ProgramMember pm : members) {
                    if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                        pm.setGraduated(false);
                        pm.setDiplomaEligible(false);
                        memberDao.update(pm);
                        ungraduated++;
                        ungraduatedUserIds.add(pm.getUser().getId());
                        LOG.info("Undo: un-graduated yrkesfag student '{}' at year {}",
                            pm.getUser().getUsername(), fromYear);
                    }
                }
            }
        }

        // Demote promoted students (reverse order: lower years first so we don't have conflicts)
        for (Map.Entry<String, String> entry : reverseProgression.entrySet()) {
            String currentYear = entry.getKey();   // where they are now (toYear)
            String previousYear = entry.getValue(); // where they came from (fromYear)

            List<ProgramMember> members = memberDao.findActiveByInstitutionAndYear(institutionId, currentYear);
            for (ProgramMember pm : members) {
                if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                    // Skip students who were just un-graduated — they are already in the right place
                    if (ungraduatedUserIds.contains(pm.getUser().getId())) {
                        LOG.info("Undo: skipping demotion for un-graduated student '{}' (already at correct year {})",
                            pm.getUser().getUsername(), currentYear);
                        continue;
                    }

                    // Try to find the source program they came from
                    Program sourceProgram = findTargetProgram(pm.getProgram(), currentYear, previousYear, allPrograms);

                    if (sourceProgram != null && sourceProgram.getId() != pm.getProgram().getId()) {
                        moveStudentToProgram(pm, sourceProgram, previousYear);
                        LOG.info("Undo: moved student '{}' back from '{}' ({}) to '{}' ({})",
                            pm.getUser().getUsername(), pm.getProgram().getName(), currentYear,
                            sourceProgram.getName(), previousYear);
                    } else {
                        pm.setYearLevel(previousYear);
                        memberDao.update(pm);
                        LOG.info("Undo: demoted student '{}' from {} to {} in program '{}'",
                            pm.getUser().getUsername(), currentYear, previousYear, pm.getProgram().getName());
                    }

                    // Update subject assignments back
                    Program programForSubjects = sourceProgram != null ? sourceProgram : pm.getProgram();
                    updateSubjectAssignmentsForPromotion(
                        pm.getUser().getUsername(),
                        pm.getProgram(), programForSubjects,
                        currentYear, previousYear
                    );

                    demoted++;
                }
            }
        }

        LOG.info("Undo promotion complete for institution {}: {} demoted, {} un-graduated",
            institutionId, demoted, ungraduated);

        return new PromotionResult(demoted, ungraduated, 0, 0, instLevel);
    }

    /**
     * Check if the student's current year is effectively a final year for their specific program.
     * This handles cases like yrkesfag VG2 where the program has no VG3 subjects —
     * meaning VG2 is the last year (student graduates with kompetansebevis).
     *
     * For studieforberedende programs that HAVE VG3 subjects, VG2 → VG3 promotion proceeds normally.
     */
    private boolean isDynamicFinalYear(Program program, String fromYear, String toYear, String instLevel) {
        // This check ONLY applies to VGS — yrkesfag programs where VG2 is the final year
        // For all other institution levels, students always follow standard progression
        if (!"VGS".equals(instLevel)) return false;

        if (program == null) return false;

        // Load the program with subjects to check year levels
        Program loaded = programDao.findWithSubjects(program.getId());
        if (loaded == null) return false;

        // 1) If programType is explicitly set, use it directly
        String pType = loaded.getProgramType();
        if (pType != null && !pType.isBlank()) {
            // YRKESFAG programs end at VG2 (no VG3)
            if ("YRKESFAG".equalsIgnoreCase(pType)) {
                return "VG2".equals(fromYear) && "VG3".equals(toYear);
            }
            // STUDIEFORBEREDENDE always goes VG1 → VG2 → VG3
            return false;
        }

        // 2) Fallback: detect from subjects (backward compatibility for programs without programType)
        if (loaded.getSubjects() == null || loaded.getSubjects().isEmpty()) return false;

        boolean hasTargetYearSubjects = loaded.getSubjects().stream()
            .anyMatch(s -> toYear.equals(s.getYearLevel()));

        // If no subjects at the "next" year → this is effectively the final year for this program
        return !hasTargetYearSubjects;
    }

    /**
     * Find the target program for promotion based on program naming convention.
     * E.g. "8A" with fromYear "8" → toYear "9" → target "9A"
     * E.g. "VG1-Studiespesialisering" with fromYear "VG1" → toYear "VG2" → target "VG2-Studiespesialisering"
     */
    private Program findTargetProgram(Program sourceProgram, String fromYear, String toYear, List<Program> allPrograms) {
        String sourceName = sourceProgram.getName();

        // Try to extract class suffix by removing the year prefix
        String suffix = null;
        if (sourceName.startsWith(fromYear)) {
            suffix = sourceName.substring(fromYear.length());
        }

        if (suffix == null) {
            // Program name doesn't start with year level — stay in same program
            return null;
        }

        // Look for target program: toYear + suffix (e.g. "9" + "A" = "9A")
        String targetName = toYear + suffix;
        for (Program p : allPrograms) {
            if (p.getName().equalsIgnoreCase(targetName)) {
                return p;
            }
        }

        LOG.warn("No target program found for '{}' → '{}' (looked for '{}')", sourceName, toYear, targetName);
        return null;
    }

    /**
     * Move a student from their current program to a new program.
     * Deletes the old ProgramMember and creates a new one.
     */
    private void moveStudentToProgram(ProgramMember pm, Program targetProgram, String newYearLevel) {
        // Remove from old program
        memberDao.deleteMembership(pm.getProgram().getId(), pm.getUser().getId());

        // Check if already in target program
        ProgramMember existing = memberDao.findByProgramAndUser(targetProgram.getId(), pm.getUser().getId());
        if (existing != null) {
            existing.setYearLevel(newYearLevel);
            existing.setGraduated(false);
            memberDao.update(existing);
        } else {
            ProgramMember newMember = new ProgramMember(targetProgram, pm.getUser(), pm.getRole(), newYearLevel);
            memberDao.save(newMember);
        }
    }

    /**
     * Transfer a student from one program to another (class transfer, e.g. 8A → 8B).
     * Keeps the same year level but moves all subject assignments.
     */
    public void transferStudent(int userId, int fromProgramId, int toProgramId, int institutionId) {
        ProgramMember pm = memberDao.findByProgramAndUser(fromProgramId, userId);
        if (pm == null) {
            throw new ValidationException("Eleven ble ikke funnet i kildeprogrammet");
        }
        if (!"STUDENT".equalsIgnoreCase(pm.getRole())) {
            throw new ValidationException("Bare elever kan overføres");
        }

        Program sourceProgram = programDao.findWithSubjects(fromProgramId);
        Program targetProgram = programDao.findWithSubjects(toProgramId);
        if (targetProgram == null) {
            throw new ValidationException("Målprogrammet ble ikke funnet");
        }

        String yearLevel = pm.getYearLevel();
        String username = pm.getUser().getUsername();
        int instId = sourceProgram != null ? sourceProgram.getInstitution().getId() : institutionId;

        // Remove subject assignments from old program
        if (sourceProgram != null) {
            for (Subject subject : sourceProgram.getSubjects()) {
                if (yearLevel == null || yearLevel.equals(subject.getYearLevel())) {
                    assignmentDao.removeAssignmentsForUserAndSubject(username, subject.getCode(), instId);
                }
            }
        }

        // Move the membership
        moveStudentToProgram(pm, targetProgram, yearLevel);

        // Add subject assignments for new program
        // Include subjects matching the student's year level + subjects with no year level (always assigned)
        for (Subject subject : targetProgram.getSubjects()) {
            if (yearLevel == null || yearLevel.equals(subject.getYearLevel())
                    || subject.getYearLevel() == null || subject.getYearLevel().isBlank()) {
                assignmentDao.assignStudentToSubject(username, subject.getCode(), instId);
            }
        }

        LOG.info("Student '{}' transferred from '{}' to '{}' (year {})",
            username, sourceProgram != null ? sourceProgram.getName() : fromProgramId,
            targetProgram.getName(), yearLevel);
    }

    /**
     * When a student is promoted, update their subject assignments.
     * Removes subjects from old program/year, adds subjects from new program/year.
     */
    private void updateSubjectAssignmentsForPromotion(String username, Program sourceProgram, Program targetProgram,
                                                       String fromYear, String toYear) {
        Program source = programDao.findWithSubjects(sourceProgram.getId());
        Program target = programDao.findWithSubjects(targetProgram.getId());
        if (source == null) return;

        int instId = source.getInstitution().getId();

        // Remove subject assignments for subjects matching the OLD year level from source program
        for (Subject subject : source.getSubjects()) {
            if (fromYear.equals(subject.getYearLevel())) {
                assignmentDao.removeAssignmentsForUserAndSubject(username, subject.getCode(), instId);
                LOG.debug("Removed subject assignment '{}' (year {}) for student '{}'",
                    subject.getCode(), fromYear, username);
            }
        }

        // Add subject assignments for subjects matching the NEW year level from target program
        // Also add subjects with NULL yearLevel (these are year-independent and should always be assigned)
        if (target != null) {
            for (Subject subject : target.getSubjects()) {
                if (toYear.equals(subject.getYearLevel())
                        || subject.getYearLevel() == null
                        || subject.getYearLevel().isBlank()) {
                    assignmentDao.assignStudentToSubject(username, subject.getCode(), instId);
                    LOG.debug("Added subject assignment '{}' (year {}) for student '{}'",
                        subject.getCode(), subject.getYearLevel() != null ? subject.getYearLevel() : "all", username);
                }
            }
        }
    }

    /**
     * When a student graduates, remove ALL their subject assignments for this program.
     * This ensures they disappear from the subject member lists (but grades are preserved).
     */
    private void removeSubjectAssignmentsForGraduate(ProgramMember pm) {
        Program program = programDao.findWithSubjects(pm.getProgram().getId());
        if (program == null) return;

        String username = pm.getUser().getUsername();
        int instId = program.getInstitution().getId();

        for (Subject subject : program.getSubjects()) {
            assignmentDao.removeAssignmentsForUserAndSubject(username, subject.getCode(), instId);
        }
        LOG.info("Removed all subject assignments for graduated student '{}' from program '{}'",
            username, program.getName());
    }

    /**
     * Check if a student has passed ALL subjects in their program.
     * A passing grade is >= 2 (numeric) or A-E (letter). F or 1 = failing.
     *
     * Rules:
     * - UNGDOMSSKOLE: always diploma (handled separately, not called for this level)
     * - VGS: no vitnemål until all passed
     * - FAGSKOLE/UNIVERSITET: no grad until all passed
     */
    private boolean checkAllSubjectsPassed(ProgramMember pm) {
        Program program = programDao.findWithSubjects(pm.getProgram().getId());
        if (program == null || program.getSubjects().isEmpty()) return true;

        int instId = program.getInstitution().getId();
        String username = pm.getUser().getUsername();
        List<Grade> grades = gradeDao.findByStudentUsername(username, instId);

        // Build a set of subject codes where the student has a passing grade
        Set<String> passedSubjects = new HashSet<>();
        // Track which subjects have retake (privatist) grades — these bypass attendance checks
        Set<String> retakeSubjects = new HashSet<>();
        for (Grade g : grades) {
            // IV = Ikke Vurdert — never counts as passed
            if ("IV".equalsIgnoreCase(g.getValue())) continue;

            Double val = gradeDao.parseGradeToDouble(g.getValue());
            if (val != null && val >= 2.0) {
                passedSubjects.add(g.getSubject());
                if (g.isRetake()) {
                    retakeSubjects.add(g.getSubject());
                }
            }
        }

        // Check attendance limits — if exceeded, subject counts as "Ikke vurdert" (not passed)
        // BUT: retake (privatist) grades bypass this check entirely
        if (program.isAttendanceRequired() && program.getMinAttendancePct() != null) {
            int maxAbsencePct = 100 - program.getMinAttendancePct();
            List<Attendance> attendanceRecords = attendanceDao.findByStudentUsername(username, instId);
            // Group attendance by subject
            Map<String, List<Attendance>> bySubject = new HashMap<>();
            for (Attendance a : attendanceRecords) {
                bySubject.computeIfAbsent(a.getSubjectCode(), k -> new ArrayList<>()).add(a);
            }
            for (Map.Entry<String, List<Attendance>> entry : bySubject.entrySet()) {
                // Skip subjects where the student retook via privatist — absence is irrelevant
                if (retakeSubjects.contains(entry.getKey())) continue;

                List<Attendance> recs = entry.getValue();
                if (recs.isEmpty()) continue;
                long unexcused = recs.stream()
                    .filter(a -> UNEXCUSED_STATUSES.contains(a.getStatus()) && !a.isExcused())
                    .count();
                double pct = (unexcused * 100.0) / recs.size();
                if (pct > maxAbsencePct) {
                    // This subject is "Ikke vurdert" due to excess absence
                    passedSubjects.remove(entry.getKey());
                    LOG.info("Student '{}' exceeded absence limit in '{}' ({}% > {}%) — Ikke vurdert",
                        username, entry.getKey(), String.format("%.1f", pct), maxAbsencePct);
                }
            }
        }

        // Check that every subject in the program has been passed
        for (Subject subject : program.getSubjects()) {
            if (!passedSubjects.contains(subject.getCode())) {
                LOG.debug("Student '{}' has NOT passed subject '{}' — no diploma",
                    username, subject.getCode());
                return false;
            }
        }

        return true;
    }

    // ── Result DTOs ──

    public record PromotionPreview(List<PromotionAction> promotions, List<PromotionAction> graduations,
                                     String level, List<String> missingClasses) {}

    public record PromotionAction(int userId, String username, String fullName,
                                   int programId, String programName,
                                   String fromYear, String toYear, boolean graduating,
                                   int targetProgramId, String targetProgramName) {}

    public record PromotionResult(int promoted, int graduated,
                                   int graduatedWithDiploma, int graduatedWithoutDiploma,
                                   String level) {}

    public record TransferRequest(int userId, int fromProgramId, int toProgramId) {}
}
