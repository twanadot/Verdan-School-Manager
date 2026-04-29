package no.example.verdan.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for the chat system.
 */
public class ChatDto {

    // ─── Room DTOs ───

    public record RoomResponse(
        int id,
        String name,
        boolean isGroup,
        int createdById,
        List<MemberInfo> members,
        MessagePreview lastMessage,
        long unreadCount,
        LocalDateTime createdAt
    ) {}

    public record MemberInfo(
        int userId,
        String username,
        String fullName,
        String role,
        String institutionName,
        boolean online
    ) {}

    public record CreateGroupRequest(
        String name,
        List<Integer> memberIds
    ) {}

    public record DirectChatRequest(
        int otherUserId
    ) {}

    // ─── Message DTOs ───

    public record MessageResponse(
        int id,
        int roomId,
        int senderId,
        String senderUsername,
        String senderName,
        String senderRole,
        String content,
        LocalDateTime sentAt,
        LocalDateTime editedAt,
        boolean deleted,
        ReplyPreview replyTo,
        List<AttachmentResponse> attachments,
        List<ReactionGroup> reactions
    ) {}

    public record MessagePreview(
        int id,
        int senderId,
        String senderName,
        String content,
        LocalDateTime sentAt,
        boolean deleted
    ) {}

    public record SendMessageRequest(
        String content,
        Integer replyToId,
        List<Integer> attachmentIds
    ) {}

    public record EditMessageRequest(
        String content
    ) {}

    public record ReplyPreview(
        int id,
        String senderName,
        String content
    ) {}

    // ─── Attachment DTOs ───

    public record AttachmentResponse(
        int id,
        String fileName,
        long fileSize,
        String mimeType,
        boolean isImage,
        String downloadUrl
    ) {}

    public record UploadResponse(
        int id,
        String fileName,
        long fileSize,
        String mimeType
    ) {}

    // ─── Reaction DTOs ───

    public record ReactionRequest(
        String emoji
    ) {}

    public record ReactionGroup(
        String emoji,
        int count,
        List<String> usernames,
        boolean currentUserReacted
    ) {}

    // ─── Status DTOs ───

    public record StatusResponse(
        int userId,
        boolean online,
        LocalDateTime lastSeen
    ) {}

    public record StatusBulkRequest(
        List<Integer> userIds
    ) {}

    // ─── Contact DTO (reused from old messages) ───

    public record ContactUser(
        int id,
        String username,
        String fullName,
        String role,
        String institutionName,
        boolean online
    ) {}
}
