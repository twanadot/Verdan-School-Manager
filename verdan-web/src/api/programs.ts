import api from './client';
import type {
  ApiResponse,
  Program,
  ProgramRequest,
  ProgramMembers,
  ProgramMember,
  ProgramMemberRequest,
} from '../types';

export const getPrograms = async () => {
  const { data } = await api.get<ApiResponse<Program[]>>('/programs');
  return data.data;
};

export const getProgram = async (id: number) => {
  const { data } = await api.get<ApiResponse<Program>>(`/programs/${id}`);
  return data.data;
};

export const createProgram = async (req: ProgramRequest) => {
  const { data } = await api.post<ApiResponse<Program>>('/programs', req);
  return data.data;
};

export const updateProgram = async (id: number, req: ProgramRequest) => {
  const { data } = await api.put<ApiResponse<Program>>(`/programs/${id}`, req);
  return data.data;
};

export const deleteProgram = async (id: number) => {
  await api.delete(`/programs/${id}`);
};

export const addSubjectToProgram = async (programId: number, subjectId: number) => {
  await api.post(`/programs/${programId}/subjects/${subjectId}`);
};

export const removeSubjectFromProgram = async (programId: number, subjectId: number) => {
  await api.delete(`/programs/${programId}/subjects/${subjectId}`);
};

// ── Member management ──

export const getProgramMembers = async (programId: number) => {
  const { data } = await api.get<ApiResponse<ProgramMembers>>(`/programs/${programId}/members`);
  return data.data;
};

export const addProgramMember = async (programId: number, req: ProgramMemberRequest) => {
  const { data } = await api.post<ApiResponse<ProgramMember>>(
    `/programs/${programId}/members`,
    req,
  );
  return data.data;
};

export const removeProgramMember = async (programId: number, userId: number) => {
  await api.delete(`/programs/${programId}/members/${userId}`);
};

// ── Graduated students ──

export interface GraduatedStudent {
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  programId: number;
  programName: string;
  yearLevel: string;
  diplomaEligible: boolean;
  enrolledAt: string | null;
  programType: string | null;
}

export const getGraduatedStudents = async () => {
  const { data } = await api.get<ApiResponse<GraduatedStudent[]>>('/programs/graduated');
  return data.data;
};

export const getMyGraduation = async () => {
  const { data } = await api.get<ApiResponse<{ graduated: boolean }>>('/programs/my-graduation');
  return data.data;
};

// ── Archive Management ──

export const getArchivedStudents = async () => {
  const { data } = await api.get<ApiResponse<GraduatedStudent[]>>('/programs/archived');
  return data.data;
};

export const archiveStudent = async (programId: number, userId: number) => {
  const { data } = await api.post<ApiResponse<string>>(`/programs/${programId}/archive/${userId}`);
  return data.data;
};

export const restoreStudent = async (programId: number, userId: number) => {
  const { data } = await api.post<ApiResponse<string>>(`/programs/${programId}/restore/${userId}`);
  return data.data;
};

export const bulkArchiveAll = async () => {
  const { data } = await api.post<ApiResponse<{ archivedCount: number }>>('/programs/archive-all');
  return data.data;
};
