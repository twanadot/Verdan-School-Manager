package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Represents an admission period where students can apply to programs.
 * Created by the receiving institution's admin.
 * 
 * Example: "Opptak VGS 2026" — students from ungdomsskole can apply
 * to VGS programs during start_date → end_date.
 */
@Entity
@Table(name = "admission_periods",
    indexes = {
        @Index(name = "idx_ap_inst", columnList = "institution_id"),
        @Index(name = "idx_ap_status", columnList = "status")
    }
)
public class AdmissionPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private String name;

    /** Education level that applicants come FROM (e.g. UNGDOMSSKOLE) */
    @Column(name = "from_level", length = 20, nullable = false)
    private String fromLevel;

    /** Education level that applicants apply TO (e.g. VGS) */
    @Column(name = "to_level", length = 20, nullable = false)
    private String toLevel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** OPEN, CLOSED, PROCESSED */
    @Column(length = 20, nullable = false)
    private String status = "OPEN";

    /** Max number of choices a student can make (default 5) */
    @Column(name = "max_choices")
    private int maxChoices = 5;

    // --- CONSTRUCTORS ---
    public AdmissionPeriod() {}

    // --- GETTERS AND SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFromLevel() { return fromLevel; }
    public void setFromLevel(String fromLevel) { this.fromLevel = fromLevel; }

    public String getToLevel() { return toLevel; }
    public void setToLevel(String toLevel) { this.toLevel = toLevel; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getMaxChoices() { return maxChoices; }
    public void setMaxChoices(int maxChoices) { this.maxChoices = maxChoices; }
}
