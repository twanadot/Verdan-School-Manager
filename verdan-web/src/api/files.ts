import api from './client';
import { getToken } from './client';
import type { ApiResponse, UploadResponse } from '../types';

export const uploadFile = async (file: File): Promise<UploadResponse> => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ApiResponse<UploadResponse>>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
};

/** Get authenticated file URL (appends JWT token as query param for direct browser access). */
export const getFileUrl = (attachmentId: number) => {
  const token = getToken();
  return `/api/files/${attachmentId}${token ? `?token=${token}` : ''}`;
};
