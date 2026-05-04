package no.example.verdan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "subjects", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"code", "institution_id"})
})
public class Subject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String code;

  @Column
  private String description;

  @Column(nullable = false)
  private String level = "UNIVERSITET"; // UNGDOMSSKOLE, VGS, FAGSKOLE, UNIVERSITET

  /** Education program/track name, e.g. "Studiespesialiserende", "Informatikk", "Klasse A" */
  @Column
  private String program;

  /** Year level within the program, e.g. "8", "VG1", "BACHELOR_1", "MASTER_4" */
  @Column(name = "year_level")
  private String yearLevel;

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  // --- CONSTRUCTORS ---
  public Subject() {}
  
  public Subject(String code, String name) {
      this.code = code;
      this.name = name;
      this.description = name;
      this.level = "UNIVERSITET";
  }

  public Subject(String code, String name, String level) {
      this.code = code;
      this.name = name;
      this.description = name;
      this.level = level;
  }

  // --- GETTERS AND SETTERS ---
  public int getId() { return id; }
  public void setId(int id) { this.id = id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getLevel() { return level; }
  public void setLevel(String level) { this.level = level; }

  public String getProgram() { return program; }
  public void setProgram(String program) { this.program = program; }

  public String getYearLevel() { return yearLevel; }
  public void setYearLevel(String yearLevel) { this.yearLevel = yearLevel; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }
}