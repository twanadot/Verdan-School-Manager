package no.example.verdan.model;

import jakarta.persistence.*;

/**
 * Requirements for a specific program within an admission period.
 * E.g. min GPA of 3.0, max 30 students for "Studiespesialiserende".
 */
@Entity
@Table(name = "admission_requirements",
    uniqueConstraints = @UniqueConstraint(columnNames = {"period_id", "program_id"}),
    indexes = @Index(name = "idx_ar_period", columnList = "period_id")
)
public class AdmissionRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private AdmissionPeriod period;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** Minimum GPA required (e.g. 3.0). Null means no minimum. */
    @Column(name = "min_gpa")
    private Double minGpa;

    /** Maximum number of students that can be accepted. */
    @Column(name = "max_students")
    private Integer maxStudents;

    // --- CONSTRUCTORS ---
    public AdmissionRequirement() {}

    // --- GETTERS AND SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public AdmissionPeriod getPeriod() { return period; }
    public void setPeriod(AdmissionPeriod period) { this.period = period; }

    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }

    public Double getMinGpa() { return minGpa; }
    public void setMinGpa(Double minGpa) { this.minGpa = minGpa; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
}
