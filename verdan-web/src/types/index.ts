// ─── API Response Wrapper ───
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
  errors?: string[];
  timestamp: string;
}

// ─── Institution ───
export type InstitutionLevel = 'GENERAL' | 'UNGDOMSSKOLE' | 'VGS' | 'FAGSKOLE' | 'UNIVERSITET';
export type InstitutionOwnership = 'PUBLIC' | 'PRIVATE';

export interface Institution {
  id: number;
  name: string;
  location?: string;
  level?: InstitutionLevel;
  ownership?: InstitutionOwnership;
  active: boolean;
}

// ─── Auth ───
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  user: UserInfo;
}

export interface UserInfo {
  id: number;
  username: string;
  role: Role;
  firstName: string;
  lastName: string;
  email: string;
  institutionId?: number;
  institutionName?: string;
  institutionLevel?: string;
}

export interface TokenResponse {
  token: string;
  refreshToken: string;
}

export type Role = 'SUPER_ADMIN' | 'INSTITUTION_ADMIN' | 'TEACHER' | 'STUDENT';

// ─── User ───
export interface User {
  id: number;
  username: string;
  role: Role;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  gender?: 'MALE' | 'FEMALE';
  birthDate?: string;
  institutionId?: number;
  institutionName?: string;
  transferredFromInstitutionId?: number;
  transferredFromInstitutionName?: string;
}

export interface CreateUserRequest {
  username?: string;
  password: string;
  role: Role;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  gender?: 'MALE' | 'FEMALE';
  birthDate?: string;
  institutionId?: number;
}

export interface UpdateUserRequest {
  role?: Role;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  gender?: 'MALE' | 'FEMALE';
  birthDate?: string;
  institutionId?: number;
}

// ─── Subject ───
export type SubjectLevel = 'UNGDOMSSKOLE' | 'VGS' | 'FAGSKOLE' | 'UNIVERSITET';

export interface Subject {
  id: number;
  code: string;
  name: string;
  description: string;
  level: SubjectLevel;
  institutionId?: number;
  institutionName?: string;
  program?: string;
  yearLevel?: string;
}

export interface SubjectRequest {
  code: string;
  name: string;
  description?: string;
  level?: SubjectLevel;
  teacherUsername?: string;
  institutionId?: number;
  program?: string;
  yearLevel?: string;
}

export interface SubjectMembers {
  students: User[];
  teachers: User[];
}

export interface AssignMemberRequest {
  username: string;
  role: 'STUDENT' | 'TEACHER';
}

// ─── Program (Linje / Degree / Fagskolegrad) ───
export interface ProgramSubjectSummary {
  id: number;
  code: string;
  name: string;
  yearLevel?: string;
}

export interface Program {
  id: number;
  name: string;
  description?: string;
  institutionId?: number;
  institutionName?: string;
  minGpa?: number | null;
  maxStudents?: number | null;
  prerequisites?: string | null;
  attendanceRequired: boolean;
  minAttendancePct?: number | null;
  programType?: string | null;
  currentStudentCount: number;
  subjects: ProgramSubjectSummary[];
}

export interface ProgramRequest {
  name: string;
  description?: string;
  institutionId?: number;
  minGpa?: number | null;
  maxStudents?: number | null;
  prerequisites?: string | null;
  attendanceRequired?: boolean;
  minAttendancePct?: number | null;
  programType?: string | null;
}

export interface ProgramMember {
  id: number;
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'STUDENT' | 'TEACHER';
  yearLevel?: string;
  enrolledAt?: string;
  graduated: boolean;
}

export interface ProgramMemberRequest {
  userId: number;
  role: 'STUDENT' | 'TEACHER';
  yearLevel?: string;
}

export interface ProgramMembers {
  students: ProgramMember[];
  teachers: ProgramMember[];
  totalCount: number;
}

// ─── Grade ───
export interface Grade {
  id: number;
  studentUsername: string;
  studentName: string;
  subject: string;
  value: string;
  dateGiven: string;
  teacherUsername: string;
  institutionId?: number;
  institutionName?: string;
  yearLevel?: string;
  originalValue?: string;
  blockedByAbsence?: boolean;
  retake?: boolean;
}

