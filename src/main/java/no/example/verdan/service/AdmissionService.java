package no.example.verdan.service;

import no.example.verdan.dao.AdmissionDao;
import no.example.verdan.dao.GradeDao;
import no.example.verdan.dao.InstitutionDao;
import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.SubjectAssignmentDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.dto.AdmissionDto;
import no.example.verdan.model.*;
import no.example.verdan.security.InputValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for the admission system: managing periods, requirements,
 * applications, and running the admission algorithm.
 */
public class AdmissionService {

    private static final Logger LOG = LoggerFactory.getLogger(AdmissionService.class);
    private final AdmissionDao admissionDao;
    private final ProgramDao programDao;
    private final GradeDao gradeDao;
    private final InstitutionDao institutionDao;
    private final UserDao userDao;
    private final ProgramMemberDao memberDao;
    private final SubjectAssignmentDao assignmentDao;

    /** Maps toLevel → first year level for that institution type. */
    private static final Map<String, String> FIRST_YEAR = Map.of(
        "UNGDOMSSKOLE", "8",
        "VGS", "VG1",
        "FAGSKOLE", "1",
        "UNIVERSITET", "BACHELOR_1"
    );

    public AdmissionService() {
        this(new AdmissionDao(), new ProgramDao(), new GradeDao(), new InstitutionDao(),
             new UserDao(), new ProgramMemberDao(), new SubjectAssignmentDao());
    }

    public AdmissionService(AdmissionDao admissionDao, ProgramDao programDao,
                            GradeDao gradeDao, InstitutionDao institutionDao, UserDao userDao,
                            ProgramMemberDao memberDao, SubjectAssignmentDao assignmentDao) {
        this.admissionDao = admissionDao;
        this.programDao = programDao;
        this.gradeDao = gradeDao;
        this.institutionDao = institutionDao;
        this.userDao = userDao;
        this.memberDao = memberDao;
        this.assignmentDao = assignmentDao;
    }

    // ========================================================================
    //  Portal (cross-institution)
    // ========================================================================

    /** Get published programs across all institutions for the portal, optionally filtered by fromLevel. */
    public List<AdmissionDto.PortalListing> getPortalListings(String fromLevel) {
        List<AdmissionRequirement> reqs = admissionDao.findAllOpenRequirements();
        return reqs.stream()
            .filter(r -> {
                if (fromLevel == null || fromLevel.isBlank()) return true;
                return fromLevel.equalsIgnoreCase(r.getPeriod().getFromLevel());
            })
            .map(r -> {
                AdmissionPeriod p = r.getPeriod();
                int appCount = (int) admissionDao.findApplicationsByPeriod(p.getId()).stream()
                    .filter(a -> a.getProgram().getId() == r.getProgram().getId())
                    .count();
                return new AdmissionDto.PortalListing(
                    p.getId(), p.getName(), p.getFromLevel(), p.getToLevel(),
                    p.getStartDate().toString(), p.getEndDate().toString(), p.getMaxChoices(),
                    p.getInstitution().getId(), p.getInstitution().getName(),
                    p.getInstitution().getOwnership() != null ? p.getInstitution().getOwnership() : "PUBLIC",
                    r.getProgram().getId(), r.getProgram().getName(),
                    r.getMinGpa(), r.getMaxStudents(), r.getProgram().getPrerequisites(), appCount
                );
            }).toList();
    }

    // ========================================================================
    //  Admission Periods (admin)
    // ========================================================================

    public List<AdmissionDto.PeriodResponse> getPeriodsForAdmin(int institutionId) {
        return admissionDao.findByInstitution(institutionId).stream()
            .map(this::toPeriodResponse)
            .toList();
    }

    public AdmissionDto.PeriodResponse createPeriod(AdmissionDto.PeriodRequest req, int institutionId) {
        if (req.name() == null || req.name().isBlank())
            throw new ValidationException("Period name is required");
        if (req.fromLevel() == null || req.toLevel() == null)
            throw new ValidationException("fromLevel and toLevel are required");

        // Validate admission path: fagskole does not give studiekompetanse → can't lead to university
        if ("FAGSKOLE".equalsIgnoreCase(req.fromLevel()) && "UNIVERSITET".equalsIgnoreCase(req.toLevel())) {
            throw new ValidationException("Fagskole gir ikke studiekompetanse. Kun VGS med vitnemål kvalifiserer til universitetsopptak.");
        }

        Institution inst = institutionDao.find(institutionId);
        if (inst == null) throw new NotFoundException("Institution not found");

        AdmissionPeriod period = new AdmissionPeriod();
        period.setInstitution(inst);
        period.setName(InputValidator.sanitize(req.name()));
        period.setFromLevel(req.fromLevel().toUpperCase());
        period.setToLevel(req.toLevel().toUpperCase());
        period.setStartDate(LocalDate.parse(req.startDate()));
        period.setEndDate(LocalDate.parse(req.endDate()));
        period.setMaxChoices(req.maxChoices() > 0 ? req.maxChoices() : 5);
        period.setStatus("OPEN");

        admissionDao.save(period);
        LOG.info("Admission period created: '{}' ({}→{}) at institution {}",
            period.getName(), period.getFromLevel(), period.getToLevel(), inst.getName());
        return toPeriodResponse(period);
    }

    public AdmissionDto.PeriodResponse updatePeriod(int id, AdmissionDto.PeriodRequest req, int institutionId) {
        AdmissionPeriod period = admissionDao.find(id);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        if (req.name() != null) period.setName(InputValidator.sanitize(req.name()));
        if (req.startDate() != null) period.setStartDate(LocalDate.parse(req.startDate()));
        if (req.endDate() != null) period.setEndDate(LocalDate.parse(req.endDate()));
        if (req.maxChoices() > 0) period.setMaxChoices(req.maxChoices());

        admissionDao.update(period);
        return toPeriodResponse(period);
    }

