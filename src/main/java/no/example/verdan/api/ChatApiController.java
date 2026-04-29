package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import no.example.verdan.dto.ApiResponse;
import no.example.verdan.dto.ChatDto;
import no.example.verdan.service.ChatService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API controller for the chat system.
 * After mutations, broadcasts real-time events via WebSocket.
 */
public class ChatApiController {

    private final ChatService chatService = new ChatService();
    private final ChatWebSocket ws = ChatWebSocket.getInstance();

    public void registerRoutes(Javalin app) {
        // Rooms
        app.get("/api/chat/rooms", this::getRooms);
        app.post("/api/chat/rooms/direct", this::getOrCreateDirect);
        app.post("/api/chat/rooms/group", this::createGroup);
        app.get("/api/chat/rooms/{id}/members", this::getMembers);
        app.post("/api/chat/rooms/{id}/members", this::addMember);
        app.delete("/api/chat/rooms/{id}/members/{userId}", this::removeMember);
        app.delete("/api/chat/rooms/{id}", this::deleteGroup);
        app.put("/api/chat/rooms/{id}/hide", this::hideRoom);

        // Messages
        app.get("/api/chat/rooms/{id}/messages", this::getMessages);
        app.post("/api/chat/rooms/{id}/messages", this::sendMessage);
        app.put("/api/chat/messages/{id}", this::editMessage);
        app.delete("/api/chat/messages/{id}", this::deleteMessage);

        // Reactions
        app.post("/api/chat/messages/{id}/reactions", this::toggleReaction);

        // Read status & unread count
        app.put("/api/chat/rooms/{id}/read", this::markRead);
        app.get("/api/chat/unread-count", this::getUnreadCount);

        // Contacts
        app.get("/api/chat/contacts", this::getContacts);
    }

    // ─── Room endpoints ───

    private void getRooms(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        List<ChatDto.RoomResponse> rooms = chatService.getRoomsForUser(userId);

        // Register room members in WebSocket for broadcasting
        for (ChatDto.RoomResponse room : rooms) {
            List<Integer> memberIds = room.members().stream()
                .map(ChatDto.MemberInfo::userId).toList();
            ws.registerRoomMembers(room.id(), memberIds);
        }

        // Enrich online status from WebSocket
        List<ChatDto.RoomResponse> enriched = rooms.stream().map(r -> new ChatDto.RoomResponse(
            r.id(), r.name(), r.isGroup(), r.createdById(),
            r.members().stream().map(m -> new ChatDto.MemberInfo(
                m.userId(), m.username(), m.fullName(), m.role(), m.institutionName(),
                ws.isUserOnline(m.userId())
            )).toList(),
            r.lastMessage(), r.unreadCount(), r.createdAt()
        )).toList();

        ctx.json(ApiResponse.ok(enriched));
    }

    private void getOrCreateDirect(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        ChatDto.DirectChatRequest req = ctx.bodyAsClass(ChatDto.DirectChatRequest.class);
        ChatDto.RoomResponse room = chatService.getOrCreateDirectChat(userId, req.otherUserId());

        // Register members for WS broadcasting
        ws.registerRoomMembers(room.id(), room.members().stream().map(ChatDto.MemberInfo::userId).toList());

        ctx.json(ApiResponse.ok(room));
    }

    private void createGroup(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        ChatDto.CreateGroupRequest req = ctx.bodyAsClass(ChatDto.CreateGroupRequest.class);
        ChatDto.RoomResponse room = chatService.createGroup(userId, req.name(), req.memberIds());

        // Register members for WS broadcasting
        ws.registerRoomMembers(room.id(), room.members().stream().map(ChatDto.MemberInfo::userId).toList());

        ctx.status(201).json(ApiResponse.ok(room));
    }

    private void getMembers(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        ctx.json(ApiResponse.ok(chatService.getRoomsForUser(userId).stream()
            .filter(r -> r.id() == roomId).findFirst().map(ChatDto.RoomResponse::members).orElse(java.util.List.of())));
    }

    private void addMember(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        record AddReq(int userId) {}
        AddReq req = ctx.bodyAsClass(AddReq.class);
        chatService.addMember(roomId, userId, req.userId());
        ctx.json(ApiResponse.ok());
    }

    private void removeMember(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        int targetId = Integer.parseInt(ctx.pathParam("userId"));
        chatService.removeMember(roomId, userId, targetId);
        ctx.json(ApiResponse.ok());
    }

