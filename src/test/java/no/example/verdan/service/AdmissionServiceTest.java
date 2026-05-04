package no.example.verdan.service;

import no.example.verdan.dao.*;
import no.example.verdan.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdmissionService — admission algorithm (processAdmissions).
 *
 * These tests verify the core business logic of the admission algorithm:
 * - GPA-based ranking and selection
 * - Capacity limits and waitlisting
 * - Priority-based fallback to second/third choices
 * - Automatic withdrawal of lower-priority applications upon acceptance
 * - GPA minimum requirement enforcement
 *
 * All DAO dependencies are mocked (Mockito) so tests run without a database.
 */
class AdmissionServiceTest {

    private AdmissionDao admissionDao;
    private ProgramDao programDao;
    private AdmissionService service;

    @BeforeEach
    void setUp() {
        admissionDao = mock(AdmissionDao.class);
        programDao = mock(ProgramDao.class);
        GradeDao gradeDao = mock(GradeDao.class);
        InstitutionDao institutionDao = mock(InstitutionDao.class);
        UserDao userDao = mock(UserDao.class);
        ProgramMemberDao memberDao = mock(ProgramMemberDao.class);
        SubjectAssignmentDao assignmentDao = mock(SubjectAssignmentDao.class);

        service = new AdmissionService(admissionDao, programDao, gradeDao,
                institutionDao, userDao, memberDao, assignmentDao);
    }

    // ── Helper methods ──

    private Institution makeInstitution(int id) {
        Institution inst = new Institution();
        inst.setId(id);
        inst.setName("Test Skole");
        return inst;
    }

    private AdmissionPeriod makePeriod(int id, Institution inst) {
        AdmissionPeriod period = new AdmissionPeriod();
        period.setId(id);
        period.setInstitution(inst);
        period.setName("Opptak 2026");
        period.setStatus("OPEN");
        return period;
    }

    private Program makeProgram(int id, String name) {
        Program p = new Program();
        p.setId(id);
        p.setName(name);
        return p;
    }

    private User makeStudent(int id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFirstName(username);
        u.setLastName("Test");
        return u;
    }

    private Application makeApp(int id, User student, Program program, int priority, double gpa) {
        Application app = new Application();
        app.setId(id);
        app.setStudent(student);
        app.setProgram(program);
        app.setPriority(priority);
        app.setGpaSnapshot(gpa);
        app.setStatus(ApplicationStatus.PENDING.name());
        return app;
    }

    private AdmissionRequirement makeReq(Program program, AdmissionPeriod period, Double minGpa, Integer maxStudents) {
        AdmissionRequirement req = new AdmissionRequirement();
        req.setProgram(program);
        req.setPeriod(period);
        req.setMinGpa(minGpa);
        req.setMaxStudents(maxStudents);
        return req;
    }

    // ── Tests ──

    @Test
    @DisplayName("Highest GPA accepted when capacity is 1")
    void highestGpaAccepted() {
        Institution inst = makeInstitution(1);
        AdmissionPeriod period = makePeriod(10, inst);
        Program program = makeProgram(100, "Realfag");
        User alice = makeStudent(1, "alice");
        User bob = makeStudent(2, "bob");

        // Alice: GPA 5.5, Bob: GPA 4.0 — both apply to same program
        Application appAlice = makeApp(1, alice, program, 1, 5.5);
        Application appBob = makeApp(2, bob, program, 1, 4.0);
        List<Application> apps = new ArrayList<>(List.of(appAlice, appBob));

        AdmissionRequirement req = makeReq(program, period, null, 1); // capacity = 1

        when(admissionDao.find(10)).thenReturn(period);
        when(admissionDao.findApplicationsByPeriod(10)).thenReturn(apps);
        when(admissionDao.findRequirements(10)).thenReturn(List.of(req));

        var result = service.processAdmissions(10, 1);

        assertEquals(1, result.accepted());
        assertEquals("ACCEPTED", appAlice.getStatus(), "Alice (GPA 5.5) should be accepted");
        assertEquals("WAITLISTED", appBob.getStatus(), "Bob (GPA 4.0) should be waitlisted");

        // Verify batch save was called
        verify(admissionDao).updateApplications(apps);
    }

    @Test
    @DisplayName("Student rejected when below minimum GPA")
    void belowMinGpaRejected() {
        Institution inst = makeInstitution(1);
        AdmissionPeriod period = makePeriod(10, inst);
        Program program = makeProgram(100, "Medisin");
        User student = makeStudent(1, "svak.elev");

        Application app = makeApp(1, student, program, 1, 3.0);
        List<Application> apps = new ArrayList<>(List.of(app));

        AdmissionRequirement req = makeReq(program, period, 4.5, 10); // minGPA = 4.5

        when(admissionDao.find(10)).thenReturn(period);
        when(admissionDao.findApplicationsByPeriod(10)).thenReturn(apps);
        when(admissionDao.findRequirements(10)).thenReturn(List.of(req));

        var result = service.processAdmissions(10, 1);

        assertEquals(0, result.accepted());
        assertEquals(1, result.rejected());
        assertEquals("REJECTED", app.getStatus());
    }