    public void closePeriod(int id, int institutionId) {
        AdmissionPeriod period = admissionDao.find(id);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");
        period.setStatus("CLOSED");
        admissionDao.update(period);
    }

    public void reopenPeriod(int id, int institutionId, String newEndDate) {
        AdmissionPeriod period = admissionDao.find(id);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");
        if (newEndDate != null && !newEndDate.isBlank()) {
            LocalDate parsed = LocalDate.parse(newEndDate);
            if (parsed.isBefore(LocalDate.now())) {
                throw new ValidationException("Ny frist må være i fremtiden");
            }
            period.setEndDate(parsed);
        }
        period.setStatus("OPEN");
        admissionDao.update(period);
        LOG.info("Period '{}' reopened with end date {}", period.getName(), period.getEndDate());
    }

    public void deletePeriod(int id, int institutionId) {
        AdmissionPeriod period = admissionDao.find(id);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        // Delete all applications and requirements first
        List<Application> apps = admissionDao.findApplicationsByPeriod(id);
        if (!apps.isEmpty()) admissionDao.updateApplications(apps); // ensure they are detached
        admissionDao.deleteApplicationsForPeriod(id);
        admissionDao.deleteRequirementsForPeriod(id);
        admissionDao.delete(period);
        LOG.info("Period '{}' deleted (ID: {})", period.getName(), id);
    }

    /**
     * Bulk-publish programs to the portal: creates AdmissionRequirements for each
     * program using the program's pre-configured minGpa and maxStudents.
     */
    public int bulkPublishPrograms(int periodId, List<Integer> programIds, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        int count = 0;
        for (int progId : programIds) {
            Program program = programDao.findWithSubjects(progId);
            if (program == null) continue;

            // Skip if already published
            if (admissionDao.findRequirement(periodId, progId) != null) continue;

            AdmissionRequirement req = new AdmissionRequirement();
            req.setPeriod(period);
            req.setProgram(program);
            req.setMinGpa(program.getMinGpa());
            req.setMaxStudents(program.getMaxStudents());
            admissionDao.saveRequirement(req);
            count++;
        }
        LOG.info("Bulk-published {} programs for period '{}'", count, period.getName());
        return count;
    }

    // ========================================================================
    //  Requirements (admin)
    // ========================================================================

    public List<AdmissionDto.RequirementResponse> getRequirements(int periodId, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        List<AdmissionRequirement> reqs = admissionDao.findRequirements(periodId);
        List<Application> allApps = admissionDao.findApplicationsByPeriod(periodId);

        return reqs.stream().map(r -> {
            long count = allApps.stream()
                .filter(a -> a.getProgram().getId() == r.getProgram().getId())
                .count();
            return new AdmissionDto.RequirementResponse(
                r.getId(), r.getProgram().getId(), r.getProgram().getName(),
                r.getMinGpa(), r.getMaxStudents(), (int) count
            );
        }).toList();
    }

    public AdmissionDto.RequirementResponse setRequirement(int periodId,
            AdmissionDto.RequirementRequest req, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        Program program = programDao.findWithSubjects(req.programId());
        if (program == null) throw new NotFoundException("Program not found");

        AdmissionRequirement existing = admissionDao.findRequirement(periodId, req.programId());
        if (existing == null) {
            existing = new AdmissionRequirement();
            existing.setPeriod(period);
            existing.setProgram(program);
        }
        existing.setMinGpa(req.minGpa());
        existing.setMaxStudents(req.maxStudents());
        admissionDao.saveRequirement(existing);

        return new AdmissionDto.RequirementResponse(
            existing.getId(), program.getId(), program.getName(),
            existing.getMinGpa(), existing.getMaxStudents(), 0
        );
    }

    // ========================================================================
    //  Applications (student)
    // ========================================================================

    /** Get open admission periods available for a student based on their institution level. */
    public List<AdmissionDto.PeriodResponse> getAvailablePeriods(int studentId) {
        User student = userDao.find(studentId);
        if (student == null || student.getInstitution() == null) return List.of();

        Institution inst = student.getInstitution();
        String level = inst.getLevel();
        if (level == null) return List.of();

        return admissionDao.findOpenByFromLevel(level).stream()
            .map(this::toPeriodResponse)
            .toList();
    }

