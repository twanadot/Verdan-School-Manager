import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getChatRooms, getChatMessages, sendChatMessage, editChatMessage,
  deleteChatMessage, toggleReaction, markRoomRead, getChatContacts,
  getOrCreateDirectChat, createGroupChat, addChatMember, removeChatMember, deleteGroupChat, hideChatRoom
} from '../api/chat';
import { uploadFile, getFileUrl } from '../api/files';
import { useChatSocket } from '../contexts/ChatSocketProvider';
import { toast } from 'sonner';
import {
  MessageSquare, Send, Plus, X, Search, Users, Paperclip, Smile,
  MoreHorizontal, Edit3, Trash2, Reply, Check, ChevronLeft, Download,
  Image as ImageIcon, FileText, UserPlus, Wifi, WifiOff
} from 'lucide-react';
import type { ChatRoom, ChatMessage, ContactUser, SendChatMessageRequest } from '../types';

// ─── Quick Emoji Set ───
const EMOJIS = ['👍', '❤️', '😂', '😮', '😢', '🔥', '👏', '🎉'];

/** Ensure date string is treated as UTC (backend sends LocalDateTime without timezone). */
function toUtc(dateStr: string): string {
  if (!dateStr) return dateStr;
  // If it doesn't end with Z or +/- offset, append Z to treat as UTC
  if (!/[Z+\-]/.test(dateStr.slice(-6))) return dateStr + 'Z';
  return dateStr;
}

/** Generate a consistent avatar color from a name. */
function nameToColor(name: string): string {
  const colors = [
    'bg-blue-600', 'bg-purple-600', 'bg-emerald-600', 'bg-rose-600',
    'bg-amber-600', 'bg-cyan-600', 'bg-pink-600', 'bg-indigo-600',
    'bg-teal-600', 'bg-orange-600', 'bg-violet-600', 'bg-lime-600',
  ];
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
  return colors[Math.abs(hash) % colors.length];
}

/** Get initials from a name (max 2 chars). */
function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  return name.slice(0, 2).toUpperCase();
}

export function ChatPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [activeRoomId, setActiveRoomId] = useState<number | null>(null);
  const [showNewChat, setShowNewChat] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');
  const [showMobileChat, setShowMobileChat] = useState(false);

  // WebSocket connection (from global context — always active)
  const { isConnected, sendTyping, getTypingUsersForRoom, onlineUsers } = useChatSocket();

  // Rooms query — initial load only, then updated via WebSocket
  const { data: rooms = [] } = useQuery({
    queryKey: ['chatRooms'],
    queryFn: getChatRooms,
    staleTime: 10000,
  });

  const activeRoom = rooms.find(r => r.id === activeRoomId);

  const filteredRooms = rooms.filter(r =>
    r.name?.toLowerCase().includes(searchFilter.toLowerCase()) ||
    r.members?.some(m => m.fullName.toLowerCase().includes(searchFilter.toLowerCase()))
  );

  const handleSelectRoom = (roomId: number) => {
    setActiveRoomId(roomId);
    setShowMobileChat(true);
    markRoomRead(roomId).then(() => {
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      queryClient.invalidateQueries({ queryKey: ['unreadCount'] });
    });
  };

  if (!user) return null;

  return (
    <div className="flex h-[calc(100vh-48px)] -m-6 bg-bg-primary">
      {/* Left panel: Room list */}
      <div className={`w-80 border-r border-border flex flex-col bg-bg-secondary shrink-0 ${showMobileChat ? 'hidden md:flex' : 'flex'}`}>
        {/* Header */}
        <div className="p-4 border-b border-border">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-text-primary">Chat</h2>
              {isConnected ? (
                <span title="Connected"><Wifi size={12} className="text-green-500" /></span>
              ) : (
                <span title="Reconnecting..."><WifiOff size={12} className="text-red-400 animate-pulse" /></span>
              )}
            </div>
            <button
              onClick={() => setShowNewChat(true)}
              className="p-2 rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors"
              title="Ny samtale"
            >
              <Plus size={16} />
            </button>
          </div>
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
            <input
              value={searchFilter}
              onChange={e => setSearchFilter(e.target.value)}
              placeholder="Søk i samtaler..."
              className="w-full pl-9 pr-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
            />
          </div>
        </div>

        {/* Room list */}
        <div className="flex-1 overflow-y-auto">
          {filteredRooms.length === 0 ? (
            <div className="p-6 text-center text-text-muted text-sm">
              <MessageSquare size={32} className="mx-auto mb-2 opacity-40" />
              Ingen samtaler ennå
            </div>
          ) : (
            filteredRooms.map(room => (
              <RoomItem
                key={room.id}
                room={room}
                isActive={room.id === activeRoomId}
                currentUserId={user.id}
                onlineUsers={onlineUsers}
                onClick={() => handleSelectRoom(room.id)}
                onHide={!room.isGroup ? async () => {
                  try {
                    await hideChatRoom(room.id);
                    if (activeRoomId === room.id) setActiveRoomId(null);
                    queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
                  } catch { toast.error('Kunne ikke skjule samtale'); }
                } : undefined}
              />
            ))
          )}
        </div>
      </div>

      {/* Right panel: Conversation */}
      <div className={`flex-1 flex flex-col ${!showMobileChat ? 'hidden md:flex' : 'flex'}`}>
        {activeRoom ? (
          <ConversationView
            room={activeRoom}
            currentUserId={user.id}
            onBack={() => setShowMobileChat(false)}
            sendTyping={sendTyping}
            typingUserIds={getTypingUsersForRoom(activeRoom.id)}
            onlineUsers={onlineUsers}
          />
        ) : (
          <div className="flex-1 flex items-center justify-center text-text-muted">
            <div className="text-center">
              <MessageSquare size={48} className="mx-auto mb-3 opacity-30" />
              <p className="text-lg font-medium">Velg en samtale</p>
              <p className="text-sm mt-1">eller start en ny chat</p>
            </div>
          </div>
        )}
      </div>

      {/* New Chat Modal */}
      {showNewChat && (
        <NewChatModal
          onClose={() => setShowNewChat(false)}
          onCreated={(roomId) => {
            setShowNewChat(false);
            handleSelectRoom(roomId);
          }}
        />
      )}
    </div>
  );
}

