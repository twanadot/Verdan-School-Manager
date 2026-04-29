package no.example.verdan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents an educational institution (school, college, university).
 * All data (users, rooms, subjects) is isolated by institution.
 */
@Entity
@Table(name = "institutions")
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    private String location;

    /** Education level: UNGDOMSSKOLE, VGS, FAGSKOLE, UNIVERSITET */
    @Column(length = 20)
    private String level;

    @Column(nullable = false)
    private boolean active = true;

    /** Ownership type: PUBLIC or PRIVATE */
    @Column(length = 10)
    private String ownership = "PUBLIC";

    public Institution() {}

    public Institution(String name) {
        this.name = name;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }
}