    /** Submit applications (up to maxChoices programs, ranked by priority). */
    public List<AdmissionDto.ApplicationResponse> submitApplications(int studentId,
            AdmissionDto.ApplicationSubmit submit) {
        User student = userDao.find(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        AdmissionPeriod period = admissionDao.find(submit.periodId());
        if (period == null) throw new NotFoundException("Period not found");
        if (!"OPEN".equals(period.getStatus()))
            throw new ValidationException("This admission period is not open");

        // Check deadline
        if (LocalDate.now().isAfter(period.getEndDate()))
            throw new ValidationException("Søknadsfristen er utløpt");

        if (submit.choices() == null || submit.choices().isEmpty())
            throw new ValidationException("Du må velge minst ett studieprogram");
        if (submit.choices().size() > period.getMaxChoices())
            throw new ValidationException("Maksimalt " + period.getMaxChoices() + " valg er tillatt");

        // Check for existing applications in this period
        List<Application> existing = admissionDao.findStudentApplicationsInPeriod(studentId, submit.periodId());

        // Block only if any app has been processed by the admin algorithm (ACCEPTED/WAITLISTED)
        boolean hasProcessed = existing.stream()
            .anyMatch(a -> ApplicationStatus.ACCEPTED.name().equals(a.getStatus()) || ApplicationStatus.WAITLISTED.name().equals(a.getStatus()));
        if (hasProcessed)
            throw new ConflictException("Søknadene dine er allerede behandlet og kan ikke endres");

        // Delete all old applications (PENDING, WITHDRAWN, REJECTED) — student is resubmitting
        if (!existing.isEmpty()) {
            LOG.info("Student '{}' is resubmitting — deleting {} old applications in period {}",
                student.getUsername(), existing.size(), submit.periodId());
            for (Application old : existing) {
                admissionDao.deleteApplication(old);
            }
        }

        // Calculate GPA from the correct source level (fromLevel determines which grades to use)
        GpaResult gpaResult = calculateGpaFromLevel(student, period.getFromLevel());

        // Check diploma eligibility from the source level
        // VGS/Fagskole/Universitet require a diploma/vitnemål to apply further
        // Ungdomsskole always grants vitnemål, so no check needed
        // Special case: VGS→VGS (påbygg) only requires graduation, not full diploma
        boolean sameLevel = period.getFromLevel().equalsIgnoreCase(period.getToLevel());
        boolean hasDiploma;
        if (sameLevel) {
            // Same level (e.g. VGS→VGS for påbygg): only need to have graduated, no diploma required
            hasDiploma = checkGraduatedFromLevel(student, period.getFromLevel());
        } else {
            hasDiploma = checkDiplomaFromLevel(student, period.getFromLevel());
        }

        // Determine initial status
        ApplicationStatus initialStatus;
        if (!hasDiploma && !"UNGDOMSSKOLE".equalsIgnoreCase(period.getFromLevel())) {
            initialStatus = ApplicationStatus.REJECTED;
            LOG.info("Student '{}' does not have required completion from {} — applications auto-rejected",
                student.getUsername(), period.getFromLevel());
        } else if (!gpaResult.allPassing() && !"UNGDOMSSKOLE".equalsIgnoreCase(period.getFromLevel())) {
            // Failing grades only block admission for VGS→higher. Ungdomsskole students
            // have a legal right to VGS regardless of grades.
            initialStatus = ApplicationStatus.REJECTED;
            LOG.info("Student '{}' has failing grades — applications will be auto-rejected", student.getUsername());
        } else {
            initialStatus = ApplicationStatus.PENDING;
        }

        List<Application> apps = new ArrayList<>();
        for (AdmissionDto.ApplicationChoice choice : submit.choices()) {
            Program program = programDao.findWithSubjects(choice.programId());
            if (program == null) throw new NotFoundException("Studieprogram ikke funnet: " + choice.programId());

            // For university internal admissions, validate degree prerequisites per program
            ApplicationStatus appStatus = initialStatus;
            if ("UNIVERSITET".equalsIgnoreCase(period.getFromLevel())
                    && "UNIVERSITET".equalsIgnoreCase(period.getToLevel())
                    && initialStatus == ApplicationStatus.PENDING) {
                String requiredDegree = getRequiredDegreeForProgram(program);
                if (requiredDegree != null && !hasCompletedDegree(student, requiredDegree)) {
                    appStatus = ApplicationStatus.REJECTED;
                    LOG.info("Student '{}' lacks {} degree — rejected for program '{}'",
                        student.getUsername(), requiredDegree, program.getName());
                }
            }

            Application app = new Application();
            app.setPeriod(period);
            app.setStudent(student);
            app.setProgram(program);
            app.setPriority(choice.priority());
            app.setGpaSnapshot(gpaResult.gpa());
            app.setStatus(appStatus.name());
            app.setSubmittedAt(LocalDateTime.now());
            if (appStatus != ApplicationStatus.PENDING) {
                app.setProcessedAt(LocalDateTime.now()); // Auto-processed
            }

            admissionDao.saveApplication(app);
            apps.add(app);
        }

        LOG.info("Student '{}' submitted {} applications for period '{}' (GPA: {}, fromLevel: {}, allPassing: {})",
            student.getUsername(), apps.size(), period.getName(), gpaResult.gpa(),
            period.getFromLevel(), gpaResult.allPassing());

        return apps.stream().map(this::toApplicationResponse).toList();
    }

    /** Get a student's applications. */
    public List<AdmissionDto.ApplicationResponse> getMyApplications(int studentId) {
        return admissionDao.findApplicationsByStudent(studentId).stream()
            .map(this::toApplicationResponse)
            .toList();
    }

    /** Withdraw an application. */
    public void withdrawApplication(int applicationId, int studentId) {
        List<Application> apps = admissionDao.findApplicationsByStudent(studentId);
        Application app = apps.stream().filter(a -> a.getId() == applicationId).findFirst()
            .orElseThrow(() -> new NotFoundException("Søknad ikke funnet"));

        ApplicationStatus status = ApplicationStatus.fromString(app.getStatus());
        if (status == ApplicationStatus.WITHDRAWN)
            throw new ValidationException("Søknaden er allerede trukket");
        if (status == ApplicationStatus.CONFIRMED)
            throw new ValidationException("Kan ikke trekke en bekreftet søknad");
        if (status == ApplicationStatus.ENROLLED)
            throw new ValidationException("Kan ikke trekke en innmeldt søknad");
        if (status == ApplicationStatus.REJECTED)
            throw new ValidationException("Kan ikke trekke en avslått søknad");

        app.setStatus(ApplicationStatus.WITHDRAWN.name());
        app.setProcessedAt(LocalDateTime.now());
        admissionDao.saveApplication(app);

        LOG.info("Student {} withdrew application {} for program '{}'",
            studentId, applicationId, app.getProgram().getName());
    }

    /**
     * Confirm an accepted application.
     * Sets this application to CONFIRMED and auto-withdraws all other ACCEPTED applications
     * for the same student across all periods. This ensures a student can only be enrolled
     * at one institution.
     *
     * Uses batch transaction (updateApplications) to ensure atomicity — either ALL
     * status changes succeed, or NONE do.
     */
    public void confirmApplication(int applicationId, int studentId) {
        List<Application> allApps = admissionDao.findApplicationsByStudent(studentId);
        Application target = allApps.stream().filter(a -> a.getId() == applicationId).findFirst()
            .orElseThrow(() -> new NotFoundException("Søknad ikke funnet"));

        if (!ApplicationStatus.ACCEPTED.name().equals(target.getStatus()))
            throw new ValidationException("Kan bare bekrefte aksepterte søknader");

        // Confirm the chosen application
        target.setStatus(ApplicationStatus.CONFIRMED.name());
        target.setProcessedAt(LocalDateTime.now());

        // Auto-withdraw all other ACCEPTED applications for this student
        List<Application> changed = new ArrayList<>();
        changed.add(target);
        int withdrawn = 0;
        for (Application other : allApps) {
            if (other.getId() != applicationId && ApplicationStatus.ACCEPTED.name().equals(other.getStatus())) {
                other.setStatus(ApplicationStatus.WITHDRAWN.name());
                other.setProcessedAt(LocalDateTime.now());
                changed.add(other);
                withdrawn++;
            }
        }

        // Save all changes in a single atomic transaction
        admissionDao.updateApplications(changed);

        LOG.info("Student {} confirmed application {} for program '{}'. {} other offers auto-withdrawn.",
            studentId, applicationId, target.getProgram().getName(), withdrawn);
    }

    // ========================================================================
    //  Processing (admin) — THE ALGORITHM
    // ========================================================================

    /** Get admin overview of all applications for a period. */
    public List<AdmissionDto.ProgramApplicantSummary> getAdminOverview(int periodId, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        List<AdmissionRequirement> reqs = admissionDao.findRequirements(periodId);
        List<Application> allApps = admissionDao.findApplicationsByPeriod(periodId);

        Map<Integer, List<Application>> byProgram = allApps.stream()
            .collect(Collectors.groupingBy(a -> a.getProgram().getId()));

        return reqs.stream().map(req -> {
            List<Application> progApps = byProgram.getOrDefault(req.getProgram().getId(), List.of());
            List<AdmissionDto.ApplicantDetail> details = progApps.stream()
                .map(a -> new AdmissionDto.ApplicantDetail(
                    a.getId(), a.getStudent().getId(), a.getStudent().getUsername(),
                    a.getStudent().getFirstName() + " " + a.getStudent().getLastName(),
                    a.getPriority(), a.getGpaSnapshot(), a.getStatus()
                ))
                .toList();
            return new AdmissionDto.ProgramApplicantSummary(
                req.getProgram().getId(), req.getProgram().getName(),
                req.getMinGpa(), req.getMaxStudents(),
                progApps.size(), details
            );
        }).toList();
    }

    /**
     * Run the admission algorithm for a period.
     *
     * Algorithm:
     * 1. For each priority level (1 through maxChoices):
     *    a. Get all PENDING applications with this priority, sorted by GPA DESC
     *    b. For each application:
     *       - Skip if student already accepted at a higher-priority choice
     *       - Check minimum GPA requirement → REJECTED if below
     *       - Check capacity → ACCEPTED if spots available, WAITLISTED if full
     * 2. Mark period as PROCESSED.
     */
    public AdmissionDto.ProcessingResult processAdmissions(int periodId, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId)
            throw new NotFoundException("Period not found");

        List<Application> allApps = admissionDao.findApplicationsByPeriod(periodId);
        List<AdmissionRequirement> reqs = admissionDao.findRequirements(periodId);

        // Build requirement lookup
        Map<Integer, AdmissionRequirement> reqMap = reqs.stream()
            .collect(Collectors.toMap(r -> r.getProgram().getId(), r -> r));

        // Track accepted counts per program
        Map<Integer, Integer> acceptedCounts = new HashMap<>();
        // Track which students have been accepted
        Set<Integer> acceptedStudents = new HashSet<>();
        // Track students who were rejected due to GPA (cannot try lower choices)
        Set<Integer> rejectedStudents = new HashSet<>();

        int accepted = 0, waitlisted = 0, rejected = 0;

        // Process by priority (1 = first choice first)
        int maxPriority = allApps.stream().mapToInt(Application::getPriority).max().orElse(5);

        for (int prio = 1; prio <= maxPriority; prio++) {
            final int currentPrio = prio;

            // Get all apps at this priority, sort by GPA descending (highest first)
            List<Application> prioApps = allApps.stream()
                .filter(a -> a.getPriority() == currentPrio && ApplicationStatus.PENDING.name().equals(a.getStatus()))
                .sorted(Comparator.comparingDouble((Application a) ->
                    a.getGpaSnapshot() != null ? a.getGpaSnapshot() : 0.0).reversed())
                .toList();

            for (Application app : prioApps) {
                int studentId = app.getStudent().getId();
                int programId = app.getProgram().getId();

                // Skip if already accepted at a higher-priority choice
                if (acceptedStudents.contains(studentId)) {
                    app.setStatus(ApplicationStatus.WITHDRAWN.name());
                    app.setProcessedAt(LocalDateTime.now());
                    continue;
                }

                AdmissionRequirement req = reqMap.get(programId);
                if (req == null) {
                    // No requirements set for this program → reject this application
                    app.setStatus(ApplicationStatus.REJECTED.name());
                    app.setProcessedAt(LocalDateTime.now());
                    rejected++;
                    continue;
                }

                double gpa = app.getGpaSnapshot() != null ? app.getGpaSnapshot() : 0.0;

                // Check minimum GPA — if below, reject and don't try lower choices
                if (req.getMinGpa() != null && gpa < req.getMinGpa()) {
                    app.setStatus(ApplicationStatus.REJECTED.name());
                    app.setProcessedAt(LocalDateTime.now());
                    rejected++;
                    continue;
                }

                // Check capacity — if full, DON'T set status yet, leave as PENDING
                // so the student can be tried at the next priority choice
                int currentCount = acceptedCounts.getOrDefault(programId, 0);
                if (req.getMaxStudents() != null && currentCount >= req.getMaxStudents()) {
                    // Leave as PENDING — will be tried at next priority level
                    // Mark this specific app as capacity-blocked (but not the student)
                    app.setStatus(ApplicationStatus.WAITLISTED.name());
                    app.setProcessedAt(LocalDateTime.now());
                    // Don't add to acceptedStudents — they should try next choice
                    continue;
                }

                // Accept! Student gets this spot
                app.setStatus(ApplicationStatus.ACCEPTED.name());
                app.setProcessedAt(LocalDateTime.now());
                acceptedCounts.merge(programId, 1, Integer::sum);
                acceptedStudents.add(studentId);
                accepted++;

                // Withdraw all other pending applications from this student
                for (Application other : allApps) {
                    if (other.getStudent().getId() == studentId && other != app && ApplicationStatus.PENDING.name().equals(other.getStatus())) {
                        other.setStatus(ApplicationStatus.WITHDRAWN.name());
                        other.setProcessedAt(LocalDateTime.now());
                    }
                }
            }
        }

        // Count final waitlisted (students who were capacity-blocked on all their choices)
        for (Application app : allApps) {
            if (ApplicationStatus.WAITLISTED.name().equals(app.getStatus())) {
                // Check if this student was accepted elsewhere
                if (acceptedStudents.contains(app.getStudent().getId())) {
                    app.setStatus(ApplicationStatus.WITHDRAWN.name());
                    app.setProcessedAt(LocalDateTime.now());
                } else {
                    waitlisted++;
                }
            }
        }

        // Save all updated applications
        admissionDao.updateApplications(allApps);

        // Mark period as processed
        period.setStatus("PROCESSED");
        admissionDao.update(period);

        LOG.info("Admission processing complete for period '{}': {} accepted, {} waitlisted, {} rejected",
            period.getName(), accepted, waitlisted, rejected);

        return new AdmissionDto.ProcessingResult(accepted, waitlisted, rejected, allApps.size());
    }
    // ========================================================================
    //  Enroll accepted students into programs (admin action)
    // ========================================================================

