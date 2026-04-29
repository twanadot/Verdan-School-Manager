package no.example.verdan.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  @ManyToMany
  @JoinTable(
    name = "booking_rooms",
    joinColumns = @JoinColumn(name = "booking_id"),
    inverseJoinColumns = @JoinColumn(name = "room_id")
  )
  private List<Room> rooms = new ArrayList<>();

  private LocalDateTime startDateTime;
  private LocalDateTime endDateTime;

  @Enumerated(EnumType.STRING)
  private BookingStatus status;

  private String description;

  // Who created the booking (teacher's username)
  private String createdBy;

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public List<Room> getRooms() { return rooms; }
  public void setRooms(List<Room> rooms) { this.rooms = rooms; }

  public LocalDateTime getStartDateTime() { return startDateTime; }
  public void setStartDateTime(LocalDateTime d) { this.startDateTime = d; }

  public LocalDateTime getEndDateTime() { return endDateTime; }
  public void setEndDateTime(LocalDateTime d) { this.endDateTime = d; }

  public BookingStatus getStatus() { return status; }
  public void setStatus(BookingStatus s) { this.status = s; }

  public String getDescription() { return description; }
  public void setDescription(String d) { this.description = d; }

  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String c) { this.createdBy = c; }

  // Subject associated with this booking
  @Column(nullable = false)
  private String subject;

  // Optional: link to a specific program/class (e.g. 8A, 8B)
  @ManyToOne
  @JoinColumn(name = "program_id")
  private Program program;

  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }

  public Program getProgram() { return program; }
  public void setProgram(Program program) { this.program = program; }
}
