package no.example.verdan.service;

import no.example.verdan.dao.*;
import no.example.verdan.dto.ChatDto;
import no.example.verdan.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core chat service handling rooms, messages, reactions, and contacts.
 * Optimized to minimize database queries using batch fetching.
 */
public class ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

    private final ChatRoomDao roomDao;
    private final ChatMessageDao messageDao;
    private final ChatReactionDao reactionDao;
    private final ChatAttachmentDao attachmentDao;
    private final UserDao userDao;
    private final UserStatusDao statusDao;
    private final SubjectAssignmentDao saDao;
    private final ProgramMemberDao memberDao;

    public ChatService() {
        this(new ChatRoomDao(), new ChatMessageDao(), new ChatReactionDao(),
             new ChatAttachmentDao(), new UserDao(), new UserStatusDao(), new SubjectAssignmentDao(),
             new ProgramMemberDao());
    }

    public ChatService(ChatRoomDao roomDao, ChatMessageDao messageDao, ChatReactionDao reactionDao,
                        ChatAttachmentDao attachmentDao, UserDao userDao, UserStatusDao statusDao,
                        SubjectAssignmentDao saDao, ProgramMemberDao memberDao) {
        this.roomDao = roomDao;
        this.messageDao = messageDao;
        this.reactionDao = reactionDao;
        this.attachmentDao = attachmentDao;
        this.userDao = userDao;
        this.statusDao = statusDao;
        this.saDao = saDao;
        this.memberDao = memberDao;
    }

    // ─── Rooms ───

    /**
     * Get all rooms for a user with last message preview and unread count.
     * Optimized: uses bulk queries instead of per-room queries.
     */
    public List<ChatDto.RoomResponse> getRoomsForUser(int userId) {
        List<ChatRoom> rooms = roomDao.findRoomsForUser(userId);
        if (rooms.isEmpty()) return List.of();

        List<Integer> roomIds = rooms.stream().map(ChatRoom::getId).toList();

        // Bulk-fetch all members for all rooms (still per-room but cached in one pass)
        Map<Integer, List<ChatMember>> allMembers = new HashMap<>();
        Set<Integer> allUserIds = new HashSet<>();
        for (ChatRoom room : rooms) {
            List<ChatMember> members = roomDao.getMembers(room.getId());
            allMembers.put(room.getId(), members);
            members.forEach(m -> allUserIds.add(m.getUser().getId()));
        }

        // Bulk-fetch online statuses
        Map<Integer, Boolean> onlineMap = getOnlineMap(new ArrayList<>(allUserIds));

        // Bulk-fetch latest messages for all rooms (1 query instead of N)
        Map<Integer, ChatMessage> latestMessages = messageDao.findLatestForRooms(roomIds);

        // Build lastReadAt map for unread counting
        Map<Integer, LocalDateTime> lastReadMap = new HashMap<>();
        for (ChatRoom room : rooms) {
            List<ChatMember> members = allMembers.get(room.getId());
            LocalDateTime lastReadAt = members.stream()
                .filter(m -> m.getUser().getId() == userId)
                .map(ChatMember::getLastReadAt)
                .findFirst().orElse(null);
            lastReadMap.put(room.getId(), lastReadAt);
        }

        // Bulk-fetch unread counts (fewer queries)
        Map<Integer, Long> unreadCounts = messageDao.countUnreadForRooms(roomIds, lastReadMap);

        List<ChatDto.RoomResponse> result = new ArrayList<>();
        for (ChatRoom room : rooms) {
            List<ChatMember> members = allMembers.get(room.getId());
            ChatMessage latest = latestMessages.get(room.getId());
            long unread = unreadCounts.getOrDefault(room.getId(), 0L);

            List<ChatDto.MemberInfo> memberInfos = members.stream()
                .map(m -> toMemberInfo(m.getUser(), onlineMap))
                .toList();

            ChatDto.MessagePreview preview = latest != null ? new ChatDto.MessagePreview(
                latest.getId(),
                latest.getSender().getId(),
                fullName(latest.getSender()),
                latest.isDeleted() ? "Message deleted" : truncate(latest.getContent(), 60),
                latest.getSentAt(),
                latest.isDeleted()
            ) : null;

            // For direct chats, use the other person's name
            String roomName = room.getName();
            if (!room.isGroup()) {
                roomName = members.stream()
                    .filter(m -> m.getUser().getId() != userId)
                    .map(m -> fullName(m.getUser()))
                    .findFirst().orElse("Chat");
            }

            result.add(new ChatDto.RoomResponse(
                room.getId(), roomName, room.isGroup(),
                room.getCreatedBy() != null ? room.getCreatedBy().getId() : 0,
                memberInfos, preview, unread, room.getCreatedAt()
            ));
        }

        // Sort by latest message time (most recent first)
        result.sort((a, b) -> {
            LocalDateTime ta = a.lastMessage() != null ? a.lastMessage().sentAt() : a.createdAt();
            LocalDateTime tb = b.lastMessage() != null ? b.lastMessage().sentAt() : b.createdAt();
            return tb.compareTo(ta);
        });

        return result;
    }

    /** Get or create a direct chat between two users. */
    public ChatDto.RoomResponse getOrCreateDirectChat(int userId, int otherUserId) {
        if (userId == otherUserId) throw new IllegalStateException("Cannot chat with yourself");

        User user = userDao.find(userId);
        User other = userDao.find(otherUserId);
        if (other == null) throw new NotFoundException("User not found");

        // Validate contact rules
        if (!canContact(user, other)) {
            throw new IllegalStateException("You cannot start a chat with this user");
        }

        ChatRoom existing = roomDao.findDirectRoom(userId, otherUserId);
        if (existing != null) {
            // Unhide the room if it was hidden
            roomDao.setRoomHidden(existing.getId(), userId, false);
            return getRoomsForUser(userId).stream()
                .filter(r -> r.id() == existing.getId())
                .findFirst().orElse(null);
        }

        ChatRoom room = new ChatRoom(null, false, user);
        roomDao.createWithMembers(room, List.of(user, other));
        LOG.info("Direct chat created: {} ↔ {}", user.getUsername(), other.getUsername());

        return getRoomsForUser(userId).stream()
            .filter(r -> r.id() == room.getId())
            .findFirst().orElse(null);
    }

    /** Hide a direct chat from the user's sidebar. Messages are preserved. */
    public void hideRoom(int roomId, int userId) {
        ChatRoom room = roomDao.find(roomId);
        if (room == null) throw new NotFoundException("Room not found");
        if (room.isGroup()) throw new IllegalStateException("Cannot hide group chats");
        if (!roomDao.isMember(roomId, userId)) throw new IllegalStateException("Not a member");
        roomDao.setRoomHidden(roomId, userId, true);
    }

    /** Create a new group chat. */
    public ChatDto.RoomResponse createGroup(int creatorId, String name, List<Integer> memberIds) {
        if (name == null || name.isBlank()) throw new ValidationException(List.of("Group name is required"));

        User creator = userDao.find(creatorId);
        if (creator == null) throw new NotFoundException("User not found");

        Set<Integer> allIds = new LinkedHashSet<>(memberIds);
        allIds.add(creatorId);

        List<User> members = allIds.stream()
            .map(userDao::find)
            .filter(Objects::nonNull)
            .toList();

        ChatRoom room = new ChatRoom(name.trim(), true, creator);
        roomDao.createWithMembers(room, members);
        LOG.info("Group '{}' created by {} with {} members", name, creator.getUsername(), members.size());

        return getRoomsForUser(creatorId).stream()
            .filter(r -> r.id() == room.getId())
            .findFirst().orElse(null);
    }

    // ─── Messages ───

    /**
     * Get messages in a room.
     * Optimized: uses JOIN FETCH for messages and batch-fetches reactions.
     */
    public List<ChatDto.MessageResponse> getMessages(int roomId, int userId, Integer afterId) {
        if (!roomDao.isMember(roomId, userId)) {
            throw new IllegalStateException("Not a member of this room");
        }

        // JOIN FETCH: gets messages with sender + attachments + replyTo in ONE query
        List<ChatMessage> messages = messageDao.findByRoom(roomId, afterId);

        if (messages.isEmpty()) return List.of();

        // Batch-fetch ALL reactions for these messages in ONE query (eliminates N+1)
        List<Integer> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<Integer, List<ChatReaction>> allReactions = reactionDao.findByMessages(messageIds);

        return messages.stream()
            .map(m -> toMessageResponse(m, userId, allReactions.getOrDefault(m.getId(), List.of())))
            .toList();
    }

    /** Send a message to a room. */
    public ChatDto.MessageResponse sendMessage(int roomId, int userId, ChatDto.SendMessageRequest req) {
        if (req.content() == null || req.content().isBlank()) {
            throw new ValidationException(List.of("Message content is required"));
        }
        if (!roomDao.isMember(roomId, userId)) {
            throw new IllegalStateException("Not a member of this room");
        }

        User sender = userDao.find(userId);
        ChatRoom room = roomDao.find(roomId);

        ChatMessage msg = new ChatMessage(room, sender, req.content().trim());

        // Handle reply
        if (req.replyToId() != null && req.replyToId() > 0) {
            ChatMessage replyTo = messageDao.find(req.replyToId());
            if (replyTo != null) msg.setReplyTo(replyTo);
        }

        messageDao.save(msg);

        // Attach files if provided
        if (req.attachmentIds() != null) {
            for (int attId : req.attachmentIds()) {
                ChatAttachment att = attachmentDao.find(attId);
                if (att != null) {
                    att.setMessage(msg);
                    attachmentDao.update(att);
                }
            }
        }

        // Auto-unhide: when a message is sent in a direct chat,
        // make sure all members can see it in their sidebar again
        if (!room.isGroup()) {
            for (ChatMember member : roomDao.getMembers(roomId)) {
                if (member.isHidden()) {
                    roomDao.setRoomHidden(roomId, member.getUser().getId(), false);
                }
            }
        }

        // New messages have no reactions yet
        return toMessageResponse(msg, userId, List.of());
    }

    /** Edit a message (only the sender can edit). */
    public void editMessage(int messageId, int userId, String newContent) {
        ChatMessage msg = messageDao.find(messageId);
        if (msg == null) throw new NotFoundException("Message not found");
        if (msg.getSender().getId() != userId) throw new IllegalStateException("Can only edit your own messages");
        if (msg.isDeleted()) throw new IllegalStateException("Cannot edit a deleted message");
        if (newContent == null || newContent.isBlank()) throw new ValidationException(List.of("Content is required"));
        messageDao.editContent(messageId, newContent.trim());
    }

    /** Soft-delete a message (only the sender can delete). */
    public void deleteMessage(int messageId, int userId) {
        ChatMessage msg = messageDao.find(messageId);
        if (msg == null) throw new NotFoundException("Message not found");
        if (msg.getSender().getId() != userId) throw new IllegalStateException("Can only delete your own messages");
        messageDao.softDelete(messageId);
    }

    /** Toggle an emoji reaction on a message. Returns true if added, false if removed. */
    public boolean toggleReaction(int messageId, int userId, String emoji) {
        ChatMessage msg = messageDao.find(messageId);
        if (msg == null) throw new NotFoundException("Message not found");
        return reactionDao.toggle(messageId, userId, emoji);
    }

    /** Mark all messages in a room as read for a user. */
    public void markRead(int roomId, int userId) {
        messageDao.markRead(roomId, userId);
    }

    /** Get total unread message count across all rooms (for sidebar badge). */
    public long getTotalUnreadCount(int userId) {
        List<ChatRoom> rooms = roomDao.findRoomsForUser(userId);
        if (rooms.isEmpty()) return 0;

        List<Integer> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Integer, LocalDateTime> lastReadMap = new HashMap<>();

        for (ChatRoom room : rooms) {
            List<ChatMember> members = roomDao.getMembers(room.getId());
            LocalDateTime lastReadAt = members.stream()
                .filter(m -> m.getUser().getId() == userId)
                .map(ChatMember::getLastReadAt)
                .findFirst().orElse(null);
            lastReadMap.put(room.getId(), lastReadAt);
        }

        Map<Integer, Long> counts = messageDao.countUnreadForRooms(roomIds, lastReadMap);
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    // ─── Contacts ───

    /** Get contactable users (reuses same role-based rules from old MessageService). */
    public List<ChatDto.ContactUser> getContactableUsers(int userId, String role, int institutionId) {
        Set<User> contacts = new LinkedHashSet<>();
        User currentUser = userDao.find(userId);
        if (currentUser == null) return List.of();

        switch (role.toUpperCase()) {
            case "SUPER_ADMIN" -> {
                userDao.findAll().stream()
                    .filter(u -> "INSTITUTION_ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
            }
            case "INSTITUTION_ADMIN" -> {
                userDao.findAll().stream()
                    .filter(u -> "SUPER_ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
                userDao.findAll(institutionId).stream()
                    .filter(u -> "TEACHER".equalsIgnoreCase(u.getRole()) || "STUDENT".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
                userDao.findAll().stream()
                    .filter(u -> "INSTITUTION_ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
            }
            case "TEACHER" -> {
                userDao.findAll(institutionId).stream()
                    .filter(u -> "INSTITUTION_ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
                contacts.addAll(saDao.studentsForTeacher(currentUser.getUsername(), institutionId));
            }
            case "STUDENT" -> {
                userDao.findAll(institutionId).stream()
                    .filter(u -> "INSTITUTION_ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(contacts::add);
                List<String> subjects = saDao.subjectsForStudent(currentUser.getUsername(), institutionId);
                for (String subCode : subjects) {
                    contacts.addAll(saDao.teachersForSubject(subCode, institutionId));
                }
            }
        }

        contacts.removeIf(u -> u.getId() == userId);

        // Remove students who have been transferred to another institution
        contacts.removeIf(u -> {
            if ("STUDENT".equalsIgnoreCase(u.getRole()) || "TEACHER".equalsIgnoreCase(u.getRole())) {
                int userInstId = u.getInstitution() != null ? u.getInstitution().getId() : -1;
                return userInstId != institutionId && userInstId != -1;
            }
            return false;
        });

        // Remove graduated students from contacts
        Set<Integer> graduatedUserIds = new HashSet<>();
        List<ProgramMember> graduatedMembers = memberDao.findGraduatedByInstitution(institutionId);
        for (ProgramMember pm : graduatedMembers) {
            if ("STUDENT".equalsIgnoreCase(pm.getRole())) {
                graduatedUserIds.add(pm.getUser().getId());
            }
        }
        // Only remove if ALL their memberships are graduated (not if they have active ones too)
        contacts.removeIf(u -> {
            if (!"STUDENT".equalsIgnoreCase(u.getRole())) return false;
            if (!graduatedUserIds.contains(u.getId())) return false;
            List<ProgramMember> memberships = memberDao.findByUser(u.getId());
            return memberships.stream().allMatch(ProgramMember::isGraduated);
        });

        // Get online statuses
        Map<Integer, Boolean> onlineMap = getOnlineMap(
            contacts.stream().map(User::getId).toList());

        return contacts.stream()
            .map(u -> new ChatDto.ContactUser(
                u.getId(), u.getUsername(), fullName(u), u.getRole(),
                u.getInstitution() != null ? u.getInstitution().getName() : "—",
                onlineMap.getOrDefault(u.getId(), false)
            ))
            .toList();
    }

    /** Members management for groups. */
    public void addMember(int roomId, int userId, int newMemberId) {
        ChatRoom room = roomDao.find(roomId);
        if (room == null || !room.isGroup()) throw new IllegalStateException("Not a group room");
        if (!roomDao.isMember(roomId, userId)) throw new IllegalStateException("Not a member");
        User newUser = userDao.find(newMemberId);
        if (newUser == null) throw new NotFoundException("User not found");
        roomDao.addMember(room, newUser);
    }

    public void removeMember(int roomId, int userId, int targetId) {
        ChatRoom room = roomDao.find(roomId);
        if (room == null || !room.isGroup()) throw new IllegalStateException("Not a group room");
        if (!roomDao.isMember(roomId, userId)) throw new IllegalStateException("Not a member");
        // Only creator can remove others; anyone can remove themselves (leave)
        boolean isCreator = room.getCreatedBy() != null && room.getCreatedBy().getId() == userId;
        if (userId != targetId && !isCreator) throw new IllegalStateException("Only the group owner can remove members");
        roomDao.removeMember(roomId, targetId);
    }

    /** Delete a group chat entirely. Only the creator can do this. */
    public void deleteGroup(int roomId, int userId) {
        ChatRoom room = roomDao.find(roomId);
        if (room == null || !room.isGroup()) throw new IllegalStateException("Not a group room");
        boolean isCreator = room.getCreatedBy() != null && room.getCreatedBy().getId() == userId;
        if (!isCreator) throw new IllegalStateException("Only the group owner can delete the group");
        roomDao.deleteRoom(roomId);
        LOG.info("Group {} deleted by user {}", roomId, userId);
    }

    // ─── Helpers ───

    private boolean canContact(User sender, User receiver) {
        String sRole = sender.getRole().toUpperCase();
        String rRole = receiver.getRole().toUpperCase();
        int sInstId = sender.getInstitution() != null ? sender.getInstitution().getId() : -1;
        int rInstId = receiver.getInstitution() != null ? receiver.getInstitution().getId() : -1;
        return switch (sRole) {
            case "SUPER_ADMIN" -> "INSTITUTION_ADMIN".equals(rRole);
            case "INSTITUTION_ADMIN" ->
                "SUPER_ADMIN".equals(rRole) || "INSTITUTION_ADMIN".equals(rRole)
                || (("TEACHER".equals(rRole) || "STUDENT".equals(rRole)) && sInstId == rInstId);
            case "TEACHER" ->
                ("INSTITUTION_ADMIN".equals(rRole) && sInstId == rInstId)
                || ("STUDENT".equals(rRole) && sInstId == rInstId);
            case "STUDENT" ->
                ("INSTITUTION_ADMIN".equals(rRole) && sInstId == rInstId)
                || ("TEACHER".equals(rRole) && sInstId == rInstId);
            default -> false;
        };
    }

    /**
     * Convert a ChatMessage to a MessageResponse DTO.
     * Takes pre-fetched reactions to avoid N+1 queries.
     */
    private ChatDto.MessageResponse toMessageResponse(ChatMessage m, int currentUserId, List<ChatReaction> reactions) {
        Map<String, List<ChatReaction>> grouped = reactions.stream()
            .collect(Collectors.groupingBy(ChatReaction::getEmoji));

        List<ChatDto.ReactionGroup> reactionGroups = grouped.entrySet().stream()
            .map(e -> new ChatDto.ReactionGroup(
                e.getKey(),
                e.getValue().size(),
                e.getValue().stream().map(r -> r.getUser().getUsername()).toList(),
                e.getValue().stream().anyMatch(r -> r.getUser().getId() == currentUserId)
            ))
            .toList();

        ChatDto.ReplyPreview replyPreview = null;
        if (m.getReplyTo() != null) {
            ChatMessage rp = m.getReplyTo();
            replyPreview = new ChatDto.ReplyPreview(
                rp.getId(), fullName(rp.getSender()),
                rp.isDeleted() ? "Message deleted" : truncate(rp.getContent(), 50)
            );
        }

        List<ChatDto.AttachmentResponse> atts = m.getAttachments().stream()
            .map(a -> new ChatDto.AttachmentResponse(
                a.getId(), a.getFileName(), a.getFileSize(), a.getMimeType(),
                a.isImage(), "/api/files/" + a.getId()
            ))
            .toList();

        return new ChatDto.MessageResponse(
            m.getId(), m.getChatRoom().getId(),
            m.getSender().getId(), m.getSender().getUsername(),
            fullName(m.getSender()), m.getSender().getRole(),
            m.isDeleted() ? "" : m.getContent(),
            m.getSentAt(), m.getEditedAt(), m.isDeleted(),
            replyPreview, atts, reactionGroups
        );
    }

    private ChatDto.MemberInfo toMemberInfo(User u, Map<Integer, Boolean> onlineMap) {
        return new ChatDto.MemberInfo(
            u.getId(), u.getUsername(), fullName(u), u.getRole(),
            u.getInstitution() != null ? u.getInstitution().getName() : "—",
            onlineMap.getOrDefault(u.getId(), false)
        );
    }

    private Map<Integer, Boolean> getOnlineMap(List<Integer> userIds) {
        Map<Integer, Boolean> map = new HashMap<>();
        if (userIds.isEmpty()) return map;
        List<UserStatus> statuses = statusDao.getStatusForUsers(userIds);
        for (UserStatus s : statuses) {
            map.put(s.getUserId(), s.isOnline());
        }
        return map;
    }

    private String fullName(User u) {
        String fn = u.getFirstName() != null ? u.getFirstName() : "";
        String ln = u.getLastName() != null ? u.getLastName() : "";
        String full = (fn + " " + ln).trim();
        return full.isEmpty() ? u.getUsername() : full;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