    /**
     * Enroll all CONFIRMED students from a processed admission period into their programs.
     * Each confirmed student is:
     * 1. Transferred to the target institution (becomes a user of the new school)
     * 2. Added as a ProgramMember at the first year level
     * 3. Auto-enrolled in subjects matching that year level
     * 4. Application status updated to ENROLLED
     */
    public AdmissionDto.EnrollmentResult enrollAcceptedStudents(int periodId, int institutionId) {
        AdmissionPeriod period = admissionDao.find(periodId);
        if (period == null || period.getInstitution().getId() != institutionId) {
            LOG.error("enrollAccepted FAILED: periodId={}, callerInstId={}, period={}, periodInstId={}",
                periodId, institutionId, period != null ? "found" : "NULL",
                period != null && period.getInstitution() != null ? period.getInstitution().getId() : "null");
            throw new NotFoundException("Period not found");
        }

        List<Application> allApps = admissionDao.findApplicationsByPeriod(periodId);
        List<Application> confirmedApps = allApps.stream()
            .filter(a -> ApplicationStatus.CONFIRMED.name().equals(a.getStatus()))
            .toList();

        if (confirmedApps.isEmpty()) {
            return new AdmissionDto.EnrollmentResult(0, 0, 0);
        }

        // Determine default first year level based on target institution level
        String defaultFirstYear = FIRST_YEAR.getOrDefault(period.getToLevel(), "1");

        int enrolled = 0;
        int skipped = 0;

        for (Application app : confirmedApps) {
            User student = app.getStudent();
            Program program = programDao.findWithSubjects(app.getProgram().getId());
            if (program == null) {
                skipped++;
                continue;
            }

            // Check if already a member of this program
            ProgramMember existing = memberDao.findByProgramAndUser(program.getId(), student.getId());
            if (existing != null) {
                skipped++;
                continue;
            }

            List<ProgramMember> currentMemberships = memberDao.findByUser(student.getId());

            // Determine if this is a same-level transfer (e.g. VGS→VGS) or cross-level (ungdomsskole→VGS)
            boolean isSameLevelTransfer = false;
            if (student.getInstitution() != null && program.getInstitution() != null) {
                String studentInstLevel = student.getInstitution().getLevel();
                String targetInstLevel = program.getInstitution().getLevel();
                isSameLevelTransfer = studentInstLevel != null && studentInstLevel.equals(targetInstLevel)
                    && student.getInstitution().getId() != program.getInstitution().getId();
            }

            // For cross-level transfers (e.g. ungdomsskole→VGS), require graduation
            // For same-level transfers (e.g. VGS→VGS school change), allow without graduation
            if (!isSameLevelTransfer) {
                boolean isGraduated = currentMemberships.isEmpty();
                if (!currentMemberships.isEmpty()) {
                    isGraduated = currentMemberships.stream()
                        .filter(pm -> pm.getProgram().getInstitution() != null &&
                                student.getInstitution() != null &&
                                pm.getProgram().getInstitution().getId() == student.getInstitution().getId())
                        .anyMatch(ProgramMember::isGraduated);
                }
                if (!isGraduated) {
                    LOG.warn("Student '{}' is not graduated from current institution — cannot enroll in '{}'",
                        student.getUsername(), program.getName());
                    skipped++;
                    continue;
                }
            }

            // --- Transfer student to the target institution ---
            Institution targetInst = program.getInstitution();
            if (targetInst != null && (student.getInstitution() == null ||
                    student.getInstitution().getId() != targetInst.getId())) {

                // Store reference to previous institution for history access
                if (student.getInstitution() != null) {
                    student.setTransferredFromInstitutionId(student.getInstitution().getId());

                    // Remove from old program memberships (keep graduated ones for records)
                    int oldInstId = student.getInstitution().getId();
                    for (ProgramMember pm : currentMemberships) {
                        if (pm.getProgram().getInstitution() != null &&
                                pm.getProgram().getInstitution().getId() == oldInstId &&
                                !pm.isGraduated()) {
                            memberDao.deleteMembership(pm.getProgram().getId(), student.getId());
                        }
                    }

                    // Remove old subject assignments at previous institution
                    assignmentDao.removeAllForUser(student.getUsername(), oldInstId);

                    LOG.info("Cleaned up memberships and assignments for '{}' at old institution '{}'",
                        student.getUsername(), student.getInstitution().getName());
                }

                student.setInstitution(targetInst);
                userDao.update(student);
                LOG.info("Transferred student '{}' to institution '{}' (ID={})",
                    student.getUsername(), targetInst.getName(), targetInst.getId());
            }

            // For same-level transfers (e.g. VGS→VGS påbygg), place at highest year (VG3)
            // For cross-level transfers (e.g. ungdomsskole→VGS), place at first year (VG1)
            String enrollYear;
            if (isSameLevelTransfer) {
                enrollYear = detectLastYear(program, defaultFirstYear);
            } else {
                enrollYear = detectFirstYear(program, defaultFirstYear);
            }

            // Create ProgramMember at the determined year level
            ProgramMember member = new ProgramMember(program, student, "STUDENT", enrollYear);
            memberDao.save(member);

            // Auto-enroll in subjects matching the enrollment year level only
            int instId = program.getInstitution().getId();
            for (Subject subject : program.getSubjects()) {
                if (enrollYear.equals(subject.getYearLevel())) {
                    assignmentDao.assignStudentToSubject(student.getUsername(), subject.getCode(), instId);
                }
            }

            // Update application status to ENROLLED
            app.setStatus(ApplicationStatus.ENROLLED.name());
            admissionDao.saveApplication(app);

            enrolled++;
            LOG.info("Enrolled accepted student '{}' into program '{}' at institution '{}' year '{}'",
                student.getUsername(), program.getName(), targetInst != null ? targetInst.getName() : "?", enrollYear);
        }

        LOG.info("Enrollment complete for period '{}': {} enrolled, {} skipped",
            period.getName(), enrolled, skipped);

        return new AdmissionDto.EnrollmentResult(enrolled, skipped, confirmedApps.size());
    }



