package no.example.verdan.model;

import jakarta.persistence.*;

/**
 * Emoji reaction on a chat message. One per user per emoji per message.
 */
@Entity
@Table(name = "chat_reactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id", "emoji"}),
    indexes = @Index(name = "idx_chat_react_msg", columnList = "message_id")
)
public class ChatReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The emoji character (e.g. 👍, ❤️, 😂). */
    @Column(nullable = false, length = 10)
    private String emoji;

    public ChatReaction() {}

    public ChatReaction(ChatMessage message, User user, String emoji) {
        this.message = message;
        this.user = user;
        this.emoji = emoji;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public ChatMessage getMessage() { return message; }
    public void setMessage(ChatMessage message) { this.message = message; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
}
