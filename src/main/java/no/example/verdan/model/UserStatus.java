package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks online/offline status for users via heartbeat.
 * If lastSeen is older than 60 seconds, user is considered offline.
 */
@Entity
@Table(name = "user_status")
public class UserStatus {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime lastSeen;

    /** Threshold in seconds to consider user offline. */
    @Transient
    public static final int OFFLINE_THRESHOLD_SECONDS = 60;

    public UserStatus() {}

    public UserStatus(User user) {
        this.user = user;
        this.userId = user.getId();
        this.lastSeen = LocalDateTime.now();
    }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    /** Check if this user is currently considered online. */
    public boolean isOnline() {
        return lastSeen != null && lastSeen.plusSeconds(OFFLINE_THRESHOLD_SECONDS).isAfter(LocalDateTime.now());
    }
}
