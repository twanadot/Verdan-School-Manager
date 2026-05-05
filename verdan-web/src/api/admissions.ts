import api from './client';
import type { ApiResponse } from '../types';

// ── Types ──

export interface AdmissionPeriod {
  id: number;
  name: string;
  fromLevel: string;
  toLevel: string;
  startDate: string;
  endDate: string;
  status: 'OPEN' | 'CLOSED' | 'PROCESSED';
  maxChoices: number;
  institutionId: number;
  institutionName: string;
  applicationCount: number;
}

export interface AdmissionRequirement {
  id: number;
  programId: number;
  programName: string;
  minGpa: number | null;
  maxStudents: number | null;
  applicantCount: number;
}

export interface ApplicationChoice {
  programId: number;
  priority: number;
}

export interface ApplicationResponse {
  id: number;
  periodId: number;
  periodName: string;
  programId: number;
  programName: string;
  institutionName: string;
  priority: number;
  gpaSnapshot: number | null;
  status: 'PENDING' | 'ACCEPTED' | 'CONFIRMED' | 'WAITLISTED' | 'REJECTED' | 'WITHDRAWN' | 'ENROLLED';
  submittedAt: string | null;
  processedAt: string | null;
}

export interface ProcessingResult {
  accepted: number;
  waitlisted: number;
  rejected: number;
  total: number;
}

export interface ProgramApplicantSummary {
  programId: number;
  programName: string;
  minGpa: number | null;
  maxStudents: number | null;
  totalApplicants: number;
  applicants: ApplicantDetail[];
}

export interface ApplicantDetail {
  applicationId: number;
  studentId: number;
  username: string;
  fullName: string;
  priority: number;
  gpaSnapshot: number | null;
  status: string;
}

export interface PortalListing {
  periodId: number;
  periodName: string;
  fromLevel: string;
  toLevel: string;
  startDate: string;
  endDate: string;
  maxChoices: number;
  institutionId: number;
  institutionName: string;
  ownership: 'PUBLIC' | 'PRIVATE';
  programId: number;
  programName: string;
  minGpa: number | null;
  maxStudents: number | null;
  prerequisites: string | null;
  applicantCount: number;
}

// ── Portal API ──

export const getPortalListings = async (fromLevel?: string) => {
  const params = fromLevel ? { fromLevel } : {};
  const { data } = await api.get<ApiResponse<PortalListing[]>>('/admissions/portal', { params });
  return data.data;
};

// ── Student API ──

export const getAvailablePeriods = async () => {
  const { data } = await api.get<ApiResponse<AdmissionPeriod[]>>('/admissions/available');
  return data.data;
};

export const submitApplications = async (periodId: number, choices: ApplicationChoice[]) => {
  const { data } = await api.post<ApiResponse<ApplicationResponse[]>>('/admissions/apply', {
    periodId,
    choices,
  });
  return data.data;
};

export const getMyApplications = async () => {
  const { data } = await api.get<ApiResponse<ApplicationResponse[]>>('/admissions/my-applications');
  return data.data;
};

export const withdrawApplication = async (id: number) => {
  await api.put(`/admissions/applications/${id}/withdraw`);
};

export const confirmApplication = async (id: number) => {
  await api.put(`/admissions/applications/${id}/confirm`);
};

// ── Admin API ──

export const getAdmissionPeriods = async () => {
  const { data } = await api.get<ApiResponse<AdmissionPeriod[]>>('/admissions/periods');
  return data.data;
};

export const createAdmissionPeriod = async (req: {
  name: string;
  fromLevel: string;
  toLevel: string;
  startDate: string;
  endDate: string;
  maxChoices: number;
}) => {
  const { data } = await api.post<ApiResponse<AdmissionPeriod>>('/admissions/periods', req);
  return data.data;
};

export const updateAdmissionPeriod = async (
  id: number,
  req: {
    name?: string;
    startDate?: string;
    endDate?: string;
    maxChoices?: number;
  },
) => {
  const { data } = await api.put<ApiResponse<AdmissionPeriod>>(`/admissions/periods/${id}`, req);
  return data.data;
};

export const closeAdmissionPeriod = async (id: number) => {
  await api.put(`/admissions/periods/${id}/close`);
};

export const processAdmissions = async (id: number) => {
  const { data } = await api.post<ApiResponse<ProcessingResult>>(
    `/admissions/periods/${id}/process`,
  );
  return data.data;
};

export const getAdmissionOverview = async (id: number) => {
  const { data } = await api.get<ApiResponse<ProgramApplicantSummary[]>>(
    `/admissions/periods/${id}/overview`,
  );
  return data.data;
};

export const getAdmissionRequirements = async (id: number) => {
  const { data } = await api.get<ApiResponse<AdmissionRequirement[]>>(
    `/admissions/periods/${id}/requirements`,
  );
  return data.data;
};

export const setAdmissionRequirement = async (
  periodId: number,
  req: {
    programId: number;
    minGpa?: number | null;
    maxStudents?: number | null;
  },
) => {
  const { data } = await api.post<ApiResponse<AdmissionRequirement>>(
    `/admissions/periods/${periodId}/requirements`,
    req,
  );
  return data.data;
};

export const deleteAdmissionPeriod = async (id: number) => {
  await api.delete(`/admissions/periods/${id}`);
};

export const reopenAdmissionPeriod = async (id: number, newEndDate?: string) => {
  await api.put(`/admissions/periods/${id}/reopen`, { newEndDate });
};

export const bulkPublishPrograms = async (periodId: number, programIds: number[]) => {
  const { data } = await api.post<ApiResponse<number>>(
    `/admissions/periods/${periodId}/bulk-publish`,
    { programIds },
  );
  return data.data;
};

// ── Enrollment ──

export interface EnrollmentResult {
  enrolled: number;
  skipped: number;
  total: number;
}

export const enrollAccepted = async (periodId: number) => {
  const { data } = await api.post<ApiResponse<EnrollmentResult>>(
    `/admissions/periods/${periodId}/enroll-accepted`,
  );
  return data.data;
};
