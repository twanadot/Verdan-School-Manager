import api from './client';
import type { ApiResponse, ChatRoom, ChatMessage, ContactUser, UnreadCountResponse, SendChatMessageRequest } from '../types';

// ─── Rooms ───

export const getChatRooms = async () => {
  const { data } = await api.get<ApiResponse<ChatRoom[]>>('/chat/rooms');
  return data.data;
};

export const getOrCreateDirectChat = async (otherUserId: number) => {
  const { data } = await api.post<ApiResponse<ChatRoom>>('/chat/rooms/direct', { otherUserId });
  return data.data;
};

export const createGroupChat = async (name: string, memberIds: number[]) => {
  const { data } = await api.post<ApiResponse<ChatRoom>>('/chat/rooms/group', { name, memberIds });
  return data.data;
};

// ─── Messages ───

export const getChatMessages = async (roomId: number, afterId?: number) => {
  const params = afterId ? { after: afterId } : {};
  const { data } = await api.get<ApiResponse<ChatMessage[]>>(`/chat/rooms/${roomId}/messages`, { params });
  return data.data;
};

export const sendChatMessage = async (roomId: number, msg: SendChatMessageRequest) => {
  const { data } = await api.post<ApiResponse<ChatMessage>>(`/chat/rooms/${roomId}/messages`, msg);
  return data.data;
};

export const editChatMessage = async (messageId: number, content: string) => {
  await api.put(`/chat/messages/${messageId}`, { content });
};

export const deleteChatMessage = async (messageId: number) => {
  await api.delete(`/chat/messages/${messageId}`);
};

// ─── Reactions ───

export const toggleReaction = async (messageId: number, emoji: string) => {
  await api.post(`/chat/messages/${messageId}/reactions`, { emoji });
};

// ─── Read & Unread ───

export const markRoomRead = async (roomId: number) => {
  await api.put(`/chat/rooms/${roomId}/read`);
};

export const getUnreadCount = async (): Promise<number> => {
  try {
    const { data } = await api.get<ApiResponse<UnreadCountResponse>>('/chat/unread-count');
    return data?.data?.count ?? 0;
  } catch {
    return 0;
  }
};

// ─── Contacts ───

export const getChatContacts = async () => {
  const { data } = await api.get<ApiResponse<ContactUser[]>>('/chat/contacts');
  return data.data;
};

// ─── Members ───

export const addChatMember = async (roomId: number, userId: number) => {
  await api.post(`/chat/rooms/${roomId}/members`, { userId });
};

export const removeChatMember = async (roomId: number, userId: number) => {
  await api.delete(`/chat/rooms/${roomId}/members/${userId}`);
};

export const deleteGroupChat = async (roomId: number) => {
  await api.delete(`/chat/rooms/${roomId}`);
};

export const hideChatRoom = async (roomId: number) => {
  await api.put(`/chat/rooms/${roomId}/hide`);
};
