import api from './client';
import type { ApiResponse } from '../types';

export interface PromotionAction {
  userId: number;
  username: string;
  fullName: string;
  programId: number;
  programName: string;
  fromYear: string;
  toYear: string | null;
  graduating: boolean;
  targetProgramId: number;
  targetProgramName: string | null;
}

export interface PromotionPreview {
  promotions: PromotionAction[];
  graduations: PromotionAction[];
  level: string;
  missingClasses: string[];
}

export interface PromotionResult {
  promoted: number;
  graduated: number;
  level: string;
}

export const getPromotionPreview = async () => {
  const { data } = await api.get<ApiResponse<PromotionPreview>>('/promotion/preview');
  return data.data;
};

export const executePromotion = async () => {
  const { data } = await api.post<ApiResponse<PromotionResult>>('/promotion/advance');
  return data.data;
};

export const undoPromotion = async () => {
  const { data } = await api.post<ApiResponse<PromotionResult>>('/promotion/undo');
  return data.data;
};

export const transferStudent = async (
  userId: number,
  fromProgramId: number,
  toProgramId: number,
) => {
  const { data } = await api.post<ApiResponse<string>>('/promotion/transfer', {
    userId,
    fromProgramId,
    toProgramId,
  });
  return data.data;
};
