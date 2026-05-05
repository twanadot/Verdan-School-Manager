import api from './client';
import { Institution } from '../types';

export const getInstitutions = async (): Promise<Institution[]> => {
  const { data } = await api.get('/institutions');
  return data.data;
};

export const createInstitution = async (payload: {
  name: string;
  location?: string;
  level?: string;
}): Promise<Institution> => {
  const { data } = await api.post('/institutions', payload);
  return data.data;
};

export const updateInstitution = async (
  id: number,
  payload: { name: string; location?: string; level?: string },
): Promise<Institution> => {
  const { data } = await api.put(`/institutions/${id}`, payload);
  return data.data;
};

export const deleteInstitution = async (id: number): Promise<void> => {
  await api.delete(`/institutions/${id}`);
};

export const getInactiveInstitutions = async (): Promise<Institution[]> => {
  const { data } = await api.get('/institutions/inactive');
  return data.data;
};

export const reactivateInstitution = async (id: number): Promise<void> => {
  await api.put(`/institutions/${id}/reactivate`);
};
