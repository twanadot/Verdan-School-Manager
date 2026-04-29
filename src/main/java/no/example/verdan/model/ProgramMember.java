package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Represents a user's membership in a program (linje/klasse/degree/fagskolegrad).
 * When a student is added to a program, they are automatically enrolled in all subjects
 * that belong to that program.
 */
@Entity
@Table(name = "program_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"program_id", "user_id"}),
    indexes = {
        @Index(name = "idx_pm_program", columnList = "program_id"),
        @Index(name = "idx_pm_user", columnList = "user_id")
    }
)
public class ProgramMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** STUDENT or TEACHER */
    @Column(nullable = false, length = 20)
    private String role;

    /** Current year level within the program (e.g. "8", "VG1", "BACHELOR_1") */
    @Column(name = "year_level", length = 20)
    private String yearLevel;

    @Column(name = "enrolled_at")
    private LocalDate enrolledAt;

    /** Whether the student has graduated/completed this program */
    @Column(nullable = false)
    private boolean graduated = false;

    /** Whether the student qualifies for a diploma/degree (all subjects passed) */
    @Column(name = "diploma_eligible", nullable = false)
    private boolean diplomaEligible = false;

    /** Whether this graduated member has been archived (no longer shown in active graduated list) */
    @Column(nullable = false)
    private boolean archived = false;

    // --- CONSTRUCTORS ---
    public ProgramMember() {}

    public ProgramMember(Program program, User user, String role, String yearLevel) {
        this.program = program;
        this.user = user;
        this.role = role;
        this.yearLevel = yearLevel;
        this.enrolledAt = LocalDate.now();
    }

    // --- GETTERS AND SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getYearLevel() { return yearLevel; }
    public void setYearLevel(String yearLevel) { this.yearLevel = yearLevel; }

    public LocalDate getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDate enrolledAt) { this.enrolledAt = enrolledAt; }

    public boolean isGraduated() { return graduated; }
    public void setGraduated(boolean graduated) { this.graduated = graduated; }

    public boolean isDiplomaEligible() { return diplomaEligible; }
    public void setDiplomaEligible(boolean diplomaEligible) { this.diplomaEligible = diplomaEligible; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
