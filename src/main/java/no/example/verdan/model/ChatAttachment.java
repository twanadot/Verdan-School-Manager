package no.example.verdan.model;

import jakarta.persistence.*;

/**
 * File attachment linked to a chat message.
 */
@Entity
@Table(name = "chat_attachments")
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ChatMessage message;

    /** Original file name uploaded by user. */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** Internal path in the Docker volume /app/data/uploads/. */
    @Column(nullable = false, length = 500)
    private String storedPath;

    /** File size in bytes. */
    private long fileSize;

    /** MIME type (e.g. image/png, application/pdf). */
    @Column(length = 100)
    private String mimeType;

    public ChatAttachment() {}

    public ChatAttachment(ChatMessage message, String fileName, String storedPath, long fileSize, String mimeType) {
        this.message = message;
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public ChatMessage getMessage() { return message; }
    public void setMessage(ChatMessage message) { this.message = message; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    /** Helper to check if this attachment is an image. */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
