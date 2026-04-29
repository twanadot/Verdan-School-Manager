package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks which users belong to which chat rooms, plus their last-read timestamp.
 */
@Entity
@Table(name = "chat_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "user_id"}),
    indexes = @Index(name = "idx_chat_member_room", columnList = "chat_room_id")
)
public class ChatMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** Last time this user read messages in this room. Used for unread count. */
    private LocalDateTime lastReadAt;

    /** Whether this user has hidden this chat from their sidebar. */
    @Column(nullable = false)
    private boolean hidden = false;

    public ChatMember() {}

    public ChatMember(ChatRoom chatRoom, User user) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.joinedAt = LocalDateTime.now();
        this.lastReadAt = LocalDateTime.now();
        this.hidden = false;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public ChatRoom getChatRoom() { return chatRoom; }
    public void setChatRoom(ChatRoom chatRoom) { this.chatRoom = chatRoom; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
