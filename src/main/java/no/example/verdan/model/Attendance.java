package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // Changed from Long to Integer to match Controller/DAO

    // Student (User)
    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    // Date of absence/attendance
    private LocalDate dateOf;

    // Present / Absent / Sick / Late
    private String status;

    // Note / Comment
    @Column(length = 1024)
    private String note;

    // 🔹 NEW: Which subject code (Subject.code) this attendance record belongs to
    private String subjectCode;

    @ManyToOne
    @JoinColumn(name = "institution_id")
    private Institution institution;

    /** Whether this absence is excused (has documentation like doctor's note). Excused absences do not count against limits. */
    @Column(nullable = false)
    private boolean excused = false;

    public Attendance() {
    }

    // --- Getters / Setters ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public LocalDate getDateOf() {
        return dateOf;
    }

    public void setDateOf(LocalDate dateOf) {
        this.dateOf = dateOf;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public boolean isExcused() { return excused; }
    public void setExcused(boolean excused) { this.excused = excused; }
}