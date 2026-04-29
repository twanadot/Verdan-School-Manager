import api from './client';
import type { ApiResponse, Grade, GradeRequest } from '../types';

export interface EducationLevel {
  level: string;
  levelLabel: string;
  institutionName: string;
  institutionId: number;
  grades: Grade[];
  average: number;
  allPassing: boolean;
}

export const getGrades = async () => {
  const { data } = await api.get<ApiResponse<Grade[]>>('/grades');
  return data.data;
};

export const getGrade = async (id: number) => {
  const { data } = await api.get<ApiResponse<Grade>>(`/grades/${id}`);
  return data.data;
};

export const getStudentGrades = async (username: string) => {
  const { data } = await api.get<ApiResponse<Grade[]>>(`/grades/student/${username}`);
  return data.data;
};

export const getEducationHistory = async () => {
  const { data } = await api.get<ApiResponse<EducationLevel[]>>('/grades/history');
  return data.data;
};

export const createGrade = async (grade: GradeRequest) => {
  const { data } = await api.post<ApiResponse<Grade>>('/grades', grade);
  return data.data;
};

export const updateGrade = async (id: number, grade: GradeRequest) => {
  const { data } = await api.put<ApiResponse<Grade>>(`/grades/${id}`, grade);
  return data.data;
};

export const deleteGrade = async (id: number) => {
  await api.delete(`/grades/${id}`);
};

export const getGradeAverages = async () => {
  const { data } = await api.get<ApiResponse<Record<string, number>>>('/grades/stats/averages');
  return data.data;
};
