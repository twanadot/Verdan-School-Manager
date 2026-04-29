package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A student's application to a specific program within an admission period.
 * Students can have up to maxChoices applications per period, ranked by priority.
 */
@Entity
@Table(name = "applications",
    indexes = {
        @Index(name = "idx_app_period", columnList = "period_id"),
        @Index(name = "idx_app_student", columnList = "student_id"),
        @Index(name = "idx_app_status", columnList = "status")
    }
)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private AdmissionPeriod period;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** Priority rank: 1 = first choice, 2 = second choice, etc. */
    @Column(nullable = false)
    private int priority;

    /** Calculated GPA snapshot at time of application submission. */
    @Column(name = "gpa_snapshot")
    private Double gpaSnapshot;

    /** PENDING, ACCEPTED, WAITLISTED, REJECTED, WITHDRAWN */
    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // --- CONSTRUCTORS ---
    public Application() {}

    // --- GETTERS AND SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public AdmissionPeriod getPeriod() { return period; }
    public void setPeriod(AdmissionPeriod period) { this.period = period; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Double getGpaSnapshot() { return gpaSnapshot; }
    public void setGpaSnapshot(Double gpaSnapshot) { this.gpaSnapshot = gpaSnapshot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