    /**
     * Determines which institution level's grades to use based on what is being applied to (fromLevel).
     *
     * Mapping:
     * - UNGDOMSSKOLE → uses UNGDOMSSKOLE grades (applying to VGS)
     * - VGS → uses VGS grades (applying to Fagskole/Universitet)
     * - BACHELOR → uses UNIVERSITET grades (applying to Master)
     */
    private String resolveSourceLevel(String fromLevel) {
        if (fromLevel == null) return null;
        return switch (fromLevel.toUpperCase()) {
            case "UNGDOMSSKOLE" -> "UNGDOMSSKOLE";
            case "VGS" -> "VGS";
            case "FAGSKOLE" -> "FAGSKOLE";
            case "UNIVERSITET" -> "UNIVERSITET";
            default -> fromLevel;
        };
    }

    /**
     * Calculate GPA from the student's grades at the source level.
     * Also returns whether all grades are passing (≥ 2 / ≥ E).
     *
     * For UNGDOMSSKOLE: subjects follow a pattern like NOK1000, NOK2000, NOK3000
     * (same subject across grades 8-10). Only the highest-numbered code per
     * subject group counts — matching Norway's rule that only the final year's
     * grade is used for subjects that span multiple years.
     */
    private GpaResult calculateGpaFromLevel(User student, String fromLevel) {
        String sourceLevel = resolveSourceLevel(fromLevel);
        List<Grade> grades;
        if (sourceLevel != null) {
            grades = gradeDao.findByStudentAndInstitutionLevel(student.getUsername(), sourceLevel);
        } else {
            // Fallback: use current institution
            grades = gradeDao.findByStudentUsername(student.getUsername(), student.getInstitution().getId());
        }

        if (grades.isEmpty()) return new GpaResult(0.0, true);

        // For ungdomsskole: keep only the highest subject code per subject group
        // e.g. NOK1000, NOK2000, NOK3000 → only NOK3000 counts
        List<Grade> effectiveGrades;
        if ("UNGDOMSSKOLE".equalsIgnoreCase(fromLevel)) {
            // Group by subject prefix (first 3 chars), keep only highest code per group
            Map<String, Grade> bestPerGroup = new java.util.LinkedHashMap<>();
            for (Grade g : grades) {
                String code = g.getSubject();
                if (code == null || code.length() < 3) continue;
                String prefix = code.substring(0, 3).toUpperCase();
                Grade existing = bestPerGroup.get(prefix);
                if (existing == null || code.compareToIgnoreCase(existing.getSubject()) > 0) {
                    bestPerGroup.put(prefix, g);
                }
            }
            effectiveGrades = new ArrayList<>(bestPerGroup.values());
            LOG.debug("Ungdomsskole GPA for '{}': {} raw grades → {} effective (highest per subject group)",
                student.getUsername(), grades.size(), effectiveGrades.size());
        } else {
            effectiveGrades = grades;
        }

        double sum = 0;
        int count = 0;
        boolean allPassing = true;
        for (Grade g : effectiveGrades) {
            // IV (Ikke Vurdert) = blocked by absence → not passing, don't include in GPA
            if ("IV".equalsIgnoreCase(g.getValue())) {
                allPassing = false;
                continue;
            }
            Double val = gradeToNumeric(g.getValue());
            if (val != null) {
                sum += val;
                count++;
                if (val < 2.0) {
                    allPassing = false; // Grade below 2 (or F) = not passing
                }
            }
        }
        double gpa = count > 0 ? Math.round((sum / count) * 100.0) / 100.0 : 0.0;
        return new GpaResult(gpa, allPassing);
    }

