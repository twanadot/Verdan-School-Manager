package no.example.verdan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A single chat message within a ChatRoom.
 * Supports editing, soft-delete, replies, attachments, and reactions.
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_msg_room_sent", columnList = "chat_room_id, sentAt"),
    @Index(name = "idx_chat_msg_room", columnList = "chat_room_id")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    /** Non-null if the message was edited. */
    private LocalDateTime editedAt;

    /** Soft-delete flag. If true, show "Message deleted" placeholder. */
    @Column(nullable = false)
    private boolean deleted = false;

    /** Optional reply reference. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatAttachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatReaction> reactions = new ArrayList<>();

    public ChatMessage() {}

    public ChatMessage(ChatRoom chatRoom, User sender, String content) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.content = content;
        this.sentAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public ChatRoom getChatRoom() { return chatRoom; }
    public void setChatRoom(ChatRoom chatRoom) { this.chatRoom = chatRoom; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public ChatMessage getReplyTo() { return replyTo; }
    public void setReplyTo(ChatMessage replyTo) { this.replyTo = replyTo; }

    public List<ChatAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<ChatAttachment> attachments) { this.attachments = attachments; }

    public List<ChatReaction> getReactions() { return reactions; }
    public void setReactions(List<ChatReaction> reactions) { this.reactions = reactions; }
}
