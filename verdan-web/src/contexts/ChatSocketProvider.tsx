import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useCallback,
  useState,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getToken } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import type { ChatMessage } from '../types';

export interface WsEvent {
  type: string;
  roomId?: number;
  message?: ChatMessage;
  messageId?: number;
  content?: string;
  editedAt?: string;
  userId?: number;
  isTyping?: boolean;
  online?: boolean;
  totalUnread?: number;
  emoji?: string;
  username?: string;
  added?: boolean;
}

interface ChatSocketContextType {
  isConnected: boolean;
  sendTyping: (roomId: number, isTyping: boolean) => void;
  getTypingUsersForRoom: (roomId: number) => number[];
  onlineUsers: Set<number>;
}

const ChatSocketContext = createContext<ChatSocketContextType>({
  isConnected: false,
  sendTyping: () => {},
  getTypingUsersForRoom: () => [],
  onlineUsers: new Set(),
});

export const useChatSocket = () => useContext(ChatSocketContext);

/**
 * Provider that maintains a persistent WebSocket connection
 * for all authenticated pages. Handles real-time events globally.
 */
export function ChatSocketProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const pingRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined);
  const reconnectDelay = useRef(1000);
  const [isConnected, setIsConnected] = useState(false);
  const [typingUsers, setTypingUsers] = useState<
    Map<string, { userId: number; timeout: ReturnType<typeof setTimeout> }>
  >(new Map());
  const [onlineUsers, setOnlineUsers] = useState<Set<number>>(new Set());

  const handleEvent = useCallback(
    (event: WsEvent) => {
      switch (event.type) {
        case 'new_message':
          if (event.roomId && event.message) {
            queryClient.setQueryData<ChatMessage[]>(['chatMessages', event.roomId], (old) => {
              if (!old) return [event.message!];
              // Deduplicate: skip if message already in cache (from REST response)
              if (old.some((m) => m.id === event.message!.id)) return old;
              return [...old, event.message!];
            });
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
            queryClient.invalidateQueries({ queryKey: ['unreadCount'] });
          }
          break;

        case 'message_edited':
          if (event.roomId && event.messageId) {
            queryClient.setQueryData<ChatMessage[]>(
              ['chatMessages', event.roomId],
              (old) =>
                old?.map((m) =>
                  m.id === event.messageId
                    ? { ...m, content: event.content || '', editedAt: event.editedAt || null }
                    : m,
                ) || [],
            );
          }
          break;

        case 'message_deleted':
          if (event.roomId && event.messageId) {
            queryClient.setQueryData<ChatMessage[]>(
              ['chatMessages', event.roomId],
              (old) =>
                old?.map((m) =>
                  m.id === event.messageId ? { ...m, deleted: true, content: '' } : m,
                ) || [],
            );
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
          }
          break;

        case 'reaction_toggled':
          if (event.roomId && event.messageId && event.emoji != null && event.userId != null) {
            // Smart update: modify the specific message's reactions locally (no refetch)
            queryClient.setQueryData<ChatMessage[]>(['chatMessages', event.roomId], (old) => {
              if (!old) return old;
              return old.map((msg) => {
                if (msg.id !== event.messageId) return msg;

                const reactions = [...(msg.reactions || [])];
                const existingIdx = reactions.findIndex((r) => r.emoji === event.emoji);

                if (event.added) {
                  if (existingIdx >= 0) {
                    const existing = reactions[existingIdx];
                    reactions[existingIdx] = {
                      ...existing,
                      count: existing.count + 1,
                      usernames: [...existing.usernames, event.username || ''],
                    };
                  } else {
                    reactions.push({
                      emoji: event.emoji!,
                      count: 1,
                      usernames: [event.username || ''],
                      currentUserReacted: false,
                    });
                  }
                } else {
                  // Removed
                  if (existingIdx >= 0) {
                    const existing = reactions[existingIdx];
                    if (existing.count <= 1) {
                      reactions.splice(existingIdx, 1);
                    } else {
                      reactions[existingIdx] = {
                        ...existing,
                        count: existing.count - 1,
                        usernames: existing.usernames.filter((u) => u !== event.username),
                      };
                    }
                  }
                }

                return { ...msg, reactions };
              });
            });
          }
          break;

        case 'typing':
          if (event.roomId && event.userId) {
            const key = `${event.roomId}-${event.userId}`;
            setTypingUsers((prev) => {
              const next = new Map(prev);
              if (event.isTyping) {
                const existing = next.get(key);
                if (existing) clearTimeout(existing.timeout);
                const timeout = setTimeout(() => {
                  setTypingUsers((p) => {
                    const n = new Map(p);
                    n.delete(key);
                    return n;
                  });
                }, 3000);
                next.set(key, { userId: event.userId!, timeout });
              } else {
                const existing = next.get(key);
                if (existing) clearTimeout(existing.timeout);
                next.delete(key);
              }
              return next;
            });
          }
          break;

        case 'user_status':
          if (event.userId != null) {
            setOnlineUsers((prev) => {
              const next = new Set(prev);
              if (event.online) next.add(event.userId!);
              else next.delete(event.userId!);
              return next;
            });
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
          }
          break;

        case 'unread_update':
          if (event.totalUnread != null) {
            queryClient.setQueryData(['unreadCount'], event.totalUnread);
          }
          break;

        // pong responses are silently ignored (they just reset the idle timer)
        case 'pong':
          break;
      }
    },
    [queryClient],
  );

  const connect = useCallback(() => {
    const token = getToken();
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/chat?token=${token}`;

    const socket = new WebSocket(wsUrl);
    wsRef.current = socket;

    socket.onopen = () => {
      setIsConnected(true);
      reconnectDelay.current = 1000;

      // Start keepalive ping every 25 seconds to prevent idle timeout
      if (pingRef.current) clearInterval(pingRef.current);
      pingRef.current = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }));
        }
      }, 25000);
    };

    socket.onmessage = (event) => {
      try {
        const data: WsEvent = JSON.parse(event.data);
        handleEvent(data);
      } catch (e) {
        console.warn('Failed to parse WS event:', e);
      }
    };

    socket.onclose = (event) => {
      setIsConnected(false);
      wsRef.current = null;
      if (pingRef.current) {
        clearInterval(pingRef.current);
        pingRef.current = undefined;
      }

      if (event.code === 4001 || event.code === 4002) return;

      const delay = Math.min(reconnectDelay.current, 30000);
      reconnectRef.current = setTimeout(() => {
        reconnectDelay.current *= 2;
        connect();
      }, delay);
    };

    socket.onerror = () => {
      // Errors are handled by onclose reconnect logic
    };
  }, [handleEvent]);

  // Connect when user is authenticated, disconnect when not
  useEffect(() => {
    if (!user) return;
    connect();
    return () => {
      if (reconnectRef.current) clearTimeout(reconnectRef.current);
      if (pingRef.current) clearInterval(pingRef.current);
      if (wsRef.current) {
        wsRef.current.close(1000, 'Provider unmount');
        wsRef.current = null;
      }
    };
  }, [user, connect]);

  const sendTyping = useCallback((roomId: number, isTyping: boolean) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: 'typing', roomId, isTyping }));
    }
  }, []);

  const getTypingUsersForRoom = useCallback(
    (roomId: number): number[] => {
      const result: number[] = [];
      typingUsers.forEach((val, key) => {
        if (key.startsWith(`${roomId}-`)) result.push(val.userId);
      });
      return result;
    },
    [typingUsers],
  );

  return (
    <ChatSocketContext.Provider
      value={{ isConnected, sendTyping, getTypingUsersForRoom, onlineUsers }}
    >
      {children}
    </ChatSocketContext.Provider>
  );
}
