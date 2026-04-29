import { useState, useMemo } from 'react';
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query';
import { getAttendance, getStudentAttendance, createAttendance, updateAttendance, deleteAttendance, getMyAbsenceStats, getStudentAbsenceStats } from '../api/attendance';
import { getUsers } from '../api/users';
import { getSubjects, getSubjectMembers } from '../api/subjects';
import { getPrograms, getProgramMembers } from '../api/programs';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { AutocompleteInput } from '../components/AutocompleteInput';
import { Plus, Pencil, Trash2, Search, X, AlertTriangle, CheckCircle2, XCircle, ShieldCheck, ChevronDown, ChevronRight, Users } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';
import type { Attendance, AttendanceRequest, AttendanceStatus, SubjectAbsenceStats, ProgramMembers, Subject } from '../types';

export function AttendancePage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Attendance | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Attendance | null>(null);
  const [expandedPrograms, setExpandedPrograms] = useState<Set<number>>(new Set());
  const [expandedStudents, setExpandedStudents] = useState<Set<string>>(new Set());
  const [expandedTrinns, setExpandedTrinns] = useState<Set<string>>(new Set());

  const isStudent = user?.role === 'STUDENT';
  const canWrite = user?.role === 'SUPER_ADMIN' || user?.role === 'INSTITUTION_ADMIN' || user?.role === 'TEACHER';

  const { data: records = [], isLoading } = useQuery({
    queryKey: ['attendance', user?.username],
    queryFn: () => isStudent ? getStudentAttendance(user!.username) : getAttendance(),
  });

  // Absence stats for current student
  const { data: myStats } = useQuery({
    queryKey: ['myAbsenceStats'],
    queryFn: getMyAbsenceStats,
    enabled: isStudent,
  });

  // Absence stats for all expanded students (teacher/admin)
  const expandedStudentList = useMemo(() => Array.from(expandedStudents), [expandedStudents]);
  const { data: allStudentStats = {} } = useQuery({
    queryKey: ['studentAbsenceStats', expandedStudentList.join(',')],
    queryFn: async () => {
      const results = await Promise.all(expandedStudentList.map(u => getStudentAbsenceStats(u)));
      const map: Record<string, SubjectAbsenceStats[]> = {};
      expandedStudentList.forEach((u, i) => { map[u] = results[i]; });
      return map;
    },
    enabled: canWrite && expandedStudentList.length > 0,
    staleTime: 10000,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteAttendance,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      queryClient.invalidateQueries({ queryKey: ['myAbsenceStats'] });
      queryClient.invalidateQueries({ queryKey: ['studentAbsenceStats'] });
      toast.success('Slettet');
      setDeleteTarget(null);
    },
    onError: () => toast.error('Kunne ikke slette'),
  });

  const excuseMutation = useMutation({
    mutationFn: ({ id, excused }: { id: number; excused: boolean }) =>
      updateAttendance(id, { studentUsername: '', subjectCode: '', status: 'Absent', excused }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      queryClient.invalidateQueries({ queryKey: ['myAbsenceStats'] });
      queryClient.invalidateQueries({ queryKey: ['studentAbsenceStats'] });
      toast.success('Oppdatert');
    },
  });

  const filtered = records.filter(r =>
    r.studentUsername.toLowerCase().includes(search.toLowerCase()) ||
    r.subjectCode.toLowerCase().includes(search.toLowerCase())
  );

  const { data: programs = [] } = useQuery({
    queryKey: ['programs'],
    queryFn: getPrograms,
    enabled: !isStudent,
  });

  // Teacher subject filtering: subjects API already returns only teacher's assigned subjects
  const { data: teacherSubjects = [] } = useQuery({
    queryKey: ['subjects'],
    queryFn: getSubjects,
    enabled: !isStudent,
  });
  const teacherSubjectCodes = useMemo(() => new Set(teacherSubjects.map(s => s.code)), [teacherSubjects]);

  // Fetch members for all programs at once
  const { data: programMembersMap = {} } = useQuery({
    queryKey: ['allProgramMembers', programs.map(p => p.id).join(',')],
    queryFn: async () => {
      const results = await Promise.all(programs.map(p => getProgramMembers(p.id)));
      const map: Record<number, ProgramMembers> = {};
      programs.forEach((p, i) => { map[p.id] = results[i]; });
      return map;
    },
    enabled: !isStudent && programs.length > 0,
    staleTime: 60000,
  });

  // Group attendance by student username
  const recordsByStudent = useMemo(() => {
    const map: Record<string, Attendance[]> = {};
    filtered.forEach(r => {
      if (!map[r.studentUsername]) map[r.studentUsername] = [];
      map[r.studentUsername].push(r);
    });
    return map;
  }, [filtered]);

  // Build subjectCode -> yearLevel map from programs
  const subjectYearLevelMap = useMemo(() => {
    const map: Record<string, string> = {};
    programs.forEach(p => {
      p.subjects?.forEach((ps: { code: string; yearLevel?: string }) => {
        if (ps.yearLevel && !map[ps.code.toUpperCase()]) {
          map[ps.code.toUpperCase()] = ps.yearLevel;
        }
      });
    });
    return map;
  }, [programs]);

  const toggleProgram = (id: number) => {
    setExpandedPrograms(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleStudentExpand = (username: string) => {
    setExpandedStudents(prev => {
      const next = new Set(prev);
      if (next.has(username)) next.delete(username); else next.add(username);
      return next;
    });
  };

  const toggleTrinn = (key: string) => {
    setExpandedTrinns(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  // Get unique students for teacher/admin view
  const uniqueStudents = useMemo(() => {
    const map = new Map<string, string>();
    records.forEach(r => { if (!map.has(r.studentUsername)) map.set(r.studentUsername, r.studentName || r.studentUsername); });
    return Array.from(map.entries()).map(([username, name]) => ({ username, name }));
  }, [records]);

  const statusBadge = (status: string, excused: boolean) => {
    if (excused) return <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold text-white bg-blue-500">Gyldig</span>;
    const colors: Record<string, string> = { Present: 'bg-badge-present', Absent: 'bg-badge-absent', Sick: 'bg-badge-sick', Late: 'bg-badge-late', Excused: 'bg-blue-500' };
    const labels: Record<string, string> = { Present: 'Tilstede', Absent: 'Fravær', Sick: 'Syk', Late: 'Sent', Excused: 'Gyldig' };
    return <span className={`px-2 py-0.5 rounded-full text-[11px] font-semibold text-white ${colors[status] || 'bg-bg-hover'}`}>{labels[status] || status}</span>;
  };

  if (isLoading) return <LoadingState message="Laster fravær..." />;

  return (
    <div>
      <PageHeader title={isStudent ? 'Mitt Fravær' : 'Fravær'} description={isStudent ? 'Dine fraværsoppføringer og grenser' : 'Fraværsregistrering og grenser'}
        action={canWrite ? (<button onClick={() => { setEditing(null); setShowForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"><Plus size={16} /> Registrer</button>) : undefined} />

      {/* Student: Show absence stats cards */}
      {isStudent && myStats && myStats.length > 0 && <AbsenceStatsCards stats={myStats} />}

      {/* Admin/Teacher: Class-based view */}
      {canWrite && !isStudent && programs.length > 0 ? (
        <div className="space-y-3">
          {[...programs].sort((a, b) => {
            const ma = a.name.match(/^(VG\d+|\d+)(.*)/), mb = b.name.match(/^(VG\d+|\d+)(.*)/);
            if (ma && mb) { const na = ma[1].startsWith('VG') ? parseInt(ma[1].slice(2)) + 100 : parseInt(ma[1]); const nb = mb[1].startsWith('VG') ? parseInt(mb[1].slice(2)) + 100 : parseInt(mb[1]); const diff = na - nb; if (diff !== 0) return diff; return ma[2].localeCompare(mb[2]); }
            if (ma) return -1; if (mb) return 1; return a.name.localeCompare(b.name);
          }).map(p => {
            const members = programMembersMap[p.id];
            const students = members?.students?.filter(s => !s.graduated) || [];
            const isExpanded = expandedPrograms.has(p.id);
            const studentCount = students.length;
            const programRecordCount = students.reduce((sum, s) => sum + (recordsByStudent[s.username]?.length || 0), 0);

            return (
              <div key={p.id} className="bg-bg-card border border-border rounded-xl overflow-hidden">
                <button onClick={() => toggleProgram(p.id)}
                  className="w-full flex items-center justify-between px-5 py-4 hover:bg-bg-hover/50 transition-colors text-left">
                  <div className="flex items-center gap-3">
                    {isExpanded ? <ChevronDown size={16} className="text-accent" /> : <ChevronRight size={16} className="text-text-muted" />}
                    <div className="flex items-center gap-2">
                      <Users size={16} className="text-accent" />
                      <span className="text-base font-semibold text-text-primary">{p.name}</span>
                    </div>
                    <span className="text-xs text-text-muted bg-bg-hover rounded-full px-2.5 py-0.5">{studentCount} elever</span>
                    {programRecordCount > 0 && (
                      <span className="text-xs text-accent bg-accent/10 rounded-full px-2.5 py-0.5">{programRecordCount} oppføringer</span>
                    )}
                  </div>
                </button>

                {isExpanded && (
                  <div className="border-t border-border">
                    {students.length === 0 ? (
                      <p className="text-sm text-text-muted italic px-5 py-4">Ingen elever i denne klassen</p>
                    ) : (
                      <div className="divide-y divide-border/50">
                        {students.map(s => {
                          const studentRecords = recordsByStudent[s.username] || [];
                          const isStudentExp = expandedStudents.has(s.username);
                          const absent = studentRecords.filter(r => r.status !== 'Present' && !r.excused).length;

                          return (
                            <div key={s.userId}>
                              <button onClick={() => toggleStudentExpand(s.username)}
                                className="w-full flex items-center justify-between px-5 py-3 hover:bg-bg-hover/30 transition-colors text-left">
                                <div className="flex items-center gap-3">
                                  {isStudentExp ? <ChevronDown size={14} className="text-accent" /> : <ChevronRight size={14} className="text-text-muted" />}
                                  <span className="text-sm font-medium text-text-primary">{s.firstName} {s.lastName}</span>
                                  <span className="text-xs font-mono text-text-muted">{s.username}</span>
                                  {s.yearLevel && <span className="text-xs text-accent bg-accent/10 rounded px-1.5 py-0.5">{s.yearLevel}. trinn</span>}
                                </div>
                                <div className="flex items-center gap-2">
                                  {absent > 0 && <span className="text-xs text-red-400 bg-red-400/10 rounded-full px-2 py-0.5">{absent} fravær</span>}
                                  <span className="text-xs text-text-muted">
                                    {studentRecords.length > 0 ? `${studentRecords.length} oppføring${studentRecords.length !== 1 ? 'er' : ''}` : 'Ingen oppføringer'}
                                  </span>
                                </div>
                              </button>

                              {isStudentExp && (() => {
                                // Get student's current yearLevel
                                const studentYearLevel = s.yearLevel;
                                const studentYearNum = yearLevelToNumber(studentYearLevel);

                                // Get filtered stats for this student (only teacher's subjects)
                                const statsForStudent = (allStudentStats[s.username] || [])
                                  .filter(st => teacherSubjectCodes.has(st.subjectCode))
                                  .filter(st => {
                                    // Only show stats up to student's current yearLevel
                                    if (!studentYearLevel) return true; // no level info = show all
                                    const statYearNum = yearLevelToNumber(st.yearLevel);
                                    if (statYearNum === 0) return true; // no yearLevel = always show
                                    return statYearNum <= studentYearNum;
                                  });

                                // Group records by yearLevel
                                const trinnGroups: Record<string, Attendance[]> = {};
                                studentRecords.forEach(r => {
                                  const yl = subjectYearLevelMap[r.subjectCode.toUpperCase()] || '';
                                  const trinn = yl || 'Generelt';
                                  if (studentYearLevel) {
                                    const recYearNum = yearLevelToNumber(yl);
                                    if (recYearNum > 0 && recYearNum > studentYearNum) return; // skip future levels
                                  }
                                  if (!trinnGroups[trinn]) trinnGroups[trinn] = [];
                                  trinnGroups[trinn].push(r);
                                });
                                // Also add yearLevels from stats that have no records yet
                                statsForStudent.forEach(st => {
                                  const trinn = st.yearLevel || 'Generelt';
                                  if (!trinnGroups[trinn]) trinnGroups[trinn] = [];
                                });
                                const sortedTrinns = Object.keys(trinnGroups).sort((a, b) => {
                                  return yearLevelToNumber(a) - yearLevelToNumber(b);
                                });

                                return (
                                  <div className="px-5 pb-4 space-y-2">
                                    {sortedTrinns.length === 0 ? (
                                      <p className="text-sm text-text-muted italic">Ingen fraværsoppføringer registrert</p>
                                    ) : sortedTrinns.map(trinn => {
                                      const trinnKey = `${s.username}::${trinn}`;
                                      const isTrinnExp = expandedTrinns.has(trinnKey);
                                      const trinnRecords = trinnGroups[trinn];
                                      const trinnAbsent = trinnRecords.filter(r => r.status !== 'Present' && !r.excused).length;
                                      // Filter stats for this trinn
                                      const trinnStats = statsForStudent.filter(st => {
                                        const stTrinn = st.yearLevel || 'Generelt';
                                        return stTrinn === trinn;
                                      });

                                      return (
                                        <div key={trinnKey} className="bg-bg-primary/30 border border-border/40 rounded-lg overflow-hidden">
                                          <button onClick={() => toggleTrinn(trinnKey)}
                                            className="w-full flex items-center justify-between px-4 py-2.5 hover:bg-bg-hover/30 transition-colors text-left">
                                            <div className="flex items-center gap-2">
                                              {isTrinnExp ? <ChevronDown size={12} className="text-accent" /> : <ChevronRight size={12} className="text-text-muted" />}
                                              <span className="text-sm font-semibold text-text-primary">
                                                {formatYearLevelLabel(trinn)}
                                              </span>
                                            </div>
                                            <div className="flex items-center gap-2">
                                              {trinnAbsent > 0 && <span className="text-xs text-red-400 bg-red-400/10 rounded-full px-2 py-0.5">{trinnAbsent} fravær</span>}
                                              <span className="text-xs text-text-muted">{trinnRecords.length} oppføringer</span>
                                            </div>
                                          </button>

                                          {isTrinnExp && (
                                            <div className="border-t border-border/30 px-4 pb-3 pt-2 space-y-3">
                                              {trinnStats.length > 0 && <AbsenceStatsCards stats={trinnStats} />}

                                              {trinnRecords.length > 0 ? (
                                                <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
                                                  <table className="w-full text-sm">
                                                    <thead><tr className="border-b border-border bg-bg-primary/50 text-text-secondary text-left">
                                                      <th className="px-4 py-3 font-medium">Fag</th>
                                                      <th className="px-4 py-3 font-medium">Dato</th>
                                                      <th className="px-4 py-3 font-medium">Status</th>
                                                      <th className="px-4 py-3 font-medium">Notat</th>
                                                      {canWrite && <th className="px-4 py-3 font-medium w-32">Handlinger</th>}
                                                    </tr></thead>
                                                    <tbody>
                                                      {trinnRecords.map(r => (
                                                        <tr key={r.id} className="border-b border-border/50 hover:bg-bg-hover/50 transition-colors">
                                                          <td className="px-4 py-3 font-mono text-accent">{r.subjectCode}</td>
                                                          <td className="px-4 py-3 text-text-secondary">{r.date}</td>
                                                          <td className="px-4 py-3">{statusBadge(r.status, r.excused)}</td>
                                                          <td className="px-4 py-3 text-text-secondary max-w-xs truncate">{r.note || '—'}</td>
                                                          {canWrite && (
                                                            <td className="px-4 py-3"><div className="flex gap-1">
                                                              {r.status !== 'Present' && !r.excused && (
                                                                <button onClick={() => excuseMutation.mutate({ id: r.id, excused: true })}
                                                                  className="p-1.5 rounded-md hover:bg-blue-500/10 text-text-muted hover:text-blue-400 transition-colors" title="Marker gyldig">
                                                                  <ShieldCheck size={14} />
                                                                </button>
                                                              )}
                                                              {r.excused && (
                                                                <button onClick={() => excuseMutation.mutate({ id: r.id, excused: false })}
                                                                  className="p-1.5 rounded-md hover:bg-orange-500/10 text-blue-400 hover:text-orange-400 transition-colors" title="Fjern gyldig">
                                                                  <XCircle size={14} />
                                                                </button>
                                                              )}
                                                              <button onClick={() => { setEditing(r); setShowForm(true); }} className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                                                              <button onClick={() => setDeleteTarget(r)} className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                                                            </div></td>
                                                          )}
                                                        </tr>
                                                      ))}
                                                    </tbody>
                                                  </table>
                                                </div>
                                              ) : (
                                                <p className="text-sm text-text-muted italic">Ingen fraværsoppføringer for dette trinnet</p>
                                              )}
                                            </div>
                                          )}
                                        </div>
                                      );
                                    })}
                                  </div>
                                );
                              })()}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        /* Flat table view (fallback or student) */
        <>
          {filtered.length === 0 ? <EmptyState title="Ingen oppføringer funnet" /> : (
            <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead><tr className="border-b border-border bg-bg-primary/50 text-text-secondary text-left">
                  {!isStudent && <th className="px-4 py-3 font-medium">Elev</th>}
                  <th className="px-4 py-3 font-medium">Fag</th>
                  <th className="px-4 py-3 font-medium">Dato</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Notat</th>
                  {canWrite && <th className="px-4 py-3 font-medium w-32">Handlinger</th>}
                </tr></thead>
                <tbody>
                  {filtered.map(r => (
                    <tr key={r.id} className="border-b border-border/50 hover:bg-bg-hover/50 transition-colors">
                      {!isStudent && <td className="px-4 py-3 text-text-primary">{r.studentName || r.studentUsername}</td>}
                      <td className="px-4 py-3 font-mono text-accent">{r.subjectCode}</td>
                      <td className="px-4 py-3 text-text-secondary">{r.date}</td>
                      <td className="px-4 py-3">{statusBadge(r.status, r.excused)}</td>
                      <td className="px-4 py-3 text-text-secondary max-w-xs truncate">{r.note || '—'}</td>
                      {canWrite && (
                        <td className="px-4 py-3"><div className="flex gap-1">
                          {r.status !== 'Present' && !r.excused && (
                            <button onClick={() => excuseMutation.mutate({ id: r.id, excused: true })}
                              className="p-1.5 rounded-md hover:bg-blue-500/10 text-text-muted hover:text-blue-400 transition-colors" title="Marker gyldig">
                              <ShieldCheck size={14} />
                            </button>
                          )}
                          {r.excused && (
                            <button onClick={() => excuseMutation.mutate({ id: r.id, excused: false })}
                              className="p-1.5 rounded-md hover:bg-orange-500/10 text-blue-400 hover:text-orange-400 transition-colors" title="Fjern gyldig">
                              <XCircle size={14} />
                            </button>
                          )}
                          <button onClick={() => { setEditing(r); setShowForm(true); }} className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                          <button onClick={() => setDeleteTarget(r)} className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                        </div></td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {showForm && <AttendanceFormModal record={editing} onClose={() => { setShowForm(false); setEditing(null); }}
        onSaved={() => { setShowForm(false); setEditing(null); queryClient.invalidateQueries({ queryKey: ['attendance'] }); queryClient.invalidateQueries({ queryKey: ['myAbsenceStats'] }); queryClient.invalidateQueries({ queryKey: ['studentAbsenceStats'] }); }} />}

      <ConfirmDialog open={!!deleteTarget} title="Slett oppføring" message="Er du sikker?" onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)} loading={deleteMutation.isPending} />
    </div>
  );
}
// ── Year Level Utilities ─────────────────────────────────────────────────────

/** Convert a yearLevel string to a sortable number for comparison. */
function yearLevelToNumber(yearLevel: string | null | undefined): number {
  if (!yearLevel || yearLevel === 'Generelt') return 0;
  // VGS: VG1→1, VG2→2, VG3→3
  const vgMatch = yearLevel.match(/^VG(\d+)$/i);
  if (vgMatch) return parseInt(vgMatch[1]);
  // Ungdomsskole: 8→8, 9→9, 10→10
  const numMatch = yearLevel.match(/^(\d+)$/);
  if (numMatch) return parseInt(numMatch[1]);
  // Universitet/Fagskole: BACHELOR_1→1, MASTER_4→4, FAGSKOLE_1→1
  const uniMatch = yearLevel.match(/_(\d+)$/);
  if (uniMatch) return parseInt(uniMatch[1]);
  return 0;
}

/** Format a yearLevel string to a human-readable label. */
function formatYearLevelLabel(yearLevel: string): string {
  if (!yearLevel || yearLevel === 'Generelt') return 'Generelt';
  // VGS: VG1, VG2, VG3
  const vgMatch = yearLevel.match(/^VG(\d+)$/i);
  if (vgMatch) return `VG${vgMatch[1]}`;
  // Ungdomsskole: 8→8. trinn, 9→9. trinn
  const numMatch = yearLevel.match(/^(\d+)$/);
  if (numMatch) return `${numMatch[1]}. trinn`;
  // Universitet: BACHELOR_1→Bachelor 1. år, MASTER_4→Master 4. år
  const bachelorMatch = yearLevel.match(/^BACHELOR_(\d+)$/i);
  if (bachelorMatch) return `Bachelor ${bachelorMatch[1]}. år`;
  const masterMatch = yearLevel.match(/^MASTER_(\d+)$/i);
  if (masterMatch) return `Master ${masterMatch[1]}. år`;
  // Fagskole: FAGSKOLE_1→Fagskole 1. år
  const fagMatch = yearLevel.match(/^FAGSKOLE_(\d+)$/i);
  if (fagMatch) return `Fagskole ${fagMatch[1]}. år`;
  return yearLevel;
}

// ── Absence Stats Cards ──────────────────────────────────────────────────────

const LEVEL_LABELS: Record<string, string> = {
  UNGDOMSSKOLE: 'Ungdomsskole',
  VGS: 'Videregående',
  FAGSKOLE: 'Fagskole',
  UNIVERSITET: 'Universitet / Høyskole',
};

const LEVEL_ORDER = ['UNGDOMSSKOLE', 'VGS', 'FAGSKOLE', 'UNIVERSITET'];

function AbsenceStatsCards({ stats }: { stats: SubjectAbsenceStats[] }) {
  if (stats.length === 0) return null;

  // Group by institutionLevel + institutionName, then by yearLevel
  const grouped = useMemo(() => {
    const instMap = new Map<string, { level: string; name: string; yearGroups: Map<string, SubjectAbsenceStats[]> }>();
    for (const s of stats) {
      const instKey = `${s.institutionLevel}::${s.institutionName}`;
      if (!instMap.has(instKey)) instMap.set(instKey, { level: s.institutionLevel, name: s.institutionName, yearGroups: new Map() });
      const inst = instMap.get(instKey)!;
      const yl = s.yearLevel || 'Generelt';
      if (!inst.yearGroups.has(yl)) inst.yearGroups.set(yl, []);
      inst.yearGroups.get(yl)!.push(s);
    }
    // Sort institutions by level order
    const sorted = Array.from(instMap.values()).sort((a, b) =>
      (LEVEL_ORDER.indexOf(a.level) ?? 99) - (LEVEL_ORDER.indexOf(b.level) ?? 99)
    );
    return sorted;
  }, [stats]);

  return (
    <div className="mb-6 space-y-6">
      {grouped.map(group => {
        const sortedYearLevels = Array.from(group.yearGroups.keys()).sort((a, b) =>
          yearLevelToNumber(a) - yearLevelToNumber(b)
        );

        return (
          <div key={`${group.level}::${group.name}`}>
            {/* Institution header */}
            <div className="flex items-center gap-3 mb-3">
              <div className="flex items-center gap-2">
                <div className="w-1 h-6 rounded-full bg-accent" />
                <h3 className="text-sm font-bold text-text-primary">
                  {LEVEL_LABELS[group.level] || group.level}
                </h3>
              </div>
              <span className="text-xs text-text-muted">— {group.name}</span>
            </div>

            {/* Year level sub-groups */}
            <div className="space-y-4">
              {sortedYearLevels.map(yl => {
                const items = group.yearGroups.get(yl)!;
                return (
                  <div key={yl}>
                    {/* Year level sub-header (only show if there are multiple year levels or if it's not 'Generelt') */}
                    {(sortedYearLevels.length > 1 || yl !== 'Generelt') && (
                      <div className="flex items-center gap-2 mb-2 ml-3">
                        <div className="w-0.5 h-4 rounded-full bg-accent/40" />
                        <span className="text-xs font-semibold text-accent uppercase tracking-wide">
                          {formatYearLevelLabel(yl)}
                        </span>
                        <span className="text-[10px] text-text-muted">({items.length} fag)</span>
                      </div>
                    )}

                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                      {items.map(s => (
                        <AbsenceBarometerCard key={s.subjectCode} stat={s} />
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/** Individual barometer card for a single subject. */
function AbsenceBarometerCard({ stat: s }: { stat: SubjectAbsenceStats }) {
  const hasLimit = s.maxAbsencePercent != null && s.status !== 'NO_LIMIT';
  const limit = s.maxAbsencePercent ?? 100;

  // Attendance = 100 - absence
  const attendancePct = Math.max(0, 100 - s.absencePercent);
  // Minimum attendance required = 100 - max absence allowed
  const minAttendance = hasLimit ? 100 - limit : 0;

  const barColor = s.status === 'EXCEEDED' ? 'bg-red-500' : s.status === 'WARNING' ? 'bg-yellow-500' : 'bg-green-500';
  const borderColor = s.status === 'EXCEEDED' ? 'border-red-500/30' : s.status === 'WARNING' ? 'border-yellow-500/30' : 'border-border/50';
  const bgColor = s.status === 'EXCEEDED' ? 'bg-red-500/5' : s.status === 'WARNING' ? 'bg-yellow-500/5' : 'bg-bg-card';

  const distanceText = (() => {
    if (!hasLimit) return null;
    const diff = s.absencePercent - limit;
    if (diff > 0) return <span className="text-red-400 font-semibold">{diff.toFixed(1)}% over fraværsgrensen</span>;
    if (Math.abs(diff) < 1) return <span className="text-yellow-400 font-semibold">På grensen</span>;
    return <span className="text-green-400">{Math.abs(diff).toFixed(1)}% til fraværsgrensen</span>;
  })();

  const statusBadge = (() => {
    if (s.status === 'EXCEEDED') return (
      <span className="flex items-center gap-1 text-xs font-bold text-red-400 bg-red-400/10 px-2 py-0.5 rounded-full">
        <XCircle size={12} /> Ikke vurdert
      </span>
    );
    if (s.status === 'WARNING') return (
      <span className="flex items-center gap-1 text-xs font-bold text-yellow-400 bg-yellow-400/10 px-2 py-0.5 rounded-full">
        <AlertTriangle size={12} /> Advarsel
      </span>
    );
    if (s.status === 'NO_LIMIT') return (
      <span className="text-xs text-text-muted italic">Ingen grense</span>
    );
    return (
      <span className="flex items-center gap-1 text-xs font-bold text-green-400 bg-green-400/10 px-2 py-0.5 rounded-full">
        <CheckCircle2 size={12} /> OK
      </span>
    );
  })();

  return (
    <div className={`p-4 rounded-xl border ${borderColor} ${bgColor} transition-all hover:shadow-md`}>
      {/* Header */}
      <div className="flex items-start justify-between mb-3">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-text-primary truncate">{s.subjectName}</p>
          <p className="text-xs font-mono text-text-muted">{s.subjectCode}</p>
        </div>
        {statusBadge}
      </div>

      {/* Barometer — shows attendance from 100% down */}
      <div className="relative mb-2">
        {/* Scale labels */}
        <div className="flex justify-between text-[10px] text-text-muted mb-1">
          <span>0%</span>
          {hasLimit && <span className="text-red-400 font-semibold">Min {minAttendance}%</span>}
          <span>100%</span>
        </div>

        {/* Bar track */}
        <div className="relative h-4 bg-bg-hover rounded-full overflow-visible">
          {/* Fill — attendance bar (green/yellow/red filling from left) */}
          <div className={`absolute inset-y-0 left-0 rounded-full transition-all duration-500 ${barColor}`}
            style={{ width: `${attendancePct}%` }}>
            {/* Percentage label on the bar */}
            {attendancePct > 20 && (
              <span className="absolute right-1.5 top-1/2 -translate-y-1/2 text-[10px] font-bold text-white">
                {attendancePct.toFixed(1)}%
              </span>
            )}
          </div>

          {/* Red limit line (minimum attendance required) */}
          {hasLimit && minAttendance > 0 && (
            <div className="absolute top-[-4px] bottom-[-4px] w-0.5 bg-red-500 z-10"
              style={{ left: `${minAttendance}%` }}>
              <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 text-[9px] font-bold text-red-400 whitespace-nowrap">
                Krav
              </div>
            </div>
          )}

          {/* Percentage label outside bar if too narrow */}
          {attendancePct <= 20 && attendancePct < 100 && (
            <span className="absolute text-[10px] font-bold text-text-secondary"
              style={{ left: `${attendancePct + 2}%`, top: '50%', transform: 'translateY(-50%)' }}>
              {attendancePct.toFixed(1)}%
            </span>
          )}
        </div>
      </div>

      {/* Distance text */}
      <div className="text-xs mb-2">{distanceText}</div>

      {/* Stats row */}
      <div className="flex gap-3 text-[11px] text-text-muted">
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-green-500 inline-block" /> {s.attended} tilstede
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-red-500 inline-block" /> {s.absentUnexcused} ugyldig
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-blue-500 inline-block" /> {s.absentExcused} gyldig
        </span>
      </div>
    </div>
  );
}

// ── Form Modal ───────────────────────────────────────────────────────────────
function AttendanceFormModal({ record, onClose, onSaved }: { record: Attendance | null; onClose: () => void; onSaved: () => void }) {
  const { user } = useAuth();
  const isEditing = !!record;
  const [form, setForm] = useState<AttendanceRequest>({
    studentUsername: record?.studentUsername || '', status: record?.status || 'Present',
    subjectCode: record?.subjectCode || '', date: record?.date || new Date().toISOString().split('T')[0], note: record?.note || '',
    institutionId: record?.institutionId || user?.institutionId,
    excused: record?.excused || false,
  });
  const [selectedYearLevel, setSelectedYearLevel] = useState<string>('');
  const [selectedProgramId, setSelectedProgramId] = useState<number | ''>('');
  const [endDate, setEndDate] = useState<string>(form.date || new Date().toISOString().split('T')[0]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const instLevel = user?.institutionLevel || '';
  const needsProgramSelect = instLevel !== 'UNGDOMSSKOLE';

  const { data: users = [] } = useQuery({ queryKey: ['users', 'STUDENT'], queryFn: () => getUsers('STUDENT') });
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });
  const { data: allPrograms = [] } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });

  const memberQueries = useQueries({
    queries: subjects.map(s => ({
      queryKey: ['subjectMembers', s.code],
      queryFn: () => getSubjectMembers(s.code),
      staleTime: 30000,
    })),
  });

  const { data: allProgramMembers = {} } = useQuery({
    queryKey: ['allProgramMembers', allPrograms.map(p => p.id).join(',')],
    queryFn: async () => {
      const results = await Promise.all(allPrograms.map(p => getProgramMembers(p.id)));
      const map: Record<number, ProgramMembers> = {};
      allPrograms.forEach((p, i) => { map[p.id] = results[i]; });
      return map;
    },
    enabled: allPrograms.length > 0,
    staleTime: 60000,
  });

  // Map: studentUsername -> [subjectCodes] from direct + program membership
  const studentSubjectMap = useMemo(() => {
    const map: Record<string, Set<string>> = {};
    subjects.forEach((s, i) => {
      const members = memberQueries[i]?.data;
      if (!members) return;
      members.students.forEach(st => {
        if (!map[st.username]) map[st.username] = new Set();
        map[st.username].add(s.code);
      });
    });
    allPrograms.forEach(p => {
      const members = allProgramMembers[p.id];
      if (!members) return;
      members.students.filter(s => !s.graduated).forEach(st => {
        if (!map[st.username]) map[st.username] = new Set();
        p.subjects.forEach(sub => map[st.username].add(sub.code));
      });
    });
    const result: Record<string, string[]> = {};
    Object.entries(map).forEach(([k, v]) => { result[k] = [...v]; });
    return result;
  }, [subjects, memberQueries, allPrograms, allProgramMembers]);

  // Map: studentUsername -> yearLevel from program membership
  const studentYearLevelMap = useMemo(() => {
    const map: Record<string, string> = {};
    allPrograms.forEach(p => {
      const members = allProgramMembers[p.id];
      if (!members) return;
      members.students.filter(s => !s.graduated).forEach(st => {
        if (st.yearLevel) { map[st.username] = st.yearLevel; return; }
        const m = p.name.match(/^(VG\d+|\d+)/);
        if (m) map[st.username] = m[1];
      });
    });
    return map;
  }, [allPrograms, allProgramMembers]);

  // Year level dropdown options per institution type
  const yearLevelOptions = useMemo(() => {
    if (instLevel === 'UNGDOMSSKOLE') return [{ value: '8', label: '8. klasse' }, { value: '9', label: '9. klasse' }, { value: '10', label: '10. klasse' }];
    if (instLevel === 'VGS') return [{ value: 'VG1', label: 'VG1' }, { value: 'VG2', label: 'VG2' }, { value: 'VG3', label: 'VG3' }];
    if (instLevel === 'FAGSKOLE') return [{ value: '1', label: '1. året' }, { value: '2', label: '2. året' }];
    return [
      { value: 'BACHELOR_1', label: 'Bachelor 1' }, { value: 'BACHELOR_2', label: 'Bachelor 2' }, { value: 'BACHELOR_3', label: 'Bachelor 3' },
      { value: 'MASTER_1', label: 'Master 1' }, { value: 'MASTER_2', label: 'Master 2' },
    ];
  }, [instLevel]);

  // Programs matching the selected year level
  const programsForYear = useMemo(() => {
    if (!selectedYearLevel) return [];
    return allPrograms.filter(p => {
      const members = allProgramMembers[p.id];
      const memberMatch = members?.students?.some(s => !s.graduated && s.yearLevel === selectedYearLevel);
      if (memberMatch) return true;
      const subjectMatch = p.subjects?.some(s => s.yearLevel === selectedYearLevel);
      if (subjectMatch) return true;
      const m = p.name.match(/^(VG\d+|\d+)/);
      return !!(m && m[1] === selectedYearLevel);
    });
  }, [allPrograms, allProgramMembers, selectedYearLevel]);

  // Students filtered by selected program (VGS+) or yearLevel (ungdomsskole)
  const filteredStudentOptions = useMemo(() => {
    const all = users.map(u => ({ value: u.username, label: `${u.firstName} ${u.lastName}`, sublabel: u.username }));
    if (needsProgramSelect) {
      if (!selectedProgramId) return [];
      const members = allProgramMembers[selectedProgramId as number];
      if (!members) return [];
      const studentUsernames = new Set(
        members.students
          .filter(s => !s.graduated && (!selectedYearLevel || !s.yearLevel || s.yearLevel === selectedYearLevel))
          .map(s => s.username)
      );
      return all.filter(o => studentUsernames.has(o.value));
    } else {
      if (!selectedYearLevel) return all;
      return all.filter(o => studentYearLevelMap[o.value] === selectedYearLevel);
    }
  }, [users, selectedYearLevel, selectedProgramId, studentYearLevelMap, allProgramMembers, needsProgramSelect]);

  // Subject codes for selected program
  const programSubjectCodes = useMemo(() => {
    if (!needsProgramSelect || !selectedProgramId) return null;
    const program = allPrograms.find(p => p.id === selectedProgramId);
    if (!program) return null;
    const taggedForYear = selectedYearLevel
      ? program.subjects.filter(s => s.yearLevel === selectedYearLevel)
      : [];
    const pool = taggedForYear.length > 0 ? taggedForYear : program.subjects;
    return new Set(pool.map(s => s.code));
  }, [needsProgramSelect, selectedProgramId, allPrograms, selectedYearLevel]);

  // Filtered subjects: by student assignment + program
  const assignedCodes = form.studentUsername ? (studentSubjectMap[form.studentUsername] || []) : [];
  const filteredSubjects = useMemo(() => {
    let result = subjects;
    if (form.studentUsername && assignedCodes.length > 0) {
      result = result.filter(s => assignedCodes.includes(s.code));
    }
    if (programSubjectCodes) {
      result = result.filter(s => programSubjectCodes.has(s.code));
    }
    return result;
  }, [subjects, form.studentUsername, assignedCodes, programSubjectCodes]);

  const subjectOptions = filteredSubjects.map(s => ({ value: s.code, label: s.name, sublabel: s.code }));

  // ── Handlers ──

  const handleYearChange = (year: string) => {
    setSelectedYearLevel(year);
    setSelectedProgramId('');
    setForm({ ...form, studentUsername: '', subjectCode: '' });
  };

  const handleProgramChange = (programId: number | '') => {
    setSelectedProgramId(programId);
    setForm({ ...form, studentUsername: '', subjectCode: '' });
  };

  const handleStudentChange = (username: string) => {
    setForm({ ...form, studentUsername: username, subjectCode: '' });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true);
    try {
      if (isEditing) {
        await updateAttendance(record!.id, form);
        toast.success('Oppdatert');
      } else {
        // Generate weekdays between form.date and endDate
        const start = new Date(form.date || new Date().toISOString().split('T')[0]);
        const end = new Date(endDate);
        if (end < start) { setError('Til dato kan ikke være før fra dato'); setLoading(false); return; }

        const dates: string[] = [];
        const current = new Date(start);
        while (current <= end) {
          const day = current.getDay();
          if (day !== 0 && day !== 6) { // Skip weekends
            dates.push(current.toISOString().split('T')[0]);
          }
          current.setDate(current.getDate() + 1);
        }

        if (dates.length === 0) { setError('Ingen ukedager i valgt periode'); setLoading(false); return; }

        let created = 0;
        let skipped = 0;
        for (const d of dates) {
          try {
            await createAttendance({ ...form, date: d });
            created++;
          } catch (err: any) {
            // Skip duplicates (409 Conflict) but throw other errors
            if (err.response?.status === 409) { skipped++; }
            else throw err;
          }
        }

        if (skipped > 0) {
          toast.success(`${created} registrert, ${skipped} hoppes over (allerede registrert)`);
        } else {
          toast.success(`${created} ${created === 1 ? 'dag' : 'dager'} registrert`);
        }
      }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.error || 'Kunne ikke lagre'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold mb-4">{isEditing ? 'Rediger Fravær' : 'Registrer Fravær'}</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">

          {/* Step 1: Year level */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Trinn</label>
            <select value={selectedYearLevel} onChange={e => handleYearChange(e.target.value)} required
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
              <option value="">Velg trinn...</option>
              {yearLevelOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>

          {/* Step 2: Program/Line (VGS and higher only) */}
          {needsProgramSelect && selectedYearLevel && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">
                {instLevel === 'VGS' ? 'Studielinje' : 'Program'}
                {programsForYear.length === 0 && <span className="text-text-muted font-normal"> (ingen linjer på dette trinnet)</span>}
              </label>
              <select value={selectedProgramId || ''} onChange={e => handleProgramChange(e.target.value ? Number(e.target.value) : '')} required
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
                <option value="">Velg {instLevel === 'VGS' ? 'linje' : 'program'}...</option>
                {programsForYear.map(p => {
                  const members = allProgramMembers[p.id];
                  const count = members?.students?.filter(s => !s.graduated).length || 0;
                  return <option key={p.id} value={p.id}>{p.name} ({count} elever)</option>;
                })}
              </select>
            </div>
          )}

          {/* Step 3: Student */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Elev
              {needsProgramSelect && !selectedProgramId && selectedYearLevel && <span className="text-text-muted font-normal"> (velg linje først)</span>}
              {!needsProgramSelect && !selectedYearLevel && <span className="text-text-muted font-normal"> (velg trinn først)</span>}
              {((needsProgramSelect && selectedProgramId) || (!needsProgramSelect && selectedYearLevel)) && filteredStudentOptions.length === 0 && <span className="text-text-muted font-normal"> (ingen elever)</span>}
            </label>
            <AutocompleteInput
              value={form.studentUsername}
              onChange={handleStudentChange}
              options={(needsProgramSelect ? !!selectedProgramId : !!selectedYearLevel) ? filteredStudentOptions : []}
              placeholder={
                needsProgramSelect
                  ? (selectedProgramId ? "Søk etter elev..." : "Velg linje først...")
                  : (selectedYearLevel ? "Søk etter elev..." : "Velg trinn først...")
              }
              required
            />
          </div>

          {/* Step 4: Subject */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Fag {form.studentUsername && subjectOptions.length === 0 && <span className="text-text-muted font-normal">(ingen fag tildelt)</span>}
            </label>
            <AutocompleteInput value={form.subjectCode} onChange={v => setForm({...form, subjectCode: v})} options={subjectOptions} placeholder="Søk fag..." required />
          </div>

          {/* Step 5: Date range (or single date when editing) */}
          {isEditing ? (
            <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Dato</label>
              <input type="date" value={form.date} onChange={e => setForm({...form, date: e.target.value})} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" /></div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1.5">Fra dato</label>
                <input type="date" value={form.date} onChange={e => { setForm({...form, date: e.target.value}); if (e.target.value > endDate) setEndDate(e.target.value); }}
                  className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1.5">Til dato</label>
                <input type="date" value={endDate} min={form.date} onChange={e => setEndDate(e.target.value)}
                  className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" />
              </div>
            </div>
          )}

          {/* Step 6: Status */}
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Status</label>
            <select value={form.status} onChange={e => setForm({...form, status: e.target.value as AttendanceStatus})} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
              {(['Present', 'Absent', 'Sick', 'Late', 'Excused'] as const).map(s => <option key={s} value={s}>{s === 'Present' ? 'Tilstede' : s === 'Absent' ? 'Fravær' : s === 'Sick' ? 'Syk' : s === 'Late' ? 'Sent' : 'Gyldig fravær'}</option>)}
            </select></div>

          {/* Excused checkbox */}
          {form.status !== 'Present' && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.excused || false} onChange={e => setForm({...form, excused: e.target.checked})}
                className="w-4 h-4 rounded border-border" />
              <span className="text-text-secondary">Gyldig fravær (legeerklæring / dokumentasjon)</span>
            </label>
          )}

          {/* Step 7: Note */}
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Notat</label>
            <input value={form.note || ''} onChange={e => setForm({...form, note: e.target.value})} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" /></div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">{loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Registrer'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