    /** Simple record for GPA + pass status */
    private record GpaResult(double gpa, boolean allPassing) {}

    private Double gradeToNumeric(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase();
        // Try numeric first
        try {
            double num = Double.parseDouble(v);
            if (num >= 1 && num <= 6) return num;
        } catch (NumberFormatException ignored) {}
        // Letter scale
        return switch (v) {
            case "A" -> 6.0;
            case "B" -> 5.0;
            case "C" -> 4.0;
            case "D" -> 3.0;
            case "E" -> 2.0;
            case "F" -> 1.0;
            default -> null;
        };
    }

    // ========================================================================
    //  Diploma check — does the student have a vitnemål from the source level?
    // ========================================================================

    /**
     * Check if a student has graduated WITH a diploma from a program at the specified level.
     *
     * Rules:
     * - UNGDOMSSKOLE: always returns true (vitnemål granted regardless of grades)
     * - VGS: student must have graduated from VGS with diplomaEligible = true
     * - FAGSKOLE: student must have graduated from Fagskole with diplomaEligible = true
     * - UNIVERSITET: student must have graduated from Universitet with diplomaEligible = true
     *
     * This enforces that e.g. a student who finished VGS without passing all subjects
     * cannot be accepted to Universitet/Høyskole/Fagskole.
     */
    private boolean checkDiplomaFromLevel(User student, String fromLevel) {
        if (fromLevel == null) return true;
        if ("UNGDOMSSKOLE".equalsIgnoreCase(fromLevel)) return true; // Always get vitnemål

        // Map fromLevel to the institution level string used in programs
        String institutionLevel = resolveSourceLevel(fromLevel);
        if (institutionLevel == null) return true;

        // Find all program memberships where the student has graduated
        List<ProgramMember> memberships = memberDao.findByUser(student.getId());
        for (ProgramMember pm : memberships) {
            if (!pm.isGraduated()) continue;
            Program program = pm.getProgram();
            if (program == null || program.getInstitution() == null) continue;

            String progLevel = program.getInstitution().getLevel();
            if (institutionLevel.equalsIgnoreCase(progLevel) && pm.isDiplomaEligible()) {
                LOG.debug("Student '{}' has diploma from {} level — eligible to apply",
                    student.getUsername(), fromLevel);
                return true;
            }
        }

        LOG.info("Student '{}' does NOT have a diploma from {} level — NOT eligible",
            student.getUsername(), fromLevel);
        return false;
    }

