package no.example.verdan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
  name = "subject_assignments",
  indexes = {
    @Index(name = "idx_subj_user", columnList = "username"),
    @Index(name = "idx_subj_role", columnList = "role"),
    @Index(name = "idx_subj_subject", columnList = "subject")
  }
)
public class SubjectAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false)
  private String username; // Username of the user (student or teacher)

  @Column(nullable = false)
  private String role; // STUDENT or TEACHER

  @Column(nullable = false)
  private String subject;

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }
}
