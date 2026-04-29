package no.example.verdan.api;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.websocket.*;

import no.example.verdan.security.JwtUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time chat events.
 *
 * Authentication: client sends JWT token as query parameter:
 *   ws://host/ws/chat?token=xxx
 *
 * Events sent TO clients:
 *   new_message, message_edited, message_deleted, reaction_toggled,
 *   typing, user_status, unread_update
 *
 * Events received FROM clients:
 *   typing (roomId, isTyping)
 */
public class ChatWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(ChatWebSocket.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // userId → set of active WebSocket connections (supports multiple tabs)
    private static final Map<Integer, Set<WsContext>> USER_SESSIONS = new ConcurrentHashMap<>();

    // WsContext → userId (reverse lookup for onClose)
    private static final Map<WsContext, Integer> CONTEXT_TO_USER = new ConcurrentHashMap<>();

    // roomId → set of member userIds (cached for broadcasting)
    private static final Map<Integer, Set<Integer>> ROOM_MEMBERS = new ConcurrentHashMap<>();

    // ─── Singleton instance for broadcasting from controllers ───
    private static ChatWebSocket instance;

    public static ChatWebSocket getInstance() {
        if (instance == null) instance = new ChatWebSocket();
        return instance;
    }

    private ChatWebSocket() {}

    /**
     * Configure the WebSocket handler with Javalin's WsConfig.
     */
    public void configure(WsConfig ws) {
        ws.onConnect(this::onConnect);
        ws.onMessage(this::onMessage);
        ws.onClose(this::onClose);
        ws.onError(this::onError);
    }

    // ─── WebSocket Lifecycle ───

    private void onConnect(WsConnectContext ctx) {
        // Set idle timeout to 5 minutes (default is ~30s)
        ctx.session.setIdleTimeout(Duration.ofMinutes(5));

        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            LOG.warn("WS connect without token, closing");
            ctx.session.close(4001, "Missing token");
            return;
        }

        DecodedJWT jwt = JwtUtil.verifyToken(token);
        if (jwt == null || JwtUtil.isRefreshToken(jwt)) {
            LOG.warn("WS connect with invalid token, closing");
            ctx.session.close(4002, "Invalid token");
            return;
        }

        int userId = JwtUtil.getUserId(jwt);
        String username = JwtUtil.getUsername(jwt);

        // Register connection
        USER_SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(ctx);
        CONTEXT_TO_USER.put(ctx, userId);

        LOG.info("WS connected: {} (userId={}), total connections: {}", username, userId, CONTEXT_TO_USER.size());

        // Broadcast online status to all users who share rooms with this user
        broadcastUserStatus(userId, true);
    }

    private void onMessage(WsMessageContext ctx) {
        Integer userId = CONTEXT_TO_USER.get(ctx);
        if (userId == null) return;

        try {
            Map<String, Object> msg = MAPPER.readValue(ctx.message(), Map.class);
            String type = (String) msg.get("type");

            if ("ping".equals(type)) {
                // Respond with pong to keep connection alive
                safeSend(ctx, "{\"type\":\"pong\"}");
                return;
            }

            if ("typing".equals(type)) {
                int roomId = ((Number) msg.get("roomId")).intValue();
                boolean isTyping = (boolean) msg.get("isTyping");

                // Broadcast typing event to other members in the room
                Map<String, Object> event = Map.of(
                    "type", "typing",
                    "roomId", roomId,
                    "userId", userId,
                    "isTyping", isTyping
                );
                broadcastToRoom(roomId, event, userId); // exclude sender
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse WS message from userId={}: {}", userId, e.getMessage());
        }
    }

    private void onClose(WsCloseContext ctx) {
        Integer userId = CONTEXT_TO_USER.remove(ctx);
        if (userId != null) {
            Set<WsContext> sessions = USER_SESSIONS.get(userId);
            if (sessions != null) {
                sessions.remove(ctx);
                if (sessions.isEmpty()) {
                    USER_SESSIONS.remove(userId);
                    // Only broadcast offline when last connection is closed
                    broadcastUserStatus(userId, false);
                    LOG.info("WS user fully offline: userId={}", userId);
                }
            }
        }
    }

    private void onError(WsErrorContext ctx) {
        LOG.warn("WS error: {}", ctx.error() != null ? ctx.error().getMessage() : "unknown");
    }

    // ─── Broadcasting Methods (called from ChatApiController) ───

    /** Broadcast a new message to all members of a room, excluding the sender. */
    public void broadcastNewMessage(int roomId, Object messageResponse, int senderId) {
        Map<String, Object> event = Map.of(
            "type", "new_message",
            "roomId", roomId,
            "message", messageResponse
        );
        broadcastToRoom(roomId, event, senderId);
    }

    /** Broadcast that a message was edited. */
    public void broadcastMessageEdited(int roomId, int messageId, String newContent, Object editedAt) {
        Map<String, Object> event = Map.of(
            "type", "message_edited",
            "roomId", roomId,
            "messageId", messageId,
            "content", newContent,
            "editedAt", editedAt != null ? editedAt.toString() : ""
        );
        broadcastToRoom(roomId, event, null);
    }

    /** Broadcast that a message was deleted. */
    public void broadcastMessageDeleted(int roomId, int messageId) {
        Map<String, Object> event = Map.of(
            "type", "message_deleted",
            "roomId", roomId,
            "messageId", messageId
        );
        broadcastToRoom(roomId, event, null);
    }

    /**
     * Broadcast reaction update with full data so clients can update locally.
     * Includes emoji, userId, username, and whether it was added or removed.
     */
    public void broadcastReactionToggled(int roomId, int messageId, String emoji, int userId, String username, boolean added) {
        Map<String, Object> event = Map.of(
            "type", "reaction_toggled",
            "roomId", roomId,
            "messageId", messageId,
            "emoji", emoji,
            "userId", userId,
            "username", username,
            "added", added
        );
        broadcastToRoom(roomId, event, userId); // exclude sender (they have optimistic update)
    }

    /** Send unread count update to a specific user. */
    public void sendUnreadUpdate(int userId, long totalUnread) {
        sendToUser(userId, Map.of(
            "type", "unread_update",
            "totalUnread", totalUnread
        ));
    }

    // ─── Room membership cache ───

    /** Register room members (call when rooms are loaded or members change). */
    public void registerRoomMembers(int roomId, Collection<Integer> memberIds) {
        ROOM_MEMBERS.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).addAll(memberIds);
    }

    /** Check if a user is currently online. */
    public boolean isUserOnline(int userId) {
        Set<WsContext> sessions = USER_SESSIONS.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** Get all online user IDs. */
    public Set<Integer> getOnlineUserIds() {
        return Collections.unmodifiableSet(USER_SESSIONS.keySet());
    }

    // ─── Internal helpers ───

    private void broadcastToRoom(int roomId, Map<String, Object> event, Integer excludeUserId) {
        Set<Integer> members = ROOM_MEMBERS.get(roomId);
        if (members == null) return;

        String json = toJson(event);
        if (json == null) return;

        for (int memberId : members) {
            if (excludeUserId != null && memberId == excludeUserId) continue;
            sendJsonToUser(memberId, json);
        }
    }

    /** Broadcast to ALL members of a room (no exclusions). */
    public void broadcastToRoomAll(int roomId, Map<String, Object> event) {
        broadcastToRoom(roomId, event, null);
    }

    private void broadcastUserStatus(int userId, boolean online) {
        Map<String, Object> event = Map.of(
            "type", "user_status",
            "userId", userId,
            "online", online
        );
        String json = toJson(event);
        if (json == null) return;

        // Send to all connected users (they'll filter relevance on frontend)
        for (Map.Entry<Integer, Set<WsContext>> entry : USER_SESSIONS.entrySet()) {
            if (entry.getKey() == userId) continue; // don't send to self
            for (WsContext ctx : entry.getValue()) {
                safeSend(ctx, json);
            }
        }
    }

    private void sendToUser(int userId, Map<String, Object> event) {
        String json = toJson(event);
        if (json != null) sendJsonToUser(userId, json);
    }

    private void sendJsonToUser(int userId, String json) {
        Set<WsContext> sessions = USER_SESSIONS.get(userId);
        if (sessions == null) return;
        for (WsContext ctx : sessions) {
            safeSend(ctx, json);
        }
    }

    private void safeSend(WsContext ctx, String json) {
        try {
            if (ctx.session.isOpen()) {
                ctx.send(json);
            }
        } catch (Exception e) {
            LOG.warn("Failed to send WS message: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            LOG.error("Failed to serialize WS event: {}", e.getMessage());
            return null;
        }
    }
}
