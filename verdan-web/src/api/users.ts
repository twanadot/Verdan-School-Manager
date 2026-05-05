import api from './client';
import type { ApiResponse, User, CreateUserRequest } from '../types';

export const getUsers = async (role?: string) => {
  const params = role ? { role } : {};
  const { data } = await api.get<ApiResponse<User[]>>('/users', { params });
  return data.data;
};

export const getUser = async (id: number) => {
  const { data } = await api.get<ApiResponse<User>>(`/users/${id}`);
  return data.data;
};

export const createUser = async (user: CreateUserRequest) => {
  const { data } = await api.post<ApiResponse<User>>('/users', user);
  return data.data;
};

export const updateUser = async (id: number, user: Partial<CreateUserRequest>) => {
  const { data } = await api.put<ApiResponse<User>>(`/users/${id}`, user);
  return data.data;
};

export const deleteUser = async (id: number) => {
  await api.delete(`/users/${id}`);
};

// ── Batch Import ──

export interface CreatedStudent {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  birthDate: string;
  className: string;
}

export interface ImportResult {
  created: number;
  skipped: number;
  total: number;
  errors: string[];
  createdStudents: CreatedStudent[];
}

export const importStudents = async (file: File): Promise<ImportResult> => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ApiResponse<ImportResult>>('/users/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
};

export const batchDeleteUsers = async (ids: number[]): Promise<{ deleted: number }> => {
  const { data } = await api.post<ApiResponse<{ deleted: number }>>('/users/batch-delete', { ids });
  return data.data;
};

// ── Batch Transfer (VGS) ──

export interface TransferredStudent {
  username: string;
  fullName: string;
  program: string;
  yearLevel: string;
}

export interface TransferResult {
  transferred: number;
  skipped: number;
  total: number;
  errors: string[];
  transferredStudents: TransferredStudent[];
}

export const transferStudentsBatch = async (file: File): Promise<TransferResult> => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ApiResponse<TransferResult>>('/users/transfer', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
};