export interface GradeRequest {
  studentUsername: string;
  subject: string;
  value: string;
  institutionId?: number;
  yearLevel?: string;
  retake?: boolean;
}

// ─── Attendance ───
export type AttendanceStatus = 'Present' | 'Absent' | 'Sick' | 'Late' | 'Excused';

export interface Attendance {
  id: number;
  studentUsername: string;
  studentName: string;
  date: string;
  status: AttendanceStatus;
  subjectCode: string;
  note: string;
  institutionId?: number;
  institutionName?: string;
  excused: boolean;
}

export interface AttendanceRequest {
  studentUsername: string;
  date?: string;
  status: AttendanceStatus;
  subjectCode: string;
  note?: string;
  institutionId?: number;
  excused?: boolean;
}

export interface SubjectAbsenceStats {
  subjectCode: string;
  subjectName: string;
  totalSessions: number;
  attended: number;
  absentUnexcused: number;
  absentExcused: number;
  absencePercent: number;
  maxAbsencePercent: number | null;
  overLimit: boolean;
  status: 'OK' | 'WARNING' | 'EXCEEDED' | 'NO_LIMIT';
  institutionLevel: string;
  institutionName: string;
  yearLevel?: string;
}

// ─── Room ───
export interface Room {
  id: number;
  roomNumber: string;
  roomType: string;
  capacity: number;
  institutionId?: number;
  institutionName?: string;
}

export interface RoomRequest {
  roomNumber: string;
  roomType?: string;
  capacity: number;
  institutionId?: number;
}

// ─── Booking ───
export interface Booking {
  id: number;
  rooms: string[];
  startDateTime: string;
  endDateTime: string;
  status: string;
  description: string;
  createdBy: string;
  subject: string;
  institutionName?: string;
  programId?: number;
  programName?: string;
}

export interface BookingRequest {
  roomId: number;
  startDateTime: string;
  endDateTime: string;
  description?: string;
  subject: string;
  programId?: number;
}

// ─── Chat System ───

export interface ChatRoom {
  id: number;
  name: string;
  isGroup: boolean;
  createdById: number;
  members: ChatMemberInfo[];
  lastMessage: ChatMessagePreview | null;
  unreadCount: number;
  createdAt: string;
}

export interface ChatMemberInfo {
  userId: number;
  username: string;
  fullName: string;
  role: string;
  institutionName: string;
  online: boolean;
}

export interface ChatMessagePreview {
  id: number;
  senderId: number;
  senderName: string;
  content: string;
  sentAt: string;
  deleted: boolean;
}

export interface ChatMessage {
  id: number;
  roomId: number;
  senderId: number;
  senderUsername: string;
  senderName: string;
  senderRole: string;
  content: string;
  sentAt: string;
  editedAt: string | null;
  deleted: boolean;
  replyTo: ChatReplyPreview | null;
  attachments: ChatAttachment[];
  reactions: ChatReactionGroup[];
}

export interface ChatReplyPreview {
  id: number;
  senderName: string;
  content: string;
}

export interface ChatAttachment {
  id: number;
  fileName: string;
  fileSize: number;
  mimeType: string;
  isImage: boolean;
  downloadUrl: string;
}

export interface ChatReactionGroup {
  emoji: string;
  count: number;
  usernames: string[];
  currentUserReacted: boolean;
}

export interface SendChatMessageRequest {
  content: string;
  replyToId?: number;
  attachmentIds?: number[];
}

export interface ContactUser {
  id: number;
  username: string;
  fullName: string;
  role: string;
  institutionName: string;
  online: boolean;
}

export interface UnreadCountResponse {
  count: number;
}

export interface UserStatusResponse {
  userId: number;
  online: boolean;
  lastSeen: string;
}

export interface UploadResponse {
  id: number;
  fileName: string;
  fileSize: number;
  mimeType: string;
}
