package no.example.verdan.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an educational program/track/degree within an institution.
 * - VGS: "Linje" (e.g. Studiespesialiserende, Elektro)
 * - Fagskole: "Fagskolegrad"
 * - Universitet: "Degree" (e.g. Informatikk Bachelor)
 *
 * A program can contain many subjects, and a subject can belong to multiple programs.
 */
@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @ManyToOne
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToMany
    @JoinTable(
        name = "program_subjects",
        joinColumns = @JoinColumn(name = "program_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    private Set<Subject> subjects = new HashSet<>();

    // --- CONSTRUCTORS ---
    public Program() {}

    public Program(String name, Institution institution) {
        this.name = name;
        this.institution = institution;
    }

    // --- GETTERS AND SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    /** Default minimum GPA for admission (e.g. 3.0). Null = no minimum. */
    @Column(name = "min_gpa")
    private Double minGpa;

    /** Default max students for admission. Null = unlimited. */
    @Column(name = "max_students")
    private Integer maxStudents;

    /** Comma-separated admission prerequisites, e.g. "Studiespesialisering, R-matte, Fysikk" */
    @Column(name = "prerequisites", length = 1000)
    private String prerequisites;

    /** Whether attendance tracking/limits are enforced for this program */
    @Column(name = "attendance_required", nullable = false)
    private boolean attendanceRequired = false;

    /** Minimum required attendance percentage (e.g. 90 means max 10% absence). Null = no limit. */
    @Column(name = "min_attendance_pct")
    private Integer minAttendancePct;

    /** Program type for VGS: STUDIEFORBEREDENDE or YRKESFAG. Null for non-VGS. */
    @Column(name = "program_type", length = 30)
    private String programType;

    public Set<Subject> getSubjects() { return subjects; }
    public void setSubjects(Set<Subject> subjects) { this.subjects = subjects; }

    public void addSubject(Subject subject) { this.subjects.add(subject); }
    public void removeSubject(Subject subject) { this.subjects.remove(subject); }

    public Double getMinGpa() { return minGpa; }
    public void setMinGpa(Double minGpa) { this.minGpa = minGpa; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }

    public boolean isAttendanceRequired() { return attendanceRequired; }
    public void setAttendanceRequired(boolean attendanceRequired) { this.attendanceRequired = attendanceRequired; }

    public Integer getMinAttendancePct() { return minAttendancePct; }
    public void setMinAttendancePct(Integer minAttendancePct) { this.minAttendancePct = minAttendancePct; }

    public String getProgramType() { return programType; }
    public void setProgramType(String programType) { this.programType = programType; }
}
