import api from './client';
import type { ApiResponse, Attendance, AttendanceRequest, SubjectAbsenceStats } from '../types';

export const getAttendance = async () => {
  const { data } = await api.get<ApiResponse<Attendance[]>>('/attendance');
  return data.data;
};

export const getStudentAttendance = async (username: string) => {
  const { data } = await api.get<ApiResponse<Attendance[]>>(`/attendance/student/${username}`);
  return data.data;
};

export const createAttendance = async (attendance: AttendanceRequest) => {
  const { data } = await api.post<ApiResponse<Attendance>>('/attendance', attendance);
  return data.data;
};

export const updateAttendance = async (id: number, attendance: AttendanceRequest) => {
  const { data } = await api.put<ApiResponse<Attendance>>(`/attendance/${id}`, attendance);
  return data.data;
};

export const deleteAttendance = async (id: number) => {
  await api.delete(`/attendance/${id}`);
};

export const getAbsenceRate = async (username: string) => {
  const { data } = await api.get<ApiResponse<number>>(`/attendance/stats/absence-rate/${username}`);
  return data.data;
};

// ── Absence Stats (per-subject with limits) ──

export const getMyAbsenceStats = async () => {
  const { data } = await api.get<ApiResponse<SubjectAbsenceStats[]>>(
    '/attendance/my-absence-stats',
  );
  return data.data;
};

export const getStudentAbsenceStats = async (username: string) => {
  const { data } = await api.get<ApiResponse<SubjectAbsenceStats[]>>(
    `/attendance/absence-stats/${username}`,
  );
  return data.data;
};
