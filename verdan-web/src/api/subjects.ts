import api from './client';
import type {
  ApiResponse,
  Subject,
  SubjectRequest,
  SubjectMembers,
  AssignMemberRequest,
} from '../types';

export const getSubjects = async () => {
  const { data } = await api.get<ApiResponse<Subject[]>>('/subjects');
  return data.data;
};

export const getSubject = async (id: number) => {
  const { data } = await api.get<ApiResponse<Subject>>(`/subjects/${id}`);
  return data.data;
};

export const createSubject = async (subject: SubjectRequest) => {
  const { data } = await api.post<ApiResponse<Subject>>('/subjects', subject);
  return data.data;
};

export const updateSubject = async (id: number, subject: SubjectRequest) => {
  const { data } = await api.put<ApiResponse<Subject>>(`/subjects/${id}`, subject);
  return data.data;
};

export const deleteSubject = async (id: number) => {
  await api.delete(`/subjects/${id}`);
};

export const searchSubjects = async (q: string) => {
  const { data } = await api.get<ApiResponse<Subject[]>>('/subjects/search', { params: { q } });
  return data.data;
};

export const getSubjectMembers = async (code: string) => {
  const { data } = await api.get<ApiResponse<SubjectMembers>>(`/subjects/${code}/members`);
  return data.data;
};

export const assignMember = async (code: string, req: AssignMemberRequest) => {
  await api.post(`/subjects/${code}/members`, req);
};

export const removeMember = async (code: string, username: string) => {
  await api.delete(`/subjects/${code}/members/${username}`);
};