    @Test
    @DisplayName("Student falls back to second choice when first is full")
    void fallbackToSecondChoice() {
        Institution inst = makeInstitution(1);
        AdmissionPeriod period = makePeriod(10, inst);
        Program realfag = makeProgram(100, "Realfag");
        Program idrett = makeProgram(200, "Idrett");

        User topStudent = makeStudent(1, "top.elev");
        User fallbackStudent = makeStudent(2, "fallback.elev");

        // top.elev: GPA 5.5, first choice Realfag
        Application topApp = makeApp(1, topStudent, realfag, 1, 5.5);
        // fallback.elev: GPA 4.0, first choice Realfag, second choice Idrett
        Application fbApp1 = makeApp(2, fallbackStudent, realfag, 1, 4.0);
        Application fbApp2 = makeApp(3, fallbackStudent, idrett, 2, 4.0);

        List<Application> apps = new ArrayList<>(List.of(topApp, fbApp1, fbApp2));

        AdmissionRequirement reqRealfag = makeReq(realfag, period, null, 1); // capacity = 1
        AdmissionRequirement reqIdrett = makeReq(idrett, period, null, 5);  // capacity = 5

        when(admissionDao.find(10)).thenReturn(period);
        when(admissionDao.findApplicationsByPeriod(10)).thenReturn(apps);
        when(admissionDao.findRequirements(10)).thenReturn(List.of(reqRealfag, reqIdrett));

        var result = service.processAdmissions(10, 1);

        assertEquals(2, result.accepted(), "Both students should be accepted");
        assertEquals("ACCEPTED", topApp.getStatus(), "Top student gets Realfag");
        // After post-processing: fallback student was accepted on Idrett,
        // so WAITLISTED on Realfag becomes WITHDRAWN (student already placed)
        assertEquals("WITHDRAWN", fbApp1.getStatus(), "Waitlisted app auto-withdrawn after acceptance elsewhere");
        assertEquals("ACCEPTED", fbApp2.getStatus(), "Fallback student accepted on Idrett (2nd choice)");
    }

    @Test
    @DisplayName("Lower-priority apps auto-withdrawn when accepted at higher priority")
    void lowerPriorityAutoWithdrawn() {
        Institution inst = makeInstitution(1);
        AdmissionPeriod period = makePeriod(10, inst);
        Program realfag = makeProgram(100, "Realfag");
        Program idrett = makeProgram(200, "Idrett");

        User student = makeStudent(1, "elev");

        // Student applies to Realfag (priority 1) and Idrett (priority 2)
        Application app1 = makeApp(1, student, realfag, 1, 5.0);
        Application app2 = makeApp(2, student, idrett, 2, 5.0);

        List<Application> apps = new ArrayList<>(List.of(app1, app2));

        AdmissionRequirement req1 = makeReq(realfag, period, null, 10);
        AdmissionRequirement req2 = makeReq(idrett, period, null, 10);

        when(admissionDao.find(10)).thenReturn(period);
        when(admissionDao.findApplicationsByPeriod(10)).thenReturn(apps);
        when(admissionDao.findRequirements(10)).thenReturn(List.of(req1, req2));

        service.processAdmissions(10, 1);

        assertEquals("ACCEPTED", app1.getStatus(), "First choice accepted");
        assertEquals("WITHDRAWN", app2.getStatus(), "Second choice auto-withdrawn");
    }

    @Test
    @DisplayName("Confirm application sets CONFIRMED and auto-withdraws other ACCEPTED")
    void confirmApplicationWithdrawsOthers() {
        User student = makeStudent(1, "elev");
        Program p1 = makeProgram(100, "Realfag");
        Program p2 = makeProgram(200, "Idrett");

        Application accepted1 = makeApp(1, student, p1, 1, 5.0);
        accepted1.setStatus(ApplicationStatus.ACCEPTED.name());
        Application accepted2 = makeApp(2, student, p2, 1, 5.0);
        accepted2.setStatus(ApplicationStatus.ACCEPTED.name());

        when(admissionDao.findApplicationsByStudent(1)).thenReturn(new ArrayList<>(List.of(accepted1, accepted2)));

        service.confirmApplication(1, 1);

        assertEquals("CONFIRMED", accepted1.getStatus(), "Chosen application confirmed");
        assertEquals("WITHDRAWN", accepted2.getStatus(), "Other application auto-withdrawn");

        // Verify atomic batch save (not individual saves)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Application>> captor = ArgumentCaptor.forClass(List.class);
        verify(admissionDao).updateApplications(captor.capture());
        assertEquals(2, captor.getValue().size(), "Both changes saved in single transaction");
    }

    @Test
    @DisplayName("Cannot confirm a non-ACCEPTED application")
    void confirmNonAcceptedThrows() {
        User student = makeStudent(1, "elev");
        Program p = makeProgram(100, "Realfag");
        Application pending = makeApp(1, student, p, 1, 5.0);
        pending.setStatus(ApplicationStatus.PENDING.name());

        when(admissionDao.findApplicationsByStudent(1)).thenReturn(new ArrayList<>(List.of(pending)));

        assertThrows(ValidationException.class, () -> service.confirmApplication(1, 1));
    }

    @Test
    @DisplayName("Period marked as PROCESSED after algorithm runs")
    void periodMarkedProcessed() {
        Institution inst = makeInstitution(1);
        AdmissionPeriod period = makePeriod(10, inst);

        when(admissionDao.find(10)).thenReturn(period);
        when(admissionDao.findApplicationsByPeriod(10)).thenReturn(new ArrayList<>());
        when(admissionDao.findRequirements(10)).thenReturn(List.of());

        service.processAdmissions(10, 1);

        assertEquals("PROCESSED", period.getStatus());
        verify(admissionDao).update(period);
    }
}
