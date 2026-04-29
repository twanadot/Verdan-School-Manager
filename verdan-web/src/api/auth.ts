import api from './client';
import type { ApiResponse, LoginRequest, LoginResponse } from '../types';

export const login = async (credentials: LoginRequest) => {
  const { data } = await api.post<ApiResponse<LoginResponse>>('/login', credentials);
  return data.data;
};