// ─── Room Item ───
function RoomItem({ room, isActive, currentUserId, onlineUsers, onClick, onHide }: {
  room: ChatRoom; isActive: boolean; currentUserId: number; onlineUsers: Set<number>; onClick: () => void; onHide?: () => void;
}) {
  const otherMember = !room.isGroup
    ? room.members.find(m => m.userId !== currentUserId)
    : null;

  const isOtherOnline = otherMember ? onlineUsers.has(otherMember.userId) || otherMember.online : false;

  const formatTime = (dateStr: string) => {
    const d = new Date(toUtc(dateStr));
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    const days = Math.floor(diff / 86400000);
    if (days === 0) return d.toLocaleTimeString('no-NO', { hour: '2-digit', minute: '2-digit' });
    if (days === 1) return 'i går';
    if (days < 7) return d.toLocaleDateString('no-NO', { weekday: 'short' });
    return d.toLocaleDateString('no-NO', { day: 'numeric', month: 'short' });
  };

  return (
    <div className="relative group">
      <button
        onClick={onClick}
        className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-colors border-b border-border/30
          ${isActive ? 'bg-accent/10 border-l-2 border-l-accent' : 'hover:bg-bg-hover/50'}`}
      >
        <div className="relative shrink-0">
          <div className={`w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold text-white
            ${room.isGroup ? 'bg-purple-500/80' : 'bg-accent/80'}`}>
            {room.isGroup ? <Users size={18} /> : (room.name?.charAt(0) || '?').toUpperCase()}
          </div>
          {!room.isGroup && isOtherOnline && (
            <div className="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 bg-green-500 rounded-full border-2 border-bg-secondary" />
          )}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <span className={`text-sm truncate ${room.unreadCount > 0 ? 'font-bold text-text-primary' : 'font-medium text-text-primary'}`}>
              {room.name}
            </span>
            {room.lastMessage && (
              <span className="text-[10px] text-text-muted shrink-0 ml-2">{formatTime(room.lastMessage.sentAt)}</span>
            )}
          </div>
          <div className="flex items-center justify-between mt-0.5">
            <p className={`text-xs truncate ${room.unreadCount > 0 ? 'text-text-primary font-medium' : 'text-text-muted'}`}>
              {room.lastMessage
                ? (() => {
                    const prefix = room.lastMessage.senderId === currentUserId
                      ? 'Deg: '
                      : room.isGroup ? `${room.lastMessage.senderName.split(' ')[0]}: ` : '';
                    const text = room.lastMessage.deleted ? 'Melding slettet' : room.lastMessage.content;
                    return prefix + text;
                  })()
                : 'Ingen meldinger ennå'}
            </p>
            {room.unreadCount > 0 && (
              <span className="ml-2 min-w-[18px] h-[18px] px-1 flex items-center justify-center text-[10px] font-bold text-white bg-accent rounded-full shrink-0">
                {room.unreadCount > 99 ? '99+' : room.unreadCount}
              </span>
            )}
          </div>
        </div>
      </button>

      {/* Hide button for direct chats only */}
      {!room.isGroup && onHide && (
        <button
          onClick={(e) => { e.stopPropagation(); onHide(); }}
          className="absolute top-1.5 right-1.5 p-1 rounded-full bg-bg-secondary/80 text-text-muted hover:text-danger hover:bg-bg-hover opacity-0 group-hover:opacity-100 transition-all z-10"
          title="Skjul samtale"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}

// ─── Conversation View ───
function ConversationView({ room, currentUserId, onBack, sendTyping, typingUserIds, onlineUsers }: {
  room: ChatRoom; currentUserId: number; onBack: () => void;
  sendTyping: (roomId: number, isTyping: boolean) => void;
  typingUserIds: number[];
  onlineUsers: Set<number>;
}) {
  const queryClient = useQueryClient();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null);
  const [editingMsg, setEditingMsg] = useState<ChatMessage | null>(null);
  const [editContent, setEditContent] = useState('');
  const prevMsgCount = useRef(0);

  // Messages — initial load, then updated via WebSocket
  const { data: messages = [] } = useQuery({
    queryKey: ['chatMessages', room.id],
    queryFn: () => getChatMessages(room.id),
    staleTime: 30000,
  });

  const isInitialLoad = useRef(true);

  // Scroll to bottom instantly when opening a chat room
  useEffect(() => {
    isInitialLoad.current = true;
    prevMsgCount.current = 0;
  }, [room.id]);

  // Auto scroll when messages load or new messages arrive
  useEffect(() => {
    if (messages.length > 0) {
      if (isInitialLoad.current) {
        // First load: scroll instantly to bottom
        messagesEndRef.current?.scrollIntoView({ behavior: 'instant' });
        isInitialLoad.current = false;
      } else if (messages.length > prevMsgCount.current) {
        // New message: smooth scroll
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
      }
      prevMsgCount.current = messages.length;
    }
  }, [messages.length]);

  const otherMember = !room.isGroup
    ? room.members.find(m => m.userId !== currentUserId)
    : null;

  const isOtherOnline = otherMember ? onlineUsers.has(otherMember.userId) || otherMember.online : false;
  const onlineCount = room.members.filter(m => onlineUsers.has(m.userId) || m.online).length;

  // Typing users display
  const typingNames = typingUserIds
    .map(id => room.members.find(m => m.userId === id)?.fullName?.split(' ')[0])
    .filter(Boolean);

  // Edit mutation
  const editMut = useMutation({
    mutationFn: () => editChatMessage(editingMsg!.id, editContent),
    onSuccess: () => {
      setEditingMsg(null);
      setEditContent('');
      queryClient.invalidateQueries({ queryKey: ['chatMessages', room.id] });
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      toast.success('Message edited');
    },
  });

  // Delete mutation
  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteChatMessage(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatMessages', room.id] });
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      toast.success('Melding slettet');
    },
  });

  // Reaction mutation with optimistic update
  const reactMut = useMutation({
    mutationFn: ({ msgId, emoji }: { msgId: number; emoji: string }) => toggleReaction(msgId, emoji),
    onMutate: async ({ msgId, emoji }) => {
      // Cancel outgoing refetches
      await queryClient.cancelQueries({ queryKey: ['chatMessages', room.id] });

      // Snapshot previous value
      const previousMessages = queryClient.getQueryData<ChatMessage[]>(['chatMessages', room.id]);

      // Optimistically update the cache
      queryClient.setQueryData<ChatMessage[]>(['chatMessages', room.id], (old) => {
        if (!old) return old;
        return old.map(msg => {
          if (msg.id !== msgId) return msg;

          const reactions = [...(msg.reactions || [])];
          const existingIdx = reactions.findIndex(r => r.emoji === emoji);

          if (existingIdx >= 0) {
            const existing = reactions[existingIdx];
            if (existing.currentUserReacted) {
              // Remove MY reaction only (toggle off)
              if (existing.count <= 1) {
                reactions.splice(existingIdx, 1);
              } else {
                reactions[existingIdx] = {
                  ...existing,
                  count: existing.count - 1,
                  currentUserReacted: false,
                };
              }
            } else {
              // Add my reaction (someone else already reacted with this emoji)
              reactions[existingIdx] = {
                ...existing,
                count: existing.count + 1,
                currentUserReacted: true,
              };
            }
          } else {
            // New emoji reaction
            reactions.push({ emoji, count: 1, usernames: [], currentUserReacted: true });
          }

          return { ...msg, reactions };
        });
      });

      return { previousMessages };
    },
    onError: (_err, _vars, context) => {
      // Roll back to previous state on error
      if (context?.previousMessages) {
        queryClient.setQueryData(['chatMessages', room.id], context.previousMessages);
      }
    },
  });

  const [showMembers, setShowMembers] = useState(false);

  return (
    <>
      {/* Header */}
      <div className="px-4 py-3 border-b border-border flex items-center gap-3 bg-bg-secondary">
        <button onClick={onBack} className="md:hidden p-1 text-text-muted"><ChevronLeft size={20} /></button>
        <div className="relative">
          <div className={`w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold text-white
            ${room.isGroup ? 'bg-purple-500/80' : 'bg-accent/80'}`}>
            {room.isGroup ? <Users size={16} /> : (room.name?.charAt(0) || '?').toUpperCase()}
          </div>
          {!room.isGroup && isOtherOnline && (
            <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-green-500 rounded-full border-2 border-bg-secondary" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-semibold text-text-primary truncate">{room.name}</h3>
          <p className="text-[10px] text-text-muted">
            {room.isGroup
              ? `${room.members.length} medlemmer · ${onlineCount} pålogget`
              : isOtherOnline ? '🟢 Pålogget' : '⚫ Frakoblet'}
          </p>
        </div>
        {/* Members panel toggle */}
        <button
          onClick={() => setShowMembers(!showMembers)}
          className={`p-2 rounded-lg transition-colors ${showMembers ? 'bg-accent/20 text-accent' : 'text-text-muted hover:bg-bg-hover'}`}
          title="Members"
        >
          <Users size={18} />
        </button>
      </div>

      {/* Main content area with optional members panel */}
      <div className="flex flex-1 overflow-hidden">
        {/* Messages column */}
        <div className="flex-1 flex flex-col min-w-0">

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-1">
        {messages.map((msg, i) => {
          const isMine = msg.senderId === currentUserId;
          const showAvatar = i === 0 || messages[i - 1].senderId !== msg.senderId;

          return (
            <MessageBubble
              key={msg.id}
              msg={msg}
              isMine={isMine}
              showAvatar={showAvatar}
              isGroup={room.isGroup}
              onReply={() => setReplyTo(msg)}
              onEdit={() => { setEditingMsg(msg); setEditContent(msg.content); }}
              onDelete={() => deleteMut.mutate(msg.id)}
              onReact={(emoji) => reactMut.mutate({ msgId: msg.id, emoji })}
            />
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Typing indicator */}
      {typingNames.length > 0 && (
        <div className="px-4 py-1.5 text-xs text-text-muted animate-pulse">
          ✏️ {typingNames.join(', ')} skriver...
        </div>
      )}

      {/* Edit bar */}
      {editingMsg && (
        <div className="px-4 py-2 bg-amber-500/10 border-t border-amber-500/20 flex items-center gap-2">
          <Edit3 size={14} className="text-amber-400" />
          <span className="text-xs text-amber-400 shrink-0">Redigerer</span>
          <input
            value={editContent}
            onChange={e => setEditContent(e.target.value)}
            className="flex-1 px-2 py-1 bg-bg-input border border-border rounded text-sm text-text-primary"
            onKeyDown={e => e.key === 'Enter' && editMut.mutate()}
          />
          <button onClick={() => editMut.mutate()} className="p-1 text-green-400 hover:text-green-300"><Check size={16} /></button>
          <button onClick={() => setEditingMsg(null)} className="p-1 text-text-muted hover:text-text-primary"><X size={16} /></button>
        </div>
      )}

      {/* Reply bar */}
      {replyTo && !editingMsg && (
        <div className="px-4 py-2 bg-accent/5 border-t border-accent/20 flex items-center gap-2">
          <Reply size={14} className="text-accent" />
          <span className="text-xs text-accent flex-1 truncate">
            Svarer på {replyTo.senderName}: {replyTo.deleted ? 'Melding slettet' : replyTo.content}
          </span>
          <button onClick={() => setReplyTo(null)} className="p-1 text-text-muted hover:text-text-primary"><X size={16} /></button>
        </div>
      )}

      {/* Input bar */}
      {!editingMsg && (
        <ChatInput
          roomId={room.id}
          replyToId={replyTo?.id}
          onSent={() => {
            setReplyTo(null);
            queryClient.invalidateQueries({ queryKey: ['chatMessages', room.id] });
            queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
          }}
          sendTyping={sendTyping}
        />
      )}
        </div>

        {/* Members panel — right sidebar */}
        {showMembers && (
          <div className="w-64 border-l border-border bg-bg-secondary overflow-y-auto shrink-0">
            <div className="p-4">
              <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-3">
                Members ({room.members.length})
              </h4>

              {/* Add member button (owner only, groups only) */}
              {room.isGroup && room.createdById === currentUserId && (
                <AddMemberButton roomId={room.id} existingMemberIds={room.members.map(m => m.userId)} onAdded={() => {
                  queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
                }} />
              )}

              <div className="space-y-1">
                {[...room.members]
                  .sort((a, b) => {
                    // Owner first, then online, then alphabetical
                    if (a.userId === room.createdById) return -1;
                    if (b.userId === room.createdById) return 1;
                    const aOnline = onlineUsers.has(a.userId) || a.online;
                    const bOnline = onlineUsers.has(b.userId) || b.online;
                    if (aOnline && !bOnline) return -1;
                    if (!aOnline && bOnline) return 1;
                    return a.fullName.localeCompare(b.fullName);
                  })
                  .map(member => {
                    const isOnline = onlineUsers.has(member.userId) || member.online;
                    const color = nameToColor(member.fullName);
                    const initials = getInitials(member.fullName);
                    const isOwner = member.userId === room.createdById;
                    const canRemove = room.isGroup && room.createdById === currentUserId && !isOwner;
                    return (
                      <div key={member.userId} className="flex items-center gap-2.5 p-2 rounded-lg hover:bg-bg-hover/50 transition-colors group">
                        <div className="relative shrink-0">
                          <div className={`w-8 h-8 rounded-full ${color} flex items-center justify-center text-white text-[11px] font-semibold`}>
                            {initials}
                          </div>
                          <div className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-bg-secondary ${isOnline ? 'bg-green-500' : 'bg-gray-500'}`} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-medium text-text-primary truncate flex items-center gap-1">
                            {member.fullName}
                            {isOwner && <span className="text-[9px] bg-accent/20 text-accent px-1.5 py-0.5 rounded-full font-semibold">Eier</span>}
                          </p>
                          <p className="text-[10px] text-text-muted truncate">
                            {member.role.replace('_', ' ')} · {member.institutionName}
                          </p>
                        </div>
                        {canRemove && (
                          <button
                            onClick={async () => {
                              if (!confirm(`Fjerne ${member.fullName} fra gruppen?`)) return;
                              try {
                                await removeChatMember(room.id, member.userId);
                                queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
                                toast.success(`${member.fullName} removed`);
                              } catch { toast.error('Kunne ikke fjerne medlem'); }
                            }}
                            className="p-1 text-text-muted hover:text-danger opacity-0 group-hover:opacity-100 transition-opacity"
                            title="Fjern medlem"
                          >
                            <X size={14} />
                          </button>
                        )}
                      </div>
                    );
                  })}
              </div>

              {/* Delete group button (owner only) */}
              {room.isGroup && room.createdById === currentUserId && (
                <button
                  onClick={async () => {
                    if (!confirm(`Slette gruppen "${room.name}"? Dette fjerner alle meldinger og medlemmer permanent.`)) return;
                    try {
                      await deleteGroupChat(room.id);
                      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
                      onBack();
                      toast.success('Gruppe slettet');
                    } catch { toast.error('Kunne ikke slette gruppe'); }
                  }}
                  className="w-full mt-4 flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-danger/10 border border-danger/20 text-danger text-sm font-medium hover:bg-danger/20 transition-colors"
                >
                  <Trash2 size={14} />
                  Delete Group
                </button>
              )}

              {/* Leave group button (non-owner members) */}
              {room.isGroup && room.createdById !== currentUserId && (
                <button
                  onClick={async () => {
                    if (!confirm(`Forlate gruppen "${room.name}"?`)) return;
                    try {
                      await removeChatMember(room.id, currentUserId);
                      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
                      onBack();
                      toast.success('Du forlot gruppen');
                    } catch { toast.error('Kunne ikke forlate gruppe'); }
                  }}
                  className="w-full mt-4 flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-bg-hover border border-border text-text-muted text-sm font-medium hover:text-danger hover:border-danger/30 transition-colors"
                >
                  <X size={14} />
                  Leave Group
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  );
}

