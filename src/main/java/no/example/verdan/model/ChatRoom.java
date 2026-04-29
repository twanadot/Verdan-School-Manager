package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chat room (1:1 direct message or group chat).
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Display name for group chats; null for 1:1 conversations. */
    @Column(length = 100)
    private String name;

    /** True = group chat, False = direct 1:1 conversation. */
    @Column(nullable = false)
    private boolean isGroup;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "chatRoom", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ChatMember> members = new ArrayList<>();

    public ChatRoom() {}

    public ChatRoom(String name, boolean isGroup, User createdBy) {
        this.name = name;
        this.isGroup = isGroup;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ChatMember> getMembers() { return members; }
    public void setMembers(List<ChatMember> members) { this.members = members; }
}