    /**
     * Check if a student has graduated from a program at the specified level,
     * regardless of diploma eligibility. Used for same-level admissions like
     * VGS→VGS (påbygg), where kompetansebevis is sufficient.
     */
    private boolean checkGraduatedFromLevel(User student, String fromLevel) {
        if (fromLevel == null) return true;
        if ("UNGDOMSSKOLE".equalsIgnoreCase(fromLevel)) return true;

        String institutionLevel = resolveSourceLevel(fromLevel);
        if (institutionLevel == null) return true;

        List<ProgramMember> memberships = memberDao.findByUser(student.getId());
        for (ProgramMember pm : memberships) {
            if (!pm.isGraduated()) continue;
            Program program = pm.getProgram();
            if (program == null || program.getInstitution() == null) continue;

            String progLevel = program.getInstitution().getLevel();
            if (institutionLevel.equalsIgnoreCase(progLevel)) {
                LOG.debug("Student '{}' has graduated from {} level (kompetansebevis/vitnemål) — eligible for påbygg",
                    student.getUsername(), fromLevel);
                return true;
            }
        }

        LOG.info("Student '{}' has NOT graduated from {} level — NOT eligible",
            student.getUsername(), fromLevel);
        return false;
    }

    /**
     * Determine what prerequisite degree a university program requires based on its subjects.
     * - Programs with MASTER_1/MASTER_2 subjects → require BACHELOR degree
     * - Programs with PHD_1/PHD_2/PHD_3 subjects → require MASTER degree
     * - Programs with BACHELOR_1/BACHELOR_2/BACHELOR_3 subjects → no prerequisite (or VGS diploma)
     * Returns null if no specific university degree is required.
     */
    private String getRequiredDegreeForProgram(Program program) {
        if (program == null || program.getSubjects() == null) return null;

        boolean hasPhdSubjects = program.getSubjects().stream()
            .anyMatch(s -> s.getYearLevel() != null && s.getYearLevel().startsWith("PHD_"));
        if (hasPhdSubjects) return "MASTER";

        boolean hasMasterSubjects = program.getSubjects().stream()
            .anyMatch(s -> s.getYearLevel() != null && s.getYearLevel().startsWith("MASTER_"));
        if (hasMasterSubjects) return "BACHELOR";

        return null; // Bachelor programs don't require a university degree
    }

