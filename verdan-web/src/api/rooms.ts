import api from './client';
import type { ApiResponse, Room, RoomRequest } from '../types';

export const getRooms = async () => {
  const { data } = await api.get<ApiResponse<Room[]>>('/rooms');
  return data.data;
};

export const getRoom = async (id: number) => {
  const { data } = await api.get<ApiResponse<Room>>(`/rooms/${id}`);
  return data.data;
};

export const createRoom = async (room: RoomRequest) => {
  const { data } = await api.post<ApiResponse<Room>>('/rooms', room);
  return data.data;
};

export const updateRoom = async (id: number, room: RoomRequest) => {
  const { data } = await api.put<ApiResponse<Room>>(`/rooms/${id}`, room);
  return data.data;
};

export const deleteRoom = async (id: number) => {
  await api.delete(`/rooms/${id}`);
};
