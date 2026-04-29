package no.example.verdan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(unique = true, nullable = false)
  private String roomNumber;

  private String roomType;
  private int capacity;

  @ManyToOne
  @JoinColumn(name = "institution_id")
  private Institution institution;

  // --- CONSTRUCTORS ---
  
  public Room() {}
  
  /**
   * Constructor used by DataSeeder.
   */
  public Room(String roomNumber, String roomType, int capacity) {
      this.roomNumber = roomNumber;
      this.roomType = roomType;
      this.capacity = capacity;
  }
  
  // --- GETTERS AND SETTERS ---

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public String getRoomNumber() { return roomNumber; }
  public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

  public String getRoomType() { return roomType; }
  public void setRoomType(String roomType) { this.roomType = roomType; }

  public int getCapacity() { return capacity; }
  public void setCapacity(int capacity) { this.capacity = capacity; }

  public Institution getInstitution() { return institution; }
  public void setInstitution(Institution institution) { this.institution = institution; }
}