    private void deleteGroup(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        chatService.deleteGroup(roomId, userId);
        // Notify all members via WebSocket
        ws.broadcastToRoomAll(roomId, java.util.Map.of(
            "type", "group_deleted",
            "roomId", roomId
        ));
        ctx.json(ApiResponse.ok());
    }

    private void hideRoom(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        chatService.hideRoom(roomId, userId);
        ctx.json(ApiResponse.ok());
    }

    // ─── Message endpoints ───

    private void getMessages(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        String afterParam = ctx.queryParam("after");
        Integer afterId = afterParam != null ? Integer.parseInt(afterParam) : null;
        ctx.json(ApiResponse.ok(chatService.getMessages(roomId, userId, afterId)));
    }

    private void sendMessage(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        ChatDto.SendMessageRequest req = ctx.bodyAsClass(ChatDto.SendMessageRequest.class);
        ChatDto.MessageResponse msg = chatService.sendMessage(roomId, userId, req);

        // Mark room as read for the sender (so they don't get unread badge for own messages)
        chatService.markRead(roomId, userId);

        // Broadcast via WebSocket to OTHER members only (sender already has the message)
        ws.broadcastNewMessage(roomId, msg, userId);

        ctx.status(201).json(ApiResponse.ok(msg));
    }

    private void editMessage(Context ctx) {
        int messageId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        ChatDto.EditMessageRequest req = ctx.bodyAsClass(ChatDto.EditMessageRequest.class);

        // Get roomId before editing (for WS broadcast)
        var msgDao = new no.example.verdan.dao.ChatMessageDao();
        var chatMsg = msgDao.find(messageId);
        int roomId = chatMsg != null ? chatMsg.getChatRoom().getId() : -1;

        chatService.editMessage(messageId, userId, req.content());

        // Broadcast via WebSocket
        if (roomId > 0) {
            ws.broadcastMessageEdited(roomId, messageId, req.content(), LocalDateTime.now());
        }

        ctx.json(ApiResponse.ok());
    }

    private void deleteMessage(Context ctx) {
        int messageId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);

        // Get roomId before deleting
        try {
            var msgDao = new no.example.verdan.dao.ChatMessageDao();
            var chatMsg = msgDao.find(messageId);
            int roomId = chatMsg != null ? chatMsg.getChatRoom().getId() : -1;

            chatService.deleteMessage(messageId, userId);

            if (roomId > 0) {
                ws.broadcastMessageDeleted(roomId, messageId);
            }
        } catch (Exception e) {
            chatService.deleteMessage(messageId, userId);
        }

        ctx.json(ApiResponse.ok());
    }

    // ─── Reactions ───

    private void toggleReaction(Context ctx) {
        int messageId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        String username = AuthMiddleware.getUsername(ctx);
        ChatDto.ReactionRequest req = ctx.bodyAsClass(ChatDto.ReactionRequest.class);

        // toggleReaction now returns true=added, false=removed
        boolean added = chatService.toggleReaction(messageId, userId, req.emoji());

        // Broadcast with full data so clients update locally without refetch
        try {
            var msgDao = new no.example.verdan.dao.ChatMessageDao();
            var chatMsg = msgDao.find(messageId);
            if (chatMsg != null) {
                ws.broadcastReactionToggled(
                    chatMsg.getChatRoom().getId(), messageId,
                    req.emoji(), userId, username, added
                );
            }
        } catch (Exception ignored) {}

        ctx.json(ApiResponse.ok());
    }

    // ─── Read & Unread ───

    private void markRead(Context ctx) {
        int roomId = Integer.parseInt(ctx.pathParam("id"));
        int userId = AuthMiddleware.getUserId(ctx);
        chatService.markRead(roomId, userId);
        ctx.json(ApiResponse.ok());
    }

    private void getUnreadCount(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        long count = chatService.getTotalUnreadCount(userId);
        ctx.json(ApiResponse.ok(new UnreadCount(count)));
    }

    private void getContacts(Context ctx) {
        int userId = AuthMiddleware.getUserId(ctx);
        String role = AuthMiddleware.getRole(ctx);
        int institutionId = AuthMiddleware.getInstitutionId(ctx);
        List<ChatDto.ContactUser> contacts = chatService.getContactableUsers(userId, role, institutionId);

        // Enrich with WS online status
        List<ChatDto.ContactUser> enriched = contacts.stream().map(c -> new ChatDto.ContactUser(
            c.id(), c.username(), c.fullName(), c.role(), c.institutionName(),
            ws.isUserOnline(c.id())
        )).toList();

        ctx.json(ApiResponse.ok(enriched));
    }

    public record UnreadCount(long count) {}
}
