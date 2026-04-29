package no.example.verdan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(unique = true, nullable = false)
  private String username;

  private String password;

  // Role type: TEACHER / STUDENT / ADMIN
  private String role;
  
  // New fields for detailed user info
  private String firstName;
  private String lastName;
  
  @Column(unique = true)
  private String email; 
  
  private String phone;

  private String gender; // MALE or FEMALE

  private LocalDate birthDate;

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  // --- CONSTRUCTORS ---
  
  // Required default constructor for Hibernate
  public User() {} 
  
  /**
   * Constructor used by DataSeeder to initialize full user details.
   */
  public User(String username, String firstName, String lastName, String phone, String email, String role) {
      this.username = username;
      this.firstName = firstName;
      this.lastName = lastName;
      this.phone = phone;
      this.email = email;
      this.role = role;
  }

  // --- GETTERS AND SETTERS ---
  
  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  
  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }

  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }

  public LocalDate getBirthDate() { return birthDate; }
  public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }
}