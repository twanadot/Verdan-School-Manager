import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { toast } from 'sonner';
import {
  Megaphone,
  FolderOpen,
  Plus,
  Pin,
  Trash2,
  Edit3,
  MessageCircle,
  Send,
  Upload,
  Download,
  FileText,
  X,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Clock,
  PinOff,
  Image as ImageIcon,
  AlertTriangle,
  CalendarClock,
  Save,
} from 'lucide-react';
import {
  getAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  togglePinAnnouncement,
  getComments,
  addComment,
  deleteComment,
  getFolders,
  createFolder,
  updateFolder,
  deleteFolder,
  getFolderFiles,
  uploadPortalFile,
  deletePortalFile,
  getPortalFileUrl,
  getSubmissions,
  submitAssignment,
  reviewSubmission,
  getMySubmissions,
  getSubmissionFileUrl,
  type PortalAnnouncement,
  type PortalComment,
  type PortalFolder,
  type PortalFile,
  type PortalSubmission,
} from '../api/portal';
import { getPrograms, getProgramMembers } from '../api/programs';
import { getSubjects } from '../api/subjects';
import type { Program, Subject } from '../types';

// ── Helper ──
function toUtc(d: string) {
  return d && !/[Z+-]/.test(d.slice(-6)) ? d + 'Z' : d;
}
function timeAgo(d: string) {
  const ms = Date.now() - new Date(toUtc(d)).getTime();
  const mins = Math.floor(ms / 60000);
  if (mins < 1) return 'nå';
  if (mins < 60) return `${mins} min siden`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}t siden`;
  const days = Math.floor(hrs / 24);
  if (days === 1) return 'i går';
  return `${days}d siden`;
}
function fmtSize(b: number) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
  return (b / 1048576).toFixed(1) + ' MB';
}

export function PortalPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<'announcements' | 'files'>('announcements');
  const isTeacher = user?.role === 'TEACHER' || user?.role === 'INSTITUTION_ADMIN';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-text-primary">Elevportalen</h1>
      </div>

      {/* Tab bar */}
      <div className="flex gap-1 bg-bg-secondary border border-border rounded-xl p-1">
        <button
          onClick={() => setTab('announcements')}
          className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${
            tab === 'announcements'
              ? 'bg-accent text-white shadow-md'
              : 'text-text-muted hover:text-text-primary hover:bg-bg-hover'
          }`}
        >
          <Megaphone size={16} /> Kunngjøringer
        </button>
        <button
          onClick={() => setTab('files')}
          className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${
            tab === 'files'
              ? 'bg-accent text-white shadow-md'
              : 'text-text-muted hover:text-text-primary hover:bg-bg-hover'
          }`}
        >
          <FolderOpen size={16} /> Mapper & Filer
        </button>
      </div>

      {tab === 'announcements' ? (
        <AnnouncementsTab isTeacher={isTeacher} userId={user?.id || 0} userRole={user?.role} />
      ) : (
        <FilesTab isTeacher={isTeacher} userId={user?.id || 0} userRole={user?.role} />
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// ANNOUNCEMENTS TAB
// ═══════════════════════════════════════════════════════════════

function AnnouncementsTab({
  isTeacher,
  userId,
  userRole,
}: {
  isTeacher: boolean;
  userId: number;
  userRole?: string;
}) {
  const qc = useQueryClient();
  const { data: announcements = [] } = useQuery({
    queryKey: ['portalAnnouncements'],
    queryFn: getAnnouncements,
  });
  const { data: programs = [] } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [filterProgram, setFilterProgram] = useState<string>('');
  const [filterSubject, setFilterSubject] = useState<string>('');

  // For teachers: only show subjects they are assigned to (subjects is already filtered by backend)
  const teacherSubjectCodes = useMemo(() => new Set(subjects.map((s) => s.code)), [subjects]);

  const selectedProgram = programs.find((p) => p.id === Number(filterProgram));
  const availableSubjects =
    filterProgram && selectedProgram
      ? userRole === 'TEACHER'
        ? (selectedProgram.subjects || []).filter((s) => teacherSubjectCodes.has(s.code))
        : selectedProgram.subjects || []
      : subjects.map((s) => ({ code: s.code, name: s.name }));

  const filtered = useMemo(() => {
    let list = announcements;
    if (filterProgram) list = list.filter((a) => a.programId === Number(filterProgram));
    if (filterSubject) list = list.filter((a) => a.subjectCode === filterSubject);
    return list;
  }, [announcements, filterProgram, filterSubject]);

  const deleteMut = useMutation({
    mutationFn: deleteAnnouncement,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalAnnouncements'] });
      toast.success('Slettet');
    },
  });
  const pinMut = useMutation({
    mutationFn: togglePinAnnouncement,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalAnnouncements'] });
    },
  });

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <select
          value={filterProgram}
          onChange={(e) => {
            setFilterProgram(e.target.value);
            setFilterSubject('');
          }}
          className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">Alle klasser</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select
          value={filterSubject}
          onChange={(e) => setFilterSubject(e.target.value)}
          className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">{filterProgram ? 'Alle fag i klassen' : 'Alle fag'}</option>
          {availableSubjects.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name}
            </option>
          ))}
        </select>
        {isTeacher && (
          <button
            onClick={() => {
              setShowForm(!showForm);
              setEditingId(null);
            }}
            className="ml-auto flex items-center gap-1.5 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"
          >
            <Plus size={16} /> Ny kunngjøring
          </button>
        )}
      </div>

      {/* Create/edit form */}
      {isTeacher && showForm && (
        <AnnouncementForm
          programs={programs}
          subjects={subjects}
          editingId={editingId}
          announcements={announcements}
          onSaved={() => {
            setShowForm(false);
            setEditingId(null);
            qc.invalidateQueries({ queryKey: ['portalAnnouncements'] });
          }}
          onCancel={() => {
            setShowForm(false);
            setEditingId(null);
          }}
        />
      )}

      {/* Announcement cards */}
      {filtered.length === 0 ? (
        <div className="text-center py-12 text-text-muted">
          <Megaphone size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-lg font-medium">Ingen kunngjøringer ennå</p>
        </div>
      ) : (
        <div className="space-y-4">
          {filtered.map((a) => (
            <AnnouncementCard
              key={a.id}
              announcement={a}
              isTeacher={isTeacher}
              userId={userId}
              onEdit={() => {
                setEditingId(a.id);
                setShowForm(true);
              }}
              onDelete={() => deleteMut.mutate(a.id)}
              onPin={() => pinMut.mutate(a.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function AnnouncementForm({
  programs,
  subjects,
  editingId,
  announcements,
  onSaved,
  onCancel,
}: {
  programs: Program[];
  subjects: Subject[];
  editingId: number | null;
  announcements: PortalAnnouncement[];
  onSaved: () => void;
  onCancel: () => void;
}) {
  const existing = editingId ? announcements.find((a) => a.id === editingId) : null;
  const [title, setTitle] = useState(existing?.title || '');
  const [content, setContent] = useState(existing?.content || '');
  const [programId, setProgramId] = useState<string>(existing?.programId?.toString() || '');
  const [subjectCode, setSubjectCode] = useState(existing?.subjectCode || '');
  const [loading, setLoading] = useState(false);

  // For teachers: only show subjects they are assigned to
  const teacherSubjectCodes = useMemo(() => new Set(subjects.map((s) => s.code)), [subjects]);

  const selectedProgram = programs.find((p) => p.id === Number(programId));
  const availableSubjects =
    programId && selectedProgram
      ? (selectedProgram.subjects || []).filter((s) => teacherSubjectCodes.has(s.code))
      : subjects.map((s) => ({ code: s.code, name: s.name }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;
    setLoading(true);
    try {
      const req = {
        title,
        content,
        programId: programId ? Number(programId) : undefined,
        subjectCode: subjectCode || undefined,
      };
      if (editingId) await updateAnnouncement(editingId, req);
      else await createAnnouncement(req);
      toast.success(editingId ? 'Oppdatert' : 'Publisert');
      onSaved();
    } catch {
      toast.error('Feil ved lagring');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-bg-secondary border border-border rounded-xl p-5 space-y-4"
    >
      <h3 className="text-sm font-semibold text-text-primary">
        {editingId ? 'Rediger kunngjøring' : '📝 Ny kunngjøring'}
      </h3>
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Tittel..."
        required
        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
      />
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="Skriv melding..."
        required
        rows={3}
        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus resize-none"
      />
      <div className="flex gap-3">
        <select
          value={programId}
          onChange={(e) => {
            setProgramId(e.target.value);
            setSubjectCode('');
          }}
          className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">Alle klasser</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select
          value={subjectCode}
          onChange={(e) => setSubjectCode(e.target.value)}
          className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">{programId ? 'Velg fag i klassen...' : 'Alle fag'}</option>
          {availableSubjects.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
        >
          Avbryt
        </button>
        <button
          type="submit"
          disabled={loading}
          className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white font-medium transition-colors disabled:opacity-50"
        >
          {loading ? 'Lagrer...' : editingId ? 'Oppdater' : 'Publiser'}
        </button>
      </div>
    </form>
  );
}

function AnnouncementCard({
  announcement: a,
  isTeacher,
  userId,
  onEdit,
  onDelete,
  onPin,
}: {
  announcement: PortalAnnouncement;
  isTeacher: boolean;
  userId: number;
  onEdit: () => void;
  onDelete: () => void;
  onPin: () => void;
}) {
  const [showComments, setShowComments] = useState(false);
  const isOwner = a.authorId === userId || isTeacher;

  return (
    <div
      className={`bg-bg-secondary border rounded-xl overflow-hidden transition-colors ${a.pinned ? 'border-amber-500/40 bg-amber-500/5' : 'border-border'}`}
    >
      <div className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              {a.pinned && <Pin size={14} className="text-amber-500 shrink-0" />}
              <h3 className="text-base font-semibold text-text-primary">{a.title}</h3>
            </div>
            <p className="text-sm text-text-secondary whitespace-pre-wrap">{a.content}</p>
          </div>
          {isOwner && (
            <div className="flex items-center gap-1 shrink-0">
              <button
                onClick={onPin}
                className="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
                title={a.pinned ? 'Fjern pin' : 'Pin'}
              >
                {a.pinned ? (
                  <PinOff size={14} className="text-amber-500" />
                ) : (
                  <Pin size={14} className="text-text-muted" />
                )}
              </button>
              <button
                onClick={onEdit}
                className="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              >
                <Edit3 size={14} className="text-text-muted" />
              </button>
              <button
                onClick={onDelete}
                className="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              >
                <Trash2 size={14} className="text-danger" />
              </button>
            </div>
          )}
        </div>
        <div className="flex items-center gap-3 mt-3 text-xs text-text-muted">
          <span className="font-medium">{a.authorName}</span>
          <span>·</span>
          <span>{timeAgo(a.createdAt)}</span>
          {a.programName && (
            <>
              <span>·</span>
              <span className="px-1.5 py-0.5 bg-accent/10 text-accent rounded-full text-[10px] font-semibold">
                {a.programName}
              </span>
            </>
          )}
          {a.subjectName && (
            <>
              <span>·</span>
              <span className="px-1.5 py-0.5 bg-purple-500/10 text-purple-400 rounded-full text-[10px] font-semibold">
                {a.subjectName}
              </span>
            </>
          )}
        </div>
      </div>
      {/* Comments toggle */}
      <button
        onClick={() => setShowComments(!showComments)}
        className="w-full px-5 py-2 border-t border-border/50 flex items-center gap-2 text-xs text-text-muted hover:bg-bg-hover/30 transition-colors"
      >
        <MessageCircle size={13} /> {a.commentCount} kommentarer
        {showComments ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
      </button>
      {showComments && (
        <CommentsSection announcementId={a.id} isTeacher={isTeacher} userId={userId} />
      )}
    </div>
  );
}

function CommentsSection({
  announcementId,
  isTeacher,
  userId,
}: {
  announcementId: number;
  isTeacher: boolean;
  userId: number;
}) {
  const qc = useQueryClient();
  const { data: comments = [] } = useQuery({
    queryKey: ['portalComments', announcementId],
    queryFn: () => getComments(announcementId),
  });
  const [text, setText] = useState('');
  const addMut = useMutation({
    mutationFn: (content: string) => addComment(announcementId, content),
    onSuccess: () => {
      setText('');
      qc.invalidateQueries({ queryKey: ['portalComments', announcementId] });
      qc.invalidateQueries({ queryKey: ['portalAnnouncements'] });
    },
  });
  const delMut = useMutation({
    mutationFn: deleteComment,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalComments', announcementId] });
      qc.invalidateQueries({ queryKey: ['portalAnnouncements'] });
    },
  });

  return (
    <div className="px-5 pb-4 space-y-2 bg-bg-primary/30 border-t border-border/30">
      {comments.map((c) => (
        <div key={c.id} className="flex gap-2 py-1.5 group">
          <div className="flex-1 min-w-0">
            <span className="text-xs font-semibold text-text-primary">{c.authorName}</span>
            <span className="text-[10px] text-text-muted ml-1.5">
              {c.authorRole === 'TEACHER' || c.authorRole === 'INSTITUTION_ADMIN' ? '(Lærer)' : ''}
            </span>
            <span className="text-[10px] text-text-muted ml-1.5">· {timeAgo(c.createdAt)}</span>
            <p className="text-xs text-text-secondary mt-0.5">{c.content}</p>
          </div>
          {(c.authorId === userId || isTeacher) && (
            <button
              onClick={() => delMut.mutate(c.id)}
              className="p-1 opacity-0 group-hover:opacity-100 text-text-muted hover:text-danger transition-all shrink-0"
            >
              <X size={12} />
            </button>
          )}
        </div>
      ))}
      <div className="flex gap-2 pt-1">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Skriv kommentar..."
          onKeyDown={(e) => e.key === 'Enter' && text.trim() && addMut.mutate(text.trim())}
          className="flex-1 px-3 py-1.5 bg-bg-input border border-border rounded-lg text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
        />
        <button
          onClick={() => text.trim() && addMut.mutate(text.trim())}
          disabled={!text.trim()}
          className="p-1.5 rounded-lg bg-accent hover:bg-accent-hover text-white disabled:opacity-30 transition-colors"
        >
          <Send size={13} />
        </button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// FILES TAB
// ═══════════════════════════════════════════════════════════════

function FilesTab({
  isTeacher,
  userId,
  userRole,
}: {
  isTeacher: boolean;
  userId: number;
  userRole?: string;
}) {
  const qc = useQueryClient();
  const { data: folders = [] } = useQuery({ queryKey: ['portalFolders'], queryFn: getFolders });
  const { data: programs = [] } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });
  const { data: mySubmissions = [] } = useQuery({
    queryKey: ['mySubmissions'],
    queryFn: getMySubmissions,
    enabled: !isTeacher,
  });
  const [showForm, setShowForm] = useState(false);
  const [filterProgram, setFilterProgram] = useState<string>('');
  const [filterSubject, setFilterSubject] = useState<string>('');

  // For teachers: only show subjects they are assigned to (subjects is already filtered by backend)
  const teacherSubjectCodes = useMemo(() => new Set(subjects.map((s) => s.code)), [subjects]);

  const selectedProgram = programs.find((p) => p.id === Number(filterProgram));
  const availableSubjects =
    filterProgram && selectedProgram
      ? userRole === 'TEACHER'
        ? (selectedProgram.subjects || []).filter((s) => teacherSubjectCodes.has(s.code))
        : selectedProgram.subjects || []
      : subjects.map((s) => ({ code: s.code, name: s.name }));

  const filtered = useMemo(() => {
    let list = folders;
    if (filterProgram) list = list.filter((f) => f.programId === Number(filterProgram));
    if (filterSubject) list = list.filter((f) => f.subjectCode === filterSubject);
    return list;
  }, [folders, filterProgram, filterSubject]);

  const deleteFolderMut = useMutation({
    mutationFn: deleteFolder,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalFolders'] });
      toast.success('Mappe slettet');
    },
  });

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <select
          value={filterProgram}
          onChange={(e) => {
            setFilterProgram(e.target.value);
            setFilterSubject('');
          }}
          className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">Alle klasser</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select
          value={filterSubject}
          onChange={(e) => setFilterSubject(e.target.value)}
          className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">{filterProgram ? 'Alle fag i klassen' : 'Alle fag'}</option>
          {availableSubjects.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name}
            </option>
          ))}
        </select>
        {isTeacher && (
          <button
            onClick={() => setShowForm(!showForm)}
            className="ml-auto flex items-center gap-1.5 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"
          >
            <Plus size={16} /> Ny mappe
          </button>
        )}
      </div>

      {isTeacher && showForm && (
        <FolderForm
          programs={programs}
          subjects={subjects}
          onSaved={() => {
            setShowForm(false);
            qc.invalidateQueries({ queryKey: ['portalFolders'] });
          }}
          onCancel={() => setShowForm(false)}
        />
      )}

      {filtered.length === 0 ? (
        <div className="text-center py-12 text-text-muted">
          <FolderOpen size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-lg font-medium">Ingen mapper ennå</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((f) => (
            <FolderCard
              key={f.id}
              folder={f}
              isTeacher={isTeacher}
              userId={userId}
              mySubmissions={mySubmissions}
              onDelete={() => deleteFolderMut.mutate(f.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function FolderForm({
  programs,
  subjects,
  onSaved,
  onCancel,
}: {
  programs: Program[];
  subjects: Subject[];
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [programId, setProgramId] = useState('');
  const [subjectCode, setSubjectCode] = useState('');
  const [isAssignment, setIsAssignment] = useState(false);
  const [description, setDescription] = useState('');
  const [deadline, setDeadline] = useState('');
  const [loading, setLoading] = useState(false);

  // For teachers: only show subjects they are assigned to
  const teacherSubjectCodes = useMemo(() => new Set(subjects.map((s) => s.code)), [subjects]);

  const selectedProgram = programs.find((p) => p.id === Number(programId));
  const availableSubjects =
    programId && selectedProgram
      ? (selectedProgram.subjects || []).filter((s) => teacherSubjectCodes.has(s.code))
      : [];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !programId || !subjectCode) return;
    setLoading(true);
    try {
      await createFolder({
        name,
        subjectCode,
        programId: Number(programId),
        assignment: isAssignment,
        description,
        deadline: deadline || undefined,
      });
      toast.success('Mappe opprettet');
      onSaved();
    } catch {
      toast.error('Feil ved opprettelse');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-bg-secondary border border-border rounded-xl p-5 space-y-4"
    >
      <h3 className="text-sm font-semibold text-text-primary">📁 Ny mappe</h3>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Mappenavn..."
        required
        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
      />
      <div className="flex gap-3">
        <select
          value={programId}
          onChange={(e) => {
            setProgramId(e.target.value);
            setSubjectCode('');
          }}
          required
          className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
        >
          <option value="">Velg klasse...</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select
          value={subjectCode}
          onChange={(e) => setSubjectCode(e.target.value)}
          required
          disabled={!programId}
          className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <option value="">{programId ? 'Velg fag...' : 'Velg klasse først...'}</option>
          {availableSubjects.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Beskrivelse (valgfritt)..."
        rows={2}
        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus resize-none"
      />
      <label className="flex items-center gap-2 cursor-pointer">
        <input
          type="checkbox"
          checked={isAssignment}
          onChange={(e) => setIsAssignment(e.target.checked)}
          className="w-4 h-4 rounded border-border accent-accent"
        />
        <span className="text-sm text-text-secondary">📋 Oppgavemappe (elever kan levere inn)</span>
      </label>
      {isAssignment && (
        <div className="flex items-center gap-2">
          <CalendarClock size={16} className="text-text-muted" />
          <label className="text-sm text-text-secondary">Tidsfrist:</label>
          <input
            type="datetime-local"
            value={deadline}
            onChange={(e) => setDeadline(e.target.value)}
            className="px-3 py-1.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
          />
        </div>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
        >
          Avbryt
        </button>
        <button
          type="submit"
          disabled={loading}
          className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white font-medium transition-colors disabled:opacity-50"
        >
          {loading ? 'Oppretter...' : 'Opprett mappe'}
        </button>
      </div>
    </form>
  );
}

function FolderCard({
  folder,
  isTeacher,
  userId,
  mySubmissions,
  onDelete,
}: {
  folder: PortalFolder;
  isTeacher: boolean;
  userId: number;
  mySubmissions: PortalSubmission[];
  onDelete: () => void;
}) {
  const qc = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState(folder.name);
  const [editDesc, setEditDesc] = useState(folder.description || '');
  const [editAssignment, setEditAssignment] = useState(folder.assignment);
  const [editDeadline, setEditDeadline] = useState(
    folder.deadline ? folder.deadline.slice(0, 16) : '',
  );
  const [editLoading, setEditLoading] = useState(false);
  const [feedbackFor, setFeedbackFor] = useState<number | null>(null);
  const [feedbackText, setFeedbackText] = useState('');
  const { data: files = [] } = useQuery({
    queryKey: ['portalFiles', folder.id],
    queryFn: () => getFolderFiles(folder.id),
    enabled: expanded,
  });
  const { data: submissions = [] } = useQuery({
    queryKey: ['portalSubmissions', folder.id],
    queryFn: () => getSubmissions(folder.id),
    enabled: expanded && isTeacher && folder.assignment,
  });
  const mySub = mySubmissions.filter((s) => s.folderId === folder.id);

  const uploadFileMut = useMutation({
    mutationFn: (file: File) => uploadPortalFile(folder.id, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalFiles', folder.id] });
      qc.invalidateQueries({ queryKey: ['portalFolders'] });
      toast.success('Fil lastet opp');
    },
  });

  const deleteFileMut = useMutation({
    mutationFn: deletePortalFile,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalFiles', folder.id] });
      qc.invalidateQueries({ queryKey: ['portalFolders'] });
      toast.success('Fil slettet');
    },
  });

  const submitMut = useMutation({
    mutationFn: (file: File) => submitAssignment(folder.id, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mySubmissions'] });
      toast.success('Oppgave levert!');
    },
  });

  const reviewMut = useMutation({
    mutationFn: ({ id, status, feedback }: { id: number; status: string; feedback?: string }) =>
      reviewSubmission(id, status, feedback),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portalSubmissions', folder.id] });
      toast.success('Vurdering lagret');
    },
  });

  const handleSaveEdit = async () => {
    setEditLoading(true);
    try {
      await updateFolder(folder.id, {
        name: editName,
        description: editDesc,
        assignment: editAssignment,
        deadline: editDeadline || undefined,
      });
      toast.success('Mappe oppdatert');
      setEditing(false);
      qc.invalidateQueries({ queryKey: ['portalFolders'] });
    } catch {
      toast.error('Feil ved oppdatering');
    } finally {
      setEditLoading(false);
    }
  };

  const handleSendFeedback = (submissionId: number, currentStatus: string) => {
    if (!feedbackText.trim()) return;
    reviewMut.mutate({ id: submissionId, status: currentStatus, feedback: feedbackText });
    setFeedbackFor(null);
    setFeedbackText('');
  };

  const statusIcon = (s: string) => {
    if (s === 'APPROVED') return <CheckCircle2 size={14} className="text-green-500" />;
    if (s === 'REJECTED') return <XCircle size={14} className="text-red-500" />;
    return <Clock size={14} className="text-amber-400" />;
  };
  const statusLabel = (s: string) => {
    if (s === 'APPROVED') return 'Godkjent';
    if (s === 'REJECTED') return 'Ikke godkjent';
    return 'Venter';
  };

  return (
    <div className="bg-bg-secondary border border-border rounded-xl overflow-hidden">
      {/* Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full px-5 py-3.5 flex items-center gap-3 hover:bg-bg-hover/30 transition-colors text-left"
      >
        {expanded ? (
          <ChevronDown size={16} className="text-text-muted shrink-0" />
        ) : (
          <ChevronRight size={16} className="text-text-muted shrink-0" />
        )}
        {folder.assignment ? (
          <FileText size={18} className="text-purple-400 shrink-0" />
        ) : (
          <FolderOpen size={18} className="text-accent shrink-0" />
        )}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-text-primary truncate">{folder.name}</span>
            {folder.assignment && (
              <span className="text-[10px] px-1.5 py-0.5 bg-purple-500/15 text-purple-400 rounded-full font-semibold">
                Oppgave
              </span>
            )}
          </div>
          <div className="flex items-center gap-2 text-[11px] text-text-muted mt-0.5">
            {folder.programName && <span>{folder.programName}</span>}
            {folder.subjectName && (
              <>
                <span>·</span>
                <span>{folder.subjectName}</span>
              </>
            )}
            <span>·</span>
            <span>{folder.fileCount} filer</span>
            {folder.assignment && (
              <>
                <span>·</span>
                <span>{folder.submissionCount} leveringer</span>
              </>
            )}
            {folder.assignment &&
              folder.deadline &&
              (() => {
                const dl = new Date(folder.deadline.replace('T', ' '));
                const isOverdue = dl.getTime() < Date.now();
                return (
                  <>
                    <span>·</span>
                    <span
                      className={`flex items-center gap-0.5 ${isOverdue ? 'text-red-500 font-semibold' : 'text-amber-400'}`}
                    >
                      {isOverdue ? <AlertTriangle size={10} /> : <CalendarClock size={10} />}
                      Frist: {dl.toLocaleDateString('nb-NO')}{' '}
                      {dl.toLocaleTimeString('nb-NO', { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </>
                );
              })()}
          </div>
        </div>
        {isTeacher && (
          <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
            <button
              onClick={() => {
                setEditing(!editing);
                setExpanded(true);
              }}
              className="p-1.5 rounded-lg hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"
              title="Rediger mappe"
            >
              <Edit3 size={14} />
            </button>
            <button
              onClick={onDelete}
              className="p-1.5 rounded-lg hover:bg-bg-hover text-text-muted hover:text-danger transition-colors"
              title="Slett mappe"
            >
              <Trash2 size={14} />
            </button>
          </div>
        )}
      </button>

      {/* Expanded content */}
      {expanded && (
        <div className="px-5 pb-4 border-t border-border/50 space-y-3 pt-3">
          {/* Inline edit form for teachers */}
          {editing && isTeacher && (
            <div className="bg-bg-primary border border-accent/20 rounded-lg p-4 space-y-3">
              <h4 className="text-xs font-semibold text-accent uppercase tracking-wider">
                ✏️ Rediger mappe
              </h4>
              <input
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                placeholder="Mappenavn..."
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
              />
              <textarea
                value={editDesc}
                onChange={(e) => setEditDesc(e.target.value)}
                placeholder="Beskrivelse..."
                rows={2}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus resize-none"
              />
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={editAssignment}
                  onChange={(e) => setEditAssignment(e.target.checked)}
                  className="w-4 h-4 rounded border-border accent-accent"
                />
                <span className="text-sm text-text-secondary">📋 Oppgavemappe</span>
              </label>
              {editAssignment && (
                <div className="flex items-center gap-2">
                  <CalendarClock size={16} className="text-text-muted" />
                  <label className="text-sm text-text-secondary">Tidsfrist:</label>
                  <input
                    type="datetime-local"
                    value={editDeadline}
                    onChange={(e) => setEditDeadline(e.target.value)}
                    className="px-3 py-1.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
                  />
                  {editDeadline && (
                    <button
                      onClick={() => setEditDeadline('')}
                      className="text-xs text-red-400 hover:text-red-500"
                    >
                      Fjern frist
                    </button>
                  )}
                </div>
              )}
              <div className="flex justify-end gap-2">
                <button
                  onClick={() => setEditing(false)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
                >
                  Avbryt
                </button>
                <button
                  onClick={handleSaveEdit}
                  disabled={editLoading}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-accent hover:bg-accent-hover text-white font-medium transition-colors disabled:opacity-50"
                >
                  <Save size={12} /> {editLoading ? 'Lagrer...' : 'Lagre endringer'}
                </button>
              </div>
            </div>
          )}

          {folder.description && !editing && (
            <p className="text-xs text-text-muted italic">{folder.description}</p>
          )}

          {/* Files */}
          {files.map((f) => (
            <div key={f.id} className="flex items-center gap-3 py-1.5 group">
              <FileIcon mimeType={f.mimeType} />
              <div className="flex-1 min-w-0">
                <p className="text-sm text-text-primary truncate">{f.fileName}</p>
                <p className="text-[10px] text-text-muted">{fmtSize(f.fileSize)}</p>
              </div>
              <a
                href={getPortalFileUrl(f.id)}
                target="_blank"
                rel="noopener noreferrer"
                className="p-1.5 rounded-lg hover:bg-bg-hover text-accent transition-colors"
                title="Last ned"
              >
                <Download size={14} />
              </a>
              {isTeacher && (
                <button
                  onClick={() => deleteFileMut.mutate(f.id)}
                  className="p-1.5 rounded-lg hover:bg-bg-hover text-text-muted hover:text-danger opacity-0 group-hover:opacity-100 transition-all"
                >
                  <Trash2 size={13} />
                </button>
              )}
            </div>
          ))}

          {/* Teacher: upload file */}
          {isTeacher && (
            <label className="flex items-center gap-2 px-3 py-2 border border-dashed border-accent/30 rounded-lg cursor-pointer hover:bg-accent/5 transition-colors">
              <Upload size={14} className="text-accent" />
              <span className="text-xs text-accent font-medium">Last opp fil</span>
              <input
                type="file"
                className="hidden"
                onChange={(e) => e.target.files?.[0] && uploadFileMut.mutate(e.target.files[0])}
              />
            </label>
          )}

          {/* Assignment: Student submissions */}
          {folder.assignment && !isTeacher && (
            <div className="border-t border-border/30 pt-3 space-y-2">
              <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wider">
                Min levering
              </h4>
              {folder.deadline &&
                (() => {
                  const dl = new Date(folder.deadline.replace('T', ' '));
                  const isOverdue = dl.getTime() < Date.now();
                  return isOverdue && mySub.length === 0 ? (
                    <div className="flex items-center gap-2 px-3 py-2 bg-red-500/10 border border-red-500/20 rounded-lg">
                      <AlertTriangle size={14} className="text-red-500" />
                      <span className="text-xs text-red-500 font-medium">
                        Tidsfristen har gått ut — Ikke godkjent
                      </span>
                    </div>
                  ) : !isOverdue ? (
                    <div className="flex items-center gap-1.5 text-xs text-amber-400">
                      <CalendarClock size={12} />
                      <span>
                        Frist: {dl.toLocaleDateString('nb-NO')} kl.{' '}
                        {dl.toLocaleTimeString('nb-NO', { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                  ) : null;
                })()}
              {mySub.length > 0 ? (
                mySub.map((s) => (
                  <div key={s.id} className="flex items-center gap-2 text-sm">
                    {statusIcon(s.status)}
                    <span className="text-text-primary">{s.fileName}</span>
                    <span
                      className={`text-xs font-semibold ${s.status === 'APPROVED' ? 'text-green-500' : s.status === 'REJECTED' ? 'text-red-500' : 'text-amber-400'}`}
                    >
                      {statusLabel(s.status)}
                    </span>
                    {s.feedback && (
                      <span className="text-xs text-text-muted ml-2 italic">"{s.feedback}"</span>
                    )}
                  </div>
                ))
              ) : (
                <p className="text-xs text-text-muted">Ingen levering ennå</p>
              )}
              {(() => {
                const deadlinePassed =
                  folder.deadline &&
                  new Date(folder.deadline.replace('T', ' ')).getTime() < Date.now();
                return !deadlinePassed ? (
                  <label className="flex items-center gap-2 px-3 py-2 border border-dashed border-purple-400/30 rounded-lg cursor-pointer hover:bg-purple-400/5 transition-colors">
                    <Upload size={14} className="text-purple-400" />
                    <span className="text-xs text-purple-400 font-medium">
                      {mySub.length > 0 ? 'Last opp ny besvarelse' : 'Lever oppgave'}
                    </span>
                    <input
                      type="file"
                      className="hidden"
                      onChange={(e) => e.target.files?.[0] && submitMut.mutate(e.target.files[0])}
                    />
                  </label>
                ) : null;
              })()}
            </div>
          )}

          {/* Teacher: view submissions */}
          {folder.assignment && isTeacher && (
            <div className="border-t border-border/30 pt-3 space-y-2">
              <h4 className="text-xs font-semibold text-text-muted uppercase tracking-wider">
                Innleveringer ({submissions.length})
              </h4>
              {submissions.length === 0 && (
                <p className="text-xs text-text-muted">Ingen leveringer ennå</p>
              )}
              {submissions.map((s) => (
                <div key={s.id} className="border border-border/30 rounded-lg p-3 space-y-2">
                  <div className="flex items-center gap-3">
                    {statusIcon(s.status)}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-text-primary font-medium">{s.studentName}</p>
                      <p className="text-[10px] text-text-muted">
                        {s.fileName} · {fmtSize(s.fileSize)} · {timeAgo(s.submittedAt)}
                      </p>
                    </div>
                    <a
                      href={getSubmissionFileUrl(s.id)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-1.5 rounded-lg hover:bg-bg-hover text-accent transition-colors"
                      title="Åpne besvarelse"
                    >
                      <Download size={14} />
                    </a>
                    <select
                      value={s.status}
                      onChange={(e) =>
                        reviewMut.mutate({
                          id: s.id,
                          status: e.target.value,
                          feedback: s.feedback || undefined,
                        })
                      }
                      className={`px-2 py-1 rounded-lg text-xs font-semibold border ${
                        s.status === 'APPROVED'
                          ? 'bg-green-500/10 border-green-500/30 text-green-500'
                          : s.status === 'REJECTED'
                            ? 'bg-red-500/10 border-red-500/30 text-red-500'
                            : 'bg-amber-400/10 border-amber-400/30 text-amber-400'
                      }`}
                    >
                      <option value="PENDING">Venter</option>
                      <option value="APPROVED">Godkjent</option>
                      <option value="REJECTED">Ikke godkjent</option>
                    </select>
                  </div>
                  {/* Existing feedback */}
                  {s.feedback && (
                    <div className="ml-7 px-3 py-1.5 bg-bg-primary border-l-2 border-accent/40 rounded-r-lg">
                      <p className="text-xs text-text-secondary">
                        <span className="font-semibold text-accent">Kommentar:</span> {s.feedback}
                      </p>
                    </div>
                  )}
                  {/* Feedback input */}
                  {feedbackFor === s.id ? (
                    <div className="ml-7 flex gap-2">
                      <input
                        value={feedbackText}
                        onChange={(e) => setFeedbackText(e.target.value)}
                        placeholder="Skriv kommentar til eleven..."
                        className="flex-1 px-3 py-1.5 bg-bg-input border border-border rounded-lg text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus"
                        onKeyDown={(e) => e.key === 'Enter' && handleSendFeedback(s.id, s.status)}
                      />
                      <button
                        onClick={() => handleSendFeedback(s.id, s.status)}
                        className="px-3 py-1.5 bg-accent hover:bg-accent-hover text-white text-xs rounded-lg font-medium transition-colors"
                      >
                        <Send size={12} />
                      </button>
                      <button
                        onClick={() => {
                          setFeedbackFor(null);
                          setFeedbackText('');
                        }}
                        className="px-2 py-1.5 text-xs text-text-muted hover:text-text-primary transition-colors"
                      >
                        <X size={12} />
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => {
                        setFeedbackFor(s.id);
                        setFeedbackText(s.feedback || '');
                      }}
                      className="ml-7 flex items-center gap-1 text-[11px] text-accent hover:text-accent-hover transition-colors"
                    >
                      <MessageCircle size={11} />{' '}
                      {s.feedback ? 'Rediger kommentar' : 'Legg til kommentar'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function FileIcon({ mimeType }: { mimeType: string }) {
  if (mimeType?.startsWith('image/'))
    return <ImageIcon size={16} className="text-blue-400 shrink-0" />;
  return <FileText size={16} className="text-text-muted shrink-0" />;
}
