package no.example.verdan.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "grades")
public class Grade {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "user_id") 
  private User student;  // Updated to refer to User instead of Client

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  private String subject;
  private String value;
  private LocalDate dateGiven;
  private String teacherUsername;
  private String yearLevel;
  private String originalValue;  // Preserved when IV is applied due to absence limit
  private boolean retake;        // True if grade is from a privatist exam (bypasses absence check)

  // --- CONSTRUCTORS ---
  public Grade() {}

  public Grade(User student, String subject, String value, String teacherUsername) {
      this.student = student;
      this.subject = subject;
      this.value = value;
      this.teacherUsername = teacherUsername;
      this.dateGiven = LocalDate.now();
  }

  // --- GETTERS AND SETTERS ---
  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public User getStudent() { return student; }
  public void setStudent(User student) { this.student = student; }

  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }

  public String getValue() { return value; }
  public void setValue(String value) { this.value = value; }

  public LocalDate getDateGiven() { return dateGiven; }
  public void setDateGiven(LocalDate d) { this.dateGiven = d; }

  public String getTeacherUsername() { return teacherUsername; }
  public void setTeacherUsername(String t) { this.teacherUsername = t; }

  public String getYearLevel() { return yearLevel; }
  public void setYearLevel(String yearLevel) { this.yearLevel = yearLevel; }

  public String getOriginalValue() { return originalValue; }
  public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }

  public boolean isRetake() { return retake; }
  public void setRetake(boolean retake) { this.retake = retake; }
}