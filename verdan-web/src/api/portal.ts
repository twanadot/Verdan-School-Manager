import api, { getToken } from './client';
import type { ApiResponse } from '../types';

// ── Types ──

export interface PortalAnnouncement {
  id: number;
  title: string;
  content: string;
  authorId: number;
  authorName: string;
  programId: number | null;
  programName: string | null;
  subjectCode: string | null;
  subjectName: string | null;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
}

export interface PortalComment {
  id: number;
  announcementId: number;
  authorId: number;
  authorName: string;
  authorRole: string;
  content: string;
  createdAt: string;
}

export interface PortalFolder {
  id: number;
  name: string;
  subjectCode: string | null;
  subjectName: string | null;
  programId: number | null;
  programName: string | null;
  createdById: number;
  createdByName: string;
  assignment: boolean;
  description: string | null;
  deadline: string | null;
  createdAt: string;
  sortOrder: number;
  fileCount: number;
  submissionCount: number;
}

export interface PortalFile {
  id: number;
  folderId: number;
  fileName: string;
  mimeType: string;
  fileSize: number;
  uploadedById: number;
  uploadedByName: string;
  uploadedAt: string;
}

export interface PortalSubmission {
  id: number;
  folderId: number;
  folderName: string;
  studentId: number;
  studentName: string;
  studentUsername: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  feedback: string | null;
  submittedAt: string;
  reviewedAt: string | null;
}

// ── Announcements ──

export const getAnnouncements = async () => {
  const { data } = await api.get<ApiResponse<PortalAnnouncement[]>>('/portal/announcements');
  return data.data;
};

export const createAnnouncement = async (req: { title: string; content: string; programId?: number; subjectCode?: string }) => {
  const { data } = await api.post<ApiResponse<PortalAnnouncement>>('/portal/announcements', req);
  return data.data;
};

export const updateAnnouncement = async (id: number, req: { title: string; content: string; programId?: number; subjectCode?: string }) => {
  const { data } = await api.put<ApiResponse<PortalAnnouncement>>(`/portal/announcements/${id}`, req);
  return data.data;
};

export const deleteAnnouncement = async (id: number) => {
  await api.delete(`/portal/announcements/${id}`);
};

export const togglePinAnnouncement = async (id: number) => {
  await api.put(`/portal/announcements/${id}/pin`);
};

// ── Comments ──

export const getComments = async (announcementId: number) => {
  const { data } = await api.get<ApiResponse<PortalComment[]>>(`/portal/announcements/${announcementId}/comments`);
  return data.data;
};

export const addComment = async (announcementId: number, content: string) => {
  const { data } = await api.post<ApiResponse<PortalComment>>(`/portal/announcements/${announcementId}/comments`, { content });
  return data.data;
};

export const deleteComment = async (id: number) => {
  await api.delete(`/portal/comments/${id}`);
};

// ── Folders ──

export const getFolders = async () => {
  const { data } = await api.get<ApiResponse<PortalFolder[]>>('/portal/folders');
  return data.data;
};

export const createFolder = async (req: { name: string; subjectCode: string; programId: number; assignment: boolean; description?: string; deadline?: string }) => {
  const { data } = await api.post<ApiResponse<PortalFolder>>('/portal/folders', req);
  return data.data;
};

export const updateFolder = async (id: number, req: { name: string; description?: string; assignment: boolean; deadline?: string }) => {
  const { data } = await api.put<ApiResponse<PortalFolder>>(`/portal/folders/${id}`, req);
  return data.data;
};

export const deleteFolder = async (id: number) => {
  await api.delete(`/portal/folders/${id}`);
};

// ── Files ──

export const getFolderFiles = async (folderId: number) => {
  const { data } = await api.get<ApiResponse<PortalFile[]>>(`/portal/folders/${folderId}/files`);
  return data.data;
};

export const uploadPortalFile = async (folderId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ApiResponse<PortalFile>>(`/portal/folders/${folderId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
};

export const deletePortalFile = async (id: number) => {
  await api.delete(`/portal/files/${id}`);
};

export const getPortalFileUrl = (id: number) => {
  const token = getToken();
  return `/api/portal/files/${id}/download${token ? `?token=${token}` : ''}`;
};

export const getSubmissionFileUrl = (submissionId: number) => {
  const token = getToken();
  return `/api/portal/submissions/${submissionId}/download${token ? `?token=${token}` : ''}`;
};

// ── Submissions ──

export const getSubmissions = async (folderId: number) => {
  const { data } = await api.get<ApiResponse<PortalSubmission[]>>(`/portal/folders/${folderId}/submissions`);
  return data.data;
};

export const submitAssignment = async (folderId: number, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<ApiResponse<PortalSubmission>>(`/portal/folders/${folderId}/submissions`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data.data;
};

export const reviewSubmission = async (id: number, status: string, feedback?: string) => {
  const { data } = await api.put<ApiResponse<PortalSubmission>>(`/portal/submissions/${id}/review`, { status, feedback });
  return data.data;
};

export const getMySubmissions = async () => {
  const { data } = await api.get<ApiResponse<PortalSubmission[]>>('/portal/my-submissions');
  return data.data;
};