// ─── Add Member Button ───
function AddMemberButton({ roomId, existingMemberIds, onAdded }: {
  roomId: number; existingMemberIds: number[]; onAdded: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const { data: contacts = [] } = useQuery({
    queryKey: ['chatContacts'],
    queryFn: getChatContacts,
    enabled: open,
  });

  const available = contacts.filter(c =>
    !existingMemberIds.includes(c.id) &&
    c.fullName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <button
        onClick={() => setOpen(!open)}
        className="w-full mb-3 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-dashed border-accent/30 text-accent text-xs font-medium hover:bg-accent/5 transition-colors"
      >
        <UserPlus size={14} />
        Add Member
      </button>

      {open && (
        <div className="mb-3 bg-bg-card border border-border rounded-lg p-2">
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Søk i kontakter..."
            className="w-full px-2 py-1.5 text-xs bg-bg-primary border border-border rounded-md text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-1 focus:ring-accent mb-2"
            autoFocus
          />
          <div className="max-h-40 overflow-y-auto space-y-0.5">
            {available.length === 0 && (
              <p className="text-[10px] text-text-muted text-center py-2">Ingen kontakter tilgjengelig</p>
            )}
            {available.map(c => (
              <button
                key={c.id}
                onClick={async () => {
                  try {
                    await addChatMember(roomId, c.id);
                    onAdded();
                    toast.success(`${c.fullName} lagt til i gruppen`);
                    setSearch('');
                  } catch { toast.error('Kunne ikke legge til medlem'); }
                }}
                className="w-full flex items-center gap-2 p-1.5 rounded-md hover:bg-bg-hover transition-colors text-left"
              >
                <div className={`w-6 h-6 rounded-full ${nameToColor(c.fullName)} flex items-center justify-center text-white text-[9px] font-semibold shrink-0`}>
                  {getInitials(c.fullName)}
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-medium text-text-primary truncate">{c.fullName}</p>
                  <p className="text-[9px] text-text-muted truncate">{c.role.replace('_', ' ')}</p>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </>
  );
}

// ─── Message Bubble ───

function MessageBubble({ msg, isMine, showAvatar, isGroup, onReply, onEdit, onDelete, onReact }: {
  msg: ChatMessage; isMine: boolean; showAvatar: boolean; isGroup: boolean;
  onReply: () => void; onEdit: () => void; onDelete: () => void; onReact: (emoji: string) => void;
}) {
  const [showActions, setShowActions] = useState(false);
  const [showEmojis, setShowEmojis] = useState(false);

  if (msg.deleted) {
    return (
      <div className={`flex ${isMine ? 'justify-end' : 'justify-start'} ${showAvatar ? 'mt-3' : 'mt-0.5'}`}>
        {/* Avatar spacer for deleted messages */}
        {!isMine && <div className="w-8 shrink-0" />}
        <div className="px-3 py-2 rounded-xl bg-bg-hover/30 border border-border/20 italic text-xs text-text-muted">
          🚫 Melding slettet
        </div>
      </div>
    );
  }

  const formatTime = (d: string) => new Date(toUtc(d)).toLocaleTimeString('no-NO', { hour: '2-digit', minute: '2-digit' });
  const avatarColor = nameToColor(msg.senderName);
  const initials = getInitials(msg.senderName);

  return (
    <div className={`flex ${isMine ? 'justify-end' : 'justify-start'} ${showAvatar ? 'mt-3' : 'mt-0.5'}`}>
      {/* Avatar for received messages */}
      {!isMine && (
        showAvatar ? (
          <div className={`w-8 h-8 rounded-full ${avatarColor} flex items-center justify-center text-white text-[11px] font-semibold shrink-0 mt-auto mb-1 mr-2`}
               title={msg.senderName}>
            {initials}
          </div>
        ) : (
          <div className="w-8 shrink-0 mr-2" /> /* spacer to align with avatar above */
        )
      )}

      <div className={`max-w-[75%] ${isMine ? 'items-end' : 'items-start'} flex flex-col`}>
        {/* Sender name */}
        {showAvatar && !isMine && (
          <span className="text-[10px] text-text-muted mb-0.5 ml-1">{msg.senderName}</span>
        )}

        {/* Reply preview */}
        {msg.replyTo && (
          <div className={`text-[10px] px-2 py-1 rounded-t-lg border-l-2 border-accent bg-accent/5 text-text-muted mb-0.5 ${isMine ? 'ml-auto' : ''}`}>
            <span className="font-medium text-accent">{msg.replyTo.senderName}</span>: {msg.replyTo.content}
          </div>
        )}

        {/* Message row: toolbar appears beside the bubble */}
        <div
          className="relative flex items-center gap-1"
          onMouseEnter={() => setShowActions(true)}
          onMouseLeave={() => { setShowActions(false); setShowEmojis(false); }}
        >
          {/* Toolbar — LEFT side for own messages */}
          {isMine && showActions && (
            <div className="relative shrink-0">
              <div className="flex items-center gap-0.5 bg-bg-card border border-border rounded-lg shadow-lg p-0.5">
                <button onClick={onDelete} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Trash2 size={14} className="text-danger" /></button>
                <button onClick={onEdit} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Edit3 size={14} className="text-text-muted" /></button>
                <button onClick={onReply} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Reply size={14} className="text-text-muted" /></button>
                <button onClick={() => setShowEmojis(!showEmojis)} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Smile size={14} className="text-text-muted" /></button>
              </div>
              {showEmojis && (
                <div className="absolute bottom-full right-0 flex gap-1 bg-bg-card border border-border rounded-lg shadow-lg p-2 z-30">
                  {EMOJIS.map(e => (
                    <button key={e} onClick={() => { onReact(e); setShowEmojis(false); }} className="text-lg hover:scale-125 transition-transform p-0.5 hover:bg-bg-hover rounded">
                      {e}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Bubble + reactions column */}
          <div className="flex flex-col min-w-0">
            {/* Message bubble */}
            <div className={`px-3 py-2 rounded-2xl text-sm leading-relaxed break-words
              ${isMine
                ? 'bg-accent text-white rounded-br-md'
                : 'bg-bg-card border border-border/50 text-text-primary rounded-bl-md'
              }`}>
              {msg.content}

              {msg.attachments?.length > 0 && (
                <div className="mt-2 space-y-2">
                  {msg.attachments.map(att => {
                    const isImage = att.isImage || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(att.fileName);
                    if (isImage) {
                      return (
                        <a key={att.id} href={getFileUrl(att.id)} target="_blank" rel="noopener noreferrer" className="block">
                          <img
                            src={getFileUrl(att.id)}
                            alt={att.fileName}
                            className="max-w-[280px] max-h-[200px] rounded-lg object-cover cursor-pointer hover:opacity-90 transition-opacity"
                            loading="lazy"
                          />
                        </a>
                      );
                    }
                    return (
                      <a
                        key={att.id}
                        href={getFileUrl(att.id)}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-2 px-2 py-1.5 rounded-lg text-xs transition-colors
                          ${isMine ? 'bg-white/10 hover:bg-white/20' : 'bg-bg-hover hover:bg-bg-hover/80'}`}
                      >
                        <FileText size={14} />
                        <span className="truncate flex-1">{att.fileName}</span>
                        <Download size={12} />
                      </a>
                    );
                  })}
                </div>
              )}

              <div className={`flex items-center gap-1 mt-1 ${isMine ? 'justify-end' : 'justify-start'}`}>
                <span className={`text-[9px] ${isMine ? 'text-white/50' : 'text-text-muted'}`}>
                  {formatTime(msg.sentAt)}
                </span>
                {msg.editedAt && (
                  <span className={`text-[9px] ${isMine ? 'text-white/40' : 'text-text-muted/60'}`}>(edited)</span>
                )}
              </div>
            </div>

            {/* Reactions */}
            {msg.reactions?.length > 0 && (
              <div className={`flex flex-wrap gap-1 mt-1 ${isMine ? 'justify-end' : 'justify-start'}`}>
                {msg.reactions.map(r => (
                  <button
                    key={r.emoji}
                    onClick={() => onReact(r.emoji)}
                    className={`flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-xs border transition-colors
                      ${r.currentUserReacted
                        ? 'bg-accent/10 border-accent/30 text-accent'
                        : 'bg-bg-hover/50 border-border/30 text-text-muted hover:border-accent/20'}`}
                    title={r.usernames.join(', ')}
                  >
                    <span>{r.emoji}</span>
                    <span className="text-[10px]">{r.count}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Toolbar — RIGHT side for received messages */}
          {!isMine && showActions && (
            <div className="relative shrink-0">
              <div className="flex items-center gap-0.5 bg-bg-card border border-border rounded-lg shadow-lg p-0.5">
                <button onClick={() => setShowEmojis(!showEmojis)} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Smile size={14} className="text-text-muted" /></button>
                <button onClick={onReply} className="p-1.5 hover:bg-bg-hover rounded transition-colors"><Reply size={14} className="text-text-muted" /></button>
              </div>
              {showEmojis && (
                <div className="absolute bottom-full left-0 flex gap-1 bg-bg-card border border-border rounded-lg shadow-lg p-2 z-30">
                  {EMOJIS.map(e => (
                    <button key={e} onClick={() => { onReact(e); setShowEmojis(false); }} className="text-lg hover:scale-125 transition-transform p-0.5 hover:bg-bg-hover rounded">
                      {e}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Chat Input ───
function ChatInput({ roomId, replyToId, onSent, sendTyping }: {
  roomId: number; replyToId?: number; onSent: () => void;
  sendTyping: (roomId: number, isTyping: boolean) => void;
}) {
  const [text, setText] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [showEmojis, setShowEmojis] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const handleTyping = () => {
    sendTyping(roomId, true);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      sendTyping(roomId, false);
    }, 2000);
  };

  const queryClient = useQueryClient();

  const handleSend = async () => {
    if (!text.trim() && files.length === 0) return;

    // Stop typing indicator
    sendTyping(roomId, false);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);

    setUploading(true);
    try {
      const attachmentIds: number[] = [];
      for (const f of files) {
        const res = await uploadFile(f);
        attachmentIds.push(res.id);
      }

      const req: SendChatMessageRequest = {
        content: text.trim() || (files.length > 0 ? '📎 File' : ''),
        replyToId: replyToId || undefined,
        attachmentIds: attachmentIds.length > 0 ? attachmentIds : undefined,
      };

      // Send and immediately add to cache from response
      const sentMsg = await sendChatMessage(roomId, req);
      queryClient.setQueryData<ChatMessage[]>(
        ['chatMessages', roomId],
        (old) => {
          if (!old) return [sentMsg];
          if (old.some(m => m.id === sentMsg.id)) return old;
          return [...old, sentMsg];
        }
      );

      setText('');
      setFiles([]);
      onSent();
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Kunne ikke sende');
    } finally {
      setUploading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const addEmoji = (emoji: string) => {
    setText(prev => prev + emoji);
    setShowEmojis(false);
    inputRef.current?.focus();
  };

  return (
    <div className="px-4 py-3 border-t border-border bg-bg-secondary">
      {files.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-2">
          {files.map((f, i) => (
            <div key={i} className="flex items-center gap-1.5 px-2 py-1 bg-bg-hover rounded-lg text-xs text-text-secondary">
              {f.type.startsWith('image/') ? <ImageIcon size={12} /> : <FileText size={12} />}
              <span className="truncate max-w-[120px]">{f.name}</span>
              <button onClick={() => setFiles(prev => prev.filter((_, idx) => idx !== i))} className="text-text-muted hover:text-danger"><X size={12} /></button>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-end gap-2 relative">
        <button onClick={() => fileRef.current?.click()} className="p-2 text-text-muted hover:text-text-primary transition-colors" title="Attach file">
          <Paperclip size={18} />
        </button>
        <input
          ref={fileRef}
          type="file"
          multiple
          className="hidden"
          onChange={e => {
            if (e.target.files) {
              const BLOCKED = ['.exe', '.bat', '.cmd', '.msi', '.scr'];
              const newFiles: File[] = [];
              for (const file of Array.from(e.target.files)) {
                const ext = file.name.toLowerCase().slice(file.name.lastIndexOf('.'));
                if (BLOCKED.includes(ext)) {
                  toast.warning('Ugyldig filformat', {
                    description: `Filen "${file.name}" har ikke et gyldig format.`,
                  });
                } else {
                  newFiles.push(file);
                }
              }
              if (newFiles.length > 0) setFiles(prev => [...prev, ...newFiles]);
              e.target.value = '';
            }
          }}
        />

        <div className="relative">
          <button onClick={() => setShowEmojis(!showEmojis)} className="p-2 text-text-muted hover:text-text-primary transition-colors" title="Emoji">
            <Smile size={18} />
          </button>
          {showEmojis && (
            <div className="absolute bottom-full mb-2 left-0 flex gap-1 bg-bg-card border border-border rounded-lg shadow-lg p-2 z-20">
              {EMOJIS.map(e => (
                <button key={e} onClick={() => addEmoji(e)} className="text-xl hover:scale-125 transition-transform p-1">
                  {e}
                </button>
              ))}
            </div>
          )}
        </div>

        <textarea
          ref={inputRef}
          value={text}
          onChange={e => { setText(e.target.value); handleTyping(); }}
          onKeyDown={handleKeyDown}
          placeholder="Type a message..."
          rows={1}
          className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-xl text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus resize-none max-h-32"
        />

        <button
          onClick={handleSend}
          disabled={uploading || (!text.trim() && files.length === 0)}
          className="p-2 bg-accent hover:bg-accent-hover text-white rounded-xl transition-colors disabled:opacity-40"
        >
          <Send size={18} />
        </button>
      </div>
    </div>
  );
}

// ─── New Chat Modal ───
function NewChatModal({ onClose, onCreated }: { onClose: () => void; onCreated: (roomId: number) => void; }) {
  const [tab, setTab] = useState<'direct' | 'group'>('direct');
  const { data: contacts = [] } = useQuery({ queryKey: ['chatContacts'], queryFn: getChatContacts });
  const [search, setSearch] = useState('');
  const [groupName, setGroupName] = useState('');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);

  const filtered = contacts.filter(c =>
    c.fullName.toLowerCase().includes(search.toLowerCase()) ||
    c.username.toLowerCase().includes(search.toLowerCase())
  );

  const handleDirect = async (userId: number) => {
    setLoading(true);
    try {
      const room = await getOrCreateDirectChat(userId);
      onCreated(room.id);
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Failed');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGroup = async () => {
    if (!groupName.trim() || selectedIds.length === 0) return;
    setLoading(true);
    try {
      const room = await createGroupChat(groupName.trim(), selectedIds);
      onCreated(room.id);
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Failed');
    } finally {
      setLoading(false);
    }
  };

  const toggleSelect = (id: number) => {
    setSelectedIds(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  };

  const roleBadge = (role: string) => {
    const colors: Record<string, string> = { SUPER_ADMIN: 'bg-badge-admin', INSTITUTION_ADMIN: 'bg-badge-admin', TEACHER: 'bg-badge-teacher', STUDENT: 'bg-badge-student' };
    return <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-semibold text-white ${colors[role] || 'bg-bg-hover'}`}>{role.replace(/_/g, ' ')}</span>;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-5 w-full max-w-md shadow-2xl max-h-[80vh] flex flex-col">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold text-text-primary mb-3">Ny samtale</h2>

        <div className="flex gap-1 mb-3 bg-bg-primary p-1 rounded-lg">
          <button onClick={() => setTab('direct')} className={`flex-1 py-1.5 text-xs font-medium rounded-md transition-colors ${tab === 'direct' ? 'bg-accent text-white' : 'text-text-secondary'}`}>
            Direktemelding
          </button>
          <button onClick={() => setTab('group')} className={`flex-1 py-1.5 text-xs font-medium rounded-md transition-colors ${tab === 'group' ? 'bg-accent text-white' : 'text-text-secondary'}`}>
            Gruppechat
          </button>
        </div>

        {tab === 'group' && (
          <input
            value={groupName}
            onChange={e => setGroupName(e.target.value)}
            placeholder="Gruppenavn..."
            className="w-full px-3 py-2 mb-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
          />
        )}

        <div className="relative mb-2">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Søk i kontakter..." className="w-full pl-9 pr-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" />
        </div>

        {tab === 'group' && selectedIds.length > 0 && (
          <div className="flex flex-wrap gap-1 mb-2">
            {selectedIds.map(id => {
              const c = contacts.find(x => x.id === id);
              return c ? (
                <span key={id} className="flex items-center gap-1 px-2 py-0.5 bg-accent/10 text-accent text-xs rounded-full">
                  {c.fullName || c.username}
                  <button onClick={() => toggleSelect(id)}><X size={10} /></button>
                </span>
              ) : null;
            })}
          </div>
        )}

        <div className="flex-1 overflow-y-auto border border-border rounded-lg bg-bg-primary divide-y divide-border/30">
          {filtered.map(c => (
            <button
              key={c.id}
              onClick={() => tab === 'direct' ? handleDirect(c.id) : toggleSelect(c.id)}
              className={`w-full flex items-center gap-2 px-3 py-2.5 text-left hover:bg-bg-hover transition-colors
                ${tab === 'group' && selectedIds.includes(c.id) ? 'bg-accent/5' : ''}`}
            >
              <div className="relative">
                <div className="w-8 h-8 rounded-full bg-bg-hover flex items-center justify-center text-xs font-bold text-text-secondary">
                  {(c.fullName || c.username).charAt(0).toUpperCase()}
                </div>
                {c.online && <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-green-500 rounded-full border-2 border-bg-primary" />}
              </div>
              <div className="flex-1 min-w-0">
                <span className="text-sm font-medium text-text-primary truncate block">{c.fullName || c.username}</span>
                <span className="text-[10px] text-text-muted">{c.institutionName}</span>
              </div>
              {roleBadge(c.role)}
              {tab === 'group' && selectedIds.includes(c.id) && <Check size={14} className="text-accent" />}
            </button>
          ))}
        </div>

        {tab === 'group' && (
          <button
            onClick={handleCreateGroup}
            disabled={loading || !groupName.trim() || selectedIds.length === 0}
            className="mt-3 w-full flex items-center justify-center gap-2 py-2.5 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
          >
            <UserPlus size={16} />
            {loading ? 'Oppretter...' : `Opprett gruppe (${selectedIds.length} medlemmer)`}
          </button>
        )}
      </div>
    </div>
  );
}