    /**
     * Check if a student has completed (graduated with diploma) from a university program
     * at the specified degree level (BACHELOR or MASTER).
     */
    private boolean hasCompletedDegree(User student, String degreeLevel) {
        List<ProgramMember> memberships = memberDao.findByUser(student.getId());
        for (ProgramMember pm : memberships) {
            if (!pm.isGraduated() || !pm.isDiplomaEligible()) continue;
            Program program = pm.getProgram();
            if (program == null || program.getInstitution() == null) continue;

            // Must be from a university
            if (!"UNIVERSITET".equalsIgnoreCase(program.getInstitution().getLevel())) continue;

            // Load subjects to determine degree level
            Program loaded = programDao.findWithSubjects(program.getId());
            if (loaded == null || loaded.getSubjects() == null) continue;

            if ("BACHELOR".equals(degreeLevel)) {
                // Check if the program has bachelor subjects (BACHELOR_3 = final year)
                boolean isBachelorProgram = loaded.getSubjects().stream()
                    .anyMatch(s -> "BACHELOR_3".equals(s.getYearLevel()));
                if (isBachelorProgram) {
                    LOG.debug("Student '{}' has completed BACHELOR degree — eligible for MASTER",
                        student.getUsername());
                    return true;
                }
            } else if ("MASTER".equals(degreeLevel)) {
                // Check if the program has master subjects (MASTER_2 = final year)
                boolean isMasterProgram = loaded.getSubjects().stream()
                    .anyMatch(s -> "MASTER_2".equals(s.getYearLevel()));
                if (isMasterProgram) {
                    LOG.debug("Student '{}' has completed MASTER degree — eligible for PHD",
                        student.getUsername());
                    return true;
                }
            }
        }

        LOG.info("Student '{}' does NOT have required {} degree", student.getUsername(), degreeLevel);
        return false;
    }

    /**
     * Detect the first year level for a program by looking at its subjects.
     * For university programs, this determines whether it's a bachelor (BACHELOR_1),
     * master (MASTER_1), or PhD (PHD_1) program.
     */
    private String detectFirstYear(Program program, String fallback) {
        if (program.getSubjects() == null || program.getSubjects().isEmpty()) return fallback;

        // Priority order for detection
        List<String> firstYears = List.of("BACHELOR_1", "MASTER_1", "PHD_1", "VG1", "VG3_PABYGG", "1", "8");
        for (String fy : firstYears) {
            for (Subject s : program.getSubjects()) {
                if (fy.equals(s.getYearLevel())) return fy;
            }
        }

        // Fallback: use the year level of the first subject
        String firstSubjectYear = program.getSubjects().iterator().next().getYearLevel();
        return firstSubjectYear != null ? firstSubjectYear : fallback;
    }

    /**
     * Detect the HIGHEST year level in a program's subjects.
     * Used for same-level transfers (e.g. VGS→VGS påbygg) where students
     * should be placed at the last year (VG3) rather than the first (VG1).
     */
    private String detectLastYear(Program program, String fallback) {
        if (program.getSubjects() == null || program.getSubjects().isEmpty()) return fallback;

        // Reverse priority order: highest year level first
        List<String> lastYears = List.of("VG3", "VG3_PABYGG", "VG2", "VG1",
            "PHD_3", "PHD_2", "PHD_1", "MASTER_2", "MASTER_1",
            "BACHELOR_3", "BACHELOR_2", "BACHELOR_1",
            "10", "9", "8", "4", "3", "2", "1");
        for (String ly : lastYears) {
            for (Subject s : program.getSubjects()) {
                if (ly.equals(s.getYearLevel())) return ly;
            }
        }

        // Fallback: use the year level of the last subject
        String lastSubjectYear = null;
        for (Subject s : program.getSubjects()) {
            lastSubjectYear = s.getYearLevel();
        }
        return lastSubjectYear != null ? lastSubjectYear : fallback;
    }

    // ========================================================================
    //  Mappers
    // ========================================================================

    private AdmissionDto.PeriodResponse toPeriodResponse(AdmissionPeriod p) {
        int appCount = admissionDao.findApplicationsByPeriod(p.getId()).size();
        return new AdmissionDto.PeriodResponse(
            p.getId(), p.getName(), p.getFromLevel(), p.getToLevel(),
            p.getStartDate().toString(), p.getEndDate().toString(), p.getStatus(),
            p.getMaxChoices(), p.getInstitution().getId(), p.getInstitution().getName(),
            appCount
        );
    }

    private AdmissionDto.ApplicationResponse toApplicationResponse(Application a) {
        return new AdmissionDto.ApplicationResponse(
            a.getId(), a.getPeriod().getId(), a.getPeriod().getName(),
            a.getProgram().getId(), a.getProgram().getName(),
            a.getPeriod().getInstitution().getName(),
            a.getPriority(), a.getGpaSnapshot(), a.getStatus(),
            a.getSubmittedAt() != null ? a.getSubmittedAt().toString() : null,
            a.getProcessedAt() != null ? a.getProcessedAt().toString() : null
        );
    }
}
