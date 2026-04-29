import { useState, useMemo } from 'react';
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query';
import { getGrades, getStudentGrades, createGrade, updateGrade, deleteGrade, getEducationHistory } from '../api/grades';
import type { EducationLevel } from '../api/grades';
import { getUsers } from '../api/users';
import { getSubjects, getSubjectMembers } from '../api/subjects';
import { getPrograms, getProgramMembers } from '../api/programs';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { AutocompleteInput } from '../components/AutocompleteInput';
import { Plus, Pencil, Trash2, Search, X, GraduationCap, FileSearch, ChevronDown, ChevronRight, Users } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';
import { useNavigate } from 'react-router-dom';
import type { Grade, GradeRequest, Subject, SubjectLevel, Program, ProgramMember, ProgramMembers } from '../types';

function isNumericLevel(level?: SubjectLevel) {
  return level === 'UNGDOMSSKOLE' || level === 'VGS';
}

function formatGradeValue(val: string, level?: SubjectLevel): string {
  if (!level) return val;
  if (isNumericLevel(level)) {
    const map: Record<string, string> = { A: '6', B: '5', C: '4', D: '3', E: '2', F: '1' };
    return map[val.toUpperCase()] ?? val;
  }
  return val;
}

const gradeColor = (value: string, level?: SubjectLevel) => {
  // IV (Ikke Vurdert) — blocked by absence
  if (value.toUpperCase() === 'IV') return 'text-red-500 bg-red-500/15 border border-red-500/30';
  if (level && isNumericLevel(level)) {
    const v = parseInt(value);
    if (v >= 5) return 'text-green-400 bg-green-400/10';
    if (v === 4) return 'text-yellow-400 bg-yellow-400/10';
    if (v === 3) return 'text-orange-400 bg-orange-400/10';
    if (v === 2) return 'text-red-400 bg-red-400/10';
    return 'text-red-500 bg-red-500/10';
  }
  const v = value.toUpperCase();
  if (v === 'A') return 'text-green-400 bg-green-400/10';
  if (v === 'B') return 'text-blue-400 bg-blue-400/10';
  if (v === 'C') return 'text-yellow-400 bg-yellow-400/10';
  if (v === 'D') return 'text-orange-400 bg-orange-400/10';
  if (v === 'E') return 'text-red-400 bg-red-400/10';
  if (v === 'F') return 'text-red-500 bg-red-500/10';
  return 'text-text-secondary bg-bg-hover';
};

function calculateAverage(grades: Grade[], level: SubjectLevel): string | null {
  if (grades.length === 0) return null;
  const isNumeric = isNumericLevel(level);

  if (isNumeric) {
    const valid = grades.map(g => parseInt(formatGradeValue(g.value, level))).filter(v => !isNaN(v));
    if (valid.length === 0) return null;
    const sum = valid.reduce((acc, v) => acc + v, 0);
    return (sum / valid.length).toFixed(2);
  } else {
    // Letter grades to numeric A=6, B=5, C=4, D=3, E=2, F=1
    const map: Record<string, number> = { A: 6, B: 5, C: 4, D: 3, E: 2, F: 1 };
    const valid = grades.map(g => map[formatGradeValue(g.value, level).toUpperCase()]).filter(v => v !== undefined);
    if (valid.length === 0) return null;

    const sum = valid.reduce((acc, v) => acc + v, 0);
    const avg = sum / valid.length;
    
    if (avg >= 5.5) return 'A';
    if (avg >= 4.5) return 'B';
    if (avg >= 3.5) return 'C';
    if (avg >= 2.5) return 'D';
    if (avg >= 1.5) return 'E';
    return 'F';
  }
}

/** Compute numeric average regardless of scale (for GPA display). */
function numericAverage(grades: Grade[], subjectLevelMap: Record<string, SubjectLevel>): number | null {
  if (grades.length === 0) return null;
  const letterToNum: Record<string, number> = { A: 6, B: 5, C: 4, D: 3, E: 2, F: 1 };
  let sum = 0, count = 0;
  for (const g of grades) {
    const level = subjectLevelMap[g.subject.toUpperCase()];
    const display = formatGradeValue(g.value, level);
    const num = parseFloat(display);
    if (!isNaN(num) && num >= 1 && num <= 6) { sum += num; count++; }
    else if (letterToNum[display.toUpperCase()] !== undefined) { sum += letterToNum[display.toUpperCase()]; count++; }
  }
  return count > 0 ? Math.round((sum / count) * 100) / 100 : null;
}
/** Convert yearLevel to a sortable number for proper ordering across institution types. */
function yearLevelSortNum(yearLevel: string | null | undefined): number {
  if (!yearLevel) return 999;
  // VGS: VG1→1, VG2→2, VG3→3
  const vgMatch = yearLevel.match(/^VG(\d+)$/i);
  if (vgMatch) return parseInt(vgMatch[1]);
  // Ungdomsskole: 8→8, 9→9, 10→10
  const numMatch = yearLevel.match(/^(\d+)$/);
  if (numMatch) return parseInt(numMatch[1]);
  // Universitet/Fagskole: BACHELOR_1→1, MASTER_4→4
  const uniMatch = yearLevel.match(/_(\d+)$/);
  if (uniMatch) return parseInt(uniMatch[1]);
  return 999;
}

/** Format a yearLevel string to a human-readable label. */
function formatYearLabel(yearLevel: string): string {
  if (!yearLevel) return '';
  const vgMatch = yearLevel.match(/^VG(\d+)$/i);
  if (vgMatch) return `VG${vgMatch[1]}`;
  const numMatch = yearLevel.match(/^(\d+)$/);
  if (numMatch) return `${numMatch[1]}. trinn`;
  const bachelorMatch = yearLevel.match(/^BACHELOR_(\d+)$/i);
  if (bachelorMatch) return `Bachelor ${bachelorMatch[1]}. år`;
  const masterMatch = yearLevel.match(/^MASTER_(\d+)$/i);
  if (masterMatch) return `Master ${masterMatch[1]}. år`;
  const fagMatch = yearLevel.match(/^FAGSKOLE_(\d+)$/i);
  if (fagMatch) return `Fagskole ${fagMatch[1]}. år`;
  return yearLevel;
}

import React from 'react';

class GradesErrorBoundary extends React.Component<{children: React.ReactNode}, {error: Error | null}> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  render() {
    if (this.state.error) return <div className="p-8 text-red-400"><h2 className="text-lg font-bold mb-2">Feil i karaktersiden</h2><pre className="text-sm bg-bg-card p-4 rounded-lg overflow-auto">{this.state.error.message}{'\n'}{this.state.error.stack}</pre></div>;
    return this.props.children;
  }
}

export function GradesPage() {
  return <GradesErrorBoundary><GradesPageInner /></GradesErrorBoundary>;
}

function GradesPageInner() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editingGrade, setEditingGrade] = useState<Grade | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Grade | null>(null);
  const [viewTab, setViewTab] = useState<'current' | 'history'>('current');
  const [expandedPrograms, setExpandedPrograms] = useState<Set<number>>(new Set());
  const [expandedStudents, setExpandedStudents] = useState<Set<string>>(new Set());
  const [retakeTarget, setRetakeTarget] = useState<Grade | null>(null);
  const [retakeValue, setRetakeValue] = useState('');
  const navigate = useNavigate();

  const isStudent = user?.role === 'STUDENT';
  const canWrite = user?.role === 'SUPER_ADMIN' || user?.role === 'INSTITUTION_ADMIN' || user?.role === 'TEACHER';

  const { data: grades = [], isLoading } = useQuery({
    queryKey: ['grades', user?.username],
    queryFn: () => isStudent ? getStudentGrades(user!.username) : getGrades(),
  });

  const { data: educationHistory } = useQuery({
    queryKey: ['educationHistory'],
    queryFn: getEducationHistory,
    enabled: isStudent,
  });

  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });

  const { data: programs = [] } = useQuery({
    queryKey: ['programs'],
    queryFn: getPrograms,
  });

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

  const subjectLevelMap = useMemo(() => {
    const map: Record<string, SubjectLevel> = {};
    subjects.forEach(s => { map[s.code.toUpperCase()] = s.level; });
    return map;
  }, [subjects]);

  const subjectYearMap = useMemo(() => {
    const map: Record<string, string> = {};
    // 1. Use explicit yearLevel from subjects API
    subjects.forEach(s => { if (s.yearLevel) map[s.code.toUpperCase()] = s.yearLevel; });
    // 2. Derive yearLevel from programs — use both subject's own yearLevel and program name
    programs.forEach(p => {
      const match = p.name.match(/^(VG\d+|\d+)/);
      const trinnFromName = match ? match[1] : null;
      p.subjects?.forEach((ps) => {
        const key = ps.code.toUpperCase();
        if (!map[key]) {
          if (ps.yearLevel) {
            map[key] = ps.yearLevel;
          } else if (trinnFromName) {
            map[key] = trinnFromName;
          }
        }
      });
    });
    // 3. Derive yearLevel from subject's own program field (e.g. "8A" → "8", "Klasse 10B" → "10")
    subjects.forEach(s => {
      const key = s.code.toUpperCase();
      if (!map[key] && s.program) {
        const m = s.program.match(/(\d+)/);
        if (m) map[key] = m[1];
      }
    });
    return map;
  }, [subjects, programs]);

  const deleteMutation = useMutation({
    mutationFn: deleteGrade,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['grades'] }); toast.success('Grade deleted'); setDeleteTarget(null); },
    onError: () => toast.error('Failed to delete grade'),
  });

  const retakeMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: string }) =>
      updateGrade(id, { studentUsername: '', subject: '', value, retake: true }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['grades'] });
      queryClient.invalidateQueries({ queryKey: ['educationHistory'] });
      toast.success('Privatistkarakter registrert');
      setRetakeTarget(null);
      setRetakeValue('');
    },
    onError: () => toast.error('Kunne ikke registrere privatistkarakter'),
  });

  // Build a set of known subject codes for quick lookup
  const knownSubjectCodes = useMemo(() => {
    return new Set(subjects.map(s => s.code.toUpperCase()));
  }, [subjects]);

  // Helper: get year level for a grade — prefer stored yearLevel on grade, then subjectYearMap
  const getGradeYearLevel = (g: Grade) => g.yearLevel || subjectYearMap[g.subject.toUpperCase()] || null;

  // For students: detect the current (highest) year level from their grades + subjects
  const currentYearLevel = useMemo(() => {
    if (!isStudent) return null;
    const yearLevels = new Set<string>();
    grades.forEach(g => {
      const yr = getGradeYearLevel(g);
      if (yr) yearLevels.add(yr);
    });
    subjects.forEach(s => {
      if (s.yearLevel) yearLevels.add(s.yearLevel);
    });
    if (yearLevels.size === 0) return null;
    return [...yearLevels].sort((a, b) => {
      const na = parseInt(a), nb = parseInt(b);
      if (!isNaN(na) && !isNaN(nb)) return nb - na;
      return b.localeCompare(a);
    })[0];
  }, [isStudent, grades, subjectYearMap, subjects]);

  // All grades matching search (used for admin/teacher views and history)
  const allFiltered = grades.filter(g =>
    g.studentUsername.toLowerCase().includes(search.toLowerCase()) ||
    g.subject.toLowerCase().includes(search.toLowerCase()) ||
    g.value.toLowerCase().includes(search.toLowerCase())
  );

  // For students on 'current' tab: filter to only their current year level
  const filtered = useMemo(() => {
    if (!isStudent || viewTab !== 'current' || !currentYearLevel) return allFiltered;
    return allFiltered.filter(g => {
      const yr = getGradeYearLevel(g);
      // If we know the year and it's not the current one, filter it out
      if (yr && yr !== currentYearLevel) return false;
      return true;
    });
  }, [allFiltered, isStudent, viewTab, currentYearLevel, subjectYearMap]);

  const groupedGradesForStudent = useMemo(() => {
    if (!isStudent) return {};
    const fallbackLevel = (user?.institutionLevel || 'UNGDOMSSKOLE') as SubjectLevel;
    const groups: Partial<Record<SubjectLevel, Grade[]>> = {};
    filtered.forEach(g => {
      const level = subjectLevelMap[g.subject.toUpperCase()] || fallbackLevel;
      if (!groups[level]) groups[level] = [];
      groups[level]!.push(g);
    });
    return groups;
  }, [filtered, subjectLevelMap, isStudent, user?.institutionLevel]);

  // Group grades by student username (must be before any early return to comply with Rules of Hooks)
  const gradesByStudent = useMemo(() => {
    const map: Record<string, Grade[]> = {};
    filtered.forEach(g => {
      if (!map[g.studentUsername]) map[g.studentUsername] = [];
      map[g.studentUsername].push(g);
    });
    return map;
  }, [filtered]);

  if (isLoading) return <LoadingState message="Loading grades..." />;

  const renderGradeTable = (items: Grade[], hideStudentCol = false) => {
    // Sort by year level then subject code
    const sorted = [...items].sort((a, b) => {
      const aYL = getGradeYearLevel(a) || subjectYearMap[a.subject.toUpperCase()] || '';
      const bYL = getGradeYearLevel(b) || subjectYearMap[b.subject.toUpperCase()] || '';
      const aNum = yearLevelSortNum(aYL);
      const bNum = yearLevelSortNum(bYL);
      if (aNum !== bNum) return aNum - bNum;
      return a.subject.localeCompare(b.subject);
    });

    // Group by year level for sub-headers
    const yearGroups: { yearLevel: string; grades: Grade[] }[] = [];
    let currentYL = '';
    for (const g of sorted) {
      const yl = getGradeYearLevel(g) || subjectYearMap[g.subject.toUpperCase()] || '';
      if (yl !== currentYL) {
        yearGroups.push({ yearLevel: yl, grades: [] });
        currentYL = yl;
      }
      yearGroups[yearGroups.length - 1].grades.push(g);
    }

    return (
      <div className="space-y-3">
        {yearGroups.map(({ yearLevel: yl, grades: grpGrades }) => (
          <div key={yl || 'none'}>
            {/* Year level sub-header if there are multiple groups */}
            {yearGroups.length > 1 && yl && (
              <div className="flex items-center gap-2 mb-1.5 ml-1">
                <div className="w-0.5 h-4 rounded-full bg-accent/40" />
                <span className="text-xs font-semibold text-accent uppercase tracking-wide">
                  {formatYearLabel(yl)}
                </span>
                <span className="text-[10px] text-text-muted">({grpGrades.length} fag)</span>
              </div>
            )}
            <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-bg-primary/50 text-text-secondary text-left">
                    {!hideStudentCol && <th className="px-4 py-3 font-medium">Elev</th>}
                    <th className="px-4 py-3 font-medium">Fag</th>
                    <th className="px-4 py-3 font-medium">Trinn</th>
                    <th className="px-4 py-3 font-medium">Karakter</th>
                    <th className="px-4 py-3 font-medium">Dato</th>
                    <th className="px-4 py-3 font-medium">Institusjon</th>
                    <th className="px-4 py-3 font-medium">Lærer</th>
                    {canWrite && <th className="px-4 py-3 font-medium w-24">Handlinger</th>}
                  </tr>
                </thead>
                <tbody>
                  {grpGrades.map(g => {
                    const level = subjectLevelMap[g.subject.toUpperCase()];
                    const displayValue = formatGradeValue(g.value, level);
                    const gradeYL = getGradeYearLevel(g) || subjectYearMap[g.subject.toUpperCase()] || '';
                    return (
                      <tr key={g.id} className="border-b border-border/50 hover:bg-bg-hover/50 transition-colors">
                        {!hideStudentCol && <td className="px-4 py-3 text-text-primary">{g.studentName || g.studentUsername}</td>}
                        <td className="px-4 py-3 font-mono text-accent">{g.subject}</td>
                        <td className="px-4 py-3 text-text-secondary text-xs">
                          {gradeYL
                            ? <span className="bg-bg-hover px-2 py-0.5 rounded-md">{formatYearLabel(gradeYL)}</span>
                            : '—'}
                        </td>
                        <td className="px-4 py-3">
                          <span className={`inline-block px-2.5 py-1 rounded-lg font-bold text-sm ${gradeColor(displayValue, level)}`}
                            title={g.blockedByAbsence && g.originalValue ? `Opprinnelig: ${g.originalValue} — Blokkert pga. fraværsgrense` : undefined}>
                            {displayValue}
                            {g.blockedByAbsence && !g.retake && <span className="ml-1 text-[10px] font-normal opacity-70">⚠ Fravær</span>}
                          </span>
                          {g.retake && <span className="ml-2 text-[10px] font-medium text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded">Privatist</span>}
                        </td>
                        <td className="px-4 py-3 text-text-secondary">{g.dateGiven}</td>
                        <td className="px-4 py-3 text-accent font-medium">{g.institutionName || 'Default'}</td>
                        <td className="px-4 py-3 text-text-secondary">{g.teacherUsername}</td>
                        {canWrite && (
                          <td className="px-4 py-3">
                            <div className="flex gap-1">
                              {g.blockedByAbsence && !g.retake && (
                                <button onClick={() => { setRetakeTarget(g); setRetakeValue(''); }}
                                  className="px-2 py-1 rounded-md text-[11px] font-medium bg-amber-500/10 text-amber-400 hover:bg-amber-500/20 transition-colors"
                                  title="Registrer privatisteksamen">
                                  Ny prøve
                                </button>
                              )}
                              <button onClick={() => { setEditingGrade(g); setShowForm(true); }} className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                              <button onClick={() => setDeleteTarget(g)} className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                            </div>
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    );
  };

  const levelTitles: Record<SubjectLevel, string> = {
    UNGDOMSSKOLE: 'Ungdomsskole',
    VGS: 'Videregående Skole',
    FAGSKOLE: 'Fagskole',
    UNIVERSITET: 'Universitet / Høyskole',
  };

  const toggleProgram = (id: number) => {
    setExpandedPrograms(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleStudent = (username: string) => {
    setExpandedStudents(prev => {
      const next = new Set(prev);
      if (next.has(username)) next.delete(username); else next.add(username);
      return next;
    });
  };



  return (
    <div>
      <PageHeader title={isStudent ? 'Mine Karakterer' : 'Karakterer'} description={isStudent ? 'Din utdanningshistorikk og karakteroversikt' : 'Karakterer sortert etter klasse'}
        action={canWrite ? (<button onClick={() => { setEditingGrade(null); setShowForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"><Plus size={16} /> Legg til</button>) : undefined} />

      {/* Student tabs: Current grades vs full history */}
      {isStudent && (
        <div className="flex gap-1 mb-6 bg-bg-secondary border border-border rounded-lg p-1 w-fit">
          <button onClick={() => setViewTab('current')}
            className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md transition-colors ${viewTab === 'current' ? 'bg-accent text-white shadow-sm' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'}`}>
            <GraduationCap size={15} /> Nåværende
          </button>
          <button onClick={() => setViewTab('history')}
            className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md transition-colors ${viewTab === 'history' ? 'bg-accent text-white shadow-sm' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'}`}>
            <FileSearch size={15} /> Utdanningshistorikk
          </button>
        </div>
      )}

      {/* Search bar (always) */}
      {viewTab === 'current' && (
        <div className="relative max-w-sm mb-6">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search grades..."
            className="w-full pl-9 pr-4 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors" />
        </div>
      )}

      {/* Education History tab */}
      {isStudent && viewTab === 'history' && (
        <EducationHistoryView history={educationHistory || []} subjectYearMap={subjectYearMap} />
      )}

      {/* Current grades tab */}
      {viewTab === 'current' && (
        <>
          {isStudent ? (
            /* Student view */
            filtered.length === 0 ? <EmptyState title="Ingen karakterer funnet" /> : (
              <div className="space-y-8">
                {/* Vitnemål Summary Card */}
                <VitnemaalCard grades={filtered} subjectLevelMap={subjectLevelMap}
                  groupedGrades={groupedGradesForStudent} />

                {(Object.keys(levelTitles) as SubjectLevel[]).map(level => {
                  const groupGrades = groupedGradesForStudent[level];
                  if (!groupGrades || groupGrades.length === 0) return null;
                  
                  const avg = calculateAverage(groupGrades, level);
                  const numAvg = numericAverage(groupGrades, subjectLevelMap);

                  // Sub-group by year level
                  const byYear: Record<string, Grade[]> = {};
                  groupGrades.forEach(g => {
                    const yr = g.yearLevel || subjectYearMap[g.subject.toUpperCase()] || 'Ukjent';
                    if (!byYear[yr]) byYear[yr] = [];
                    byYear[yr].push(g);
                  });
                  const sortedYears = Object.keys(byYear).sort((a, b) => {
                    return yearLevelSortNum(a) - yearLevelSortNum(b);
                  });
                  
                  return (
                    <div key={level} className="animate-in fade-in slide-in-from-bottom-2 duration-300">
                      <div className="flex items-center justify-between mb-3 border-b border-border/50 pb-2">
                        <div className="flex items-center gap-2">
                          <GraduationCap size={18} className="text-accent" />
                          <h3 className="text-lg font-semibold text-text-primary">{levelTitles[level]}</h3>
                          <span className="text-xs text-text-muted bg-bg-hover rounded-full px-2 py-0.5">{groupGrades.length} fag</span>
                        </div>
                        <div className="flex items-center gap-3">
                          {avg && (
                            <div className="bg-bg-card px-3 py-1.5 rounded-lg border border-border flex items-center gap-2 shadow-sm">
                              <span className="text-sm font-medium text-text-secondary">Snitt:</span>
                              <span className={`font-bold ${isNumericLevel(level) ? 'text-accent' : gradeColor(avg, level).split(' ')[0]}`}>{avg}</span>
                              {numAvg != null && !isNumericLevel(level) && (
                                <span className="text-xs text-text-muted">({numAvg})</span>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                      {sortedYears.length > 1 ? (
                        <div className="space-y-4">
                          {sortedYears.map(yr => (
                            <div key={yr}>
                              <h4 className="text-sm font-semibold text-text-secondary mb-2 flex items-center gap-2">
                                <span className="w-1.5 h-1.5 rounded-full bg-accent"></span>
                                {formatYearLabel(yr)}
                                <span className="text-xs text-text-muted font-normal">({byYear[yr].length} fag)</span>
                              </h4>
                              {renderGradeTable(byYear[yr], true)}
                            </div>
                          ))}
                        </div>
                      ) : (
                        renderGradeTable(groupGrades, true)
                      )}
                    </div>
                  );
                })}
              </div>
            )
          ) : (
            /* Admin/Teacher view: always show class-based view */
            programs.length > 0 ? (
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
                  const programGradeCount = students.reduce((sum, s) => sum + (gradesByStudent[s.username]?.length || 0), 0);

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
                          {programGradeCount > 0 && (
                            <span className="text-xs text-accent bg-accent/10 rounded-full px-2.5 py-0.5">{programGradeCount} karakterer</span>
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
                                const studentGrades = gradesByStudent[s.username] || [];
                                const isStudentExpanded = expandedStudents.has(s.username);

                                return (
                                  <div key={s.userId}>
                                    <button onClick={() => toggleStudent(s.username)}
                                      className="w-full flex items-center justify-between px-5 py-3 hover:bg-bg-hover/30 transition-colors text-left">
                                      <div className="flex items-center gap-3">
                                        {isStudentExpanded ? <ChevronDown size={14} className="text-accent" /> : <ChevronRight size={14} className="text-text-muted" />}
                                        <span className="text-sm font-medium text-text-primary">{s.firstName} {s.lastName}</span>
                                        <span className="text-xs font-mono text-text-muted">{s.username}</span>
                                        {s.yearLevel && <span className="text-xs text-accent bg-accent/10 rounded px-1.5 py-0.5">{s.yearLevel}. trinn</span>}
                                      </div>
                                      <span className="text-xs text-text-muted">
                                        {studentGrades.length > 0 ? `${studentGrades.length} karakter${studentGrades.length !== 1 ? 'er' : ''}` : 'Ingen karakterer'}
                                      </span>
                                    </button>

                                    {isStudentExpanded && studentGrades.length > 0 && (
                                      <div className="px-5 pb-4">
                                        {renderGradeTable(studentGrades, true)}
                                      </div>
                                    )}
                                    {isStudentExpanded && studentGrades.length === 0 && (
                                      <p className="text-sm text-text-muted italic px-10 pb-3">Ingen karakterer registrert</p>
                                    )}
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
              <EmptyState title="Ingen klasser funnet" />
            )
          )}
        </>
      )}

      {showForm && <GradeFormModal grade={editingGrade} subjects={subjects}
        onClose={() => { setShowForm(false); setEditingGrade(null); }}
        onSaved={() => { setShowForm(false); setEditingGrade(null); queryClient.invalidateQueries({ queryKey: ['grades'] }); }} />}

      <ConfirmDialog open={!!deleteTarget} title="Delete grade" message="Are you sure? This cannot be undone."
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)} onCancel={() => setDeleteTarget(null)} loading={deleteMutation.isPending} />

      {/* Retake (privatist) dialog */}
      {retakeTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-bg-card border border-border rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-bold text-text-primary mb-1">Ny prøve (Privatist)</h3>
            <p className="text-sm text-text-muted mb-4">
              Sett ny karakter for <span className="font-mono text-accent">{retakeTarget.subject}</span> — {retakeTarget.studentName}
              {retakeTarget.originalValue && (
                <span className="block mt-1 text-xs">Opprinnelig karakter: <strong>{retakeTarget.originalValue}</strong></span>
              )}
            </p>
            <label className="block text-sm font-medium text-text-secondary mb-1">Ny karakter</label>
            <input type="text" value={retakeValue} onChange={e => setRetakeValue(e.target.value)}
              placeholder="f.eks. 4 eller C"
              className="w-full px-3 py-2 bg-bg-primary border border-border rounded-lg text-text-primary mb-4 focus:border-accent focus:outline-none" />
            <div className="flex gap-2 justify-end">
              <button onClick={() => { setRetakeTarget(null); setRetakeValue(''); }}
                className="px-4 py-2 text-sm rounded-lg bg-bg-hover text-text-secondary hover:text-text-primary transition-colors">
                Avbryt
              </button>
              <button onClick={() => retakeValue.trim() && retakeMutation.mutate({ id: retakeTarget.id, value: retakeValue.trim() })}
                disabled={!retakeValue.trim() || retakeMutation.isPending}
                className="px-4 py-2 text-sm rounded-lg bg-accent text-white font-medium hover:bg-accent/90 transition-colors disabled:opacity-50">
                {retakeMutation.isPending ? 'Lagrer...' : 'Registrer privatistkarakter'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Education History View ─────────────────────────────────────────────────────
function EducationHistoryView({ history, subjectYearMap }: { history: EducationLevel[]; subjectYearMap: Record<string, string> }) {
  if (history.length === 0) {
    return <EmptyState title="Ingen utdanningshistorikk funnet" />;
  }

  const levelIcons: Record<string, string> = {
    UNGDOMSSKOLE: '🏫',
    VGS: '🎓',
    FAGSKOLE: '🔧',
    UNIVERSITET: '🏛️',
  };

  const trinnLabels: Record<string, string> = {
    '8': '8. klasse', '9': '9. klasse', '10': '10. klasse',
    'VG1': 'VG1', 'VG2': 'VG2', 'VG3': 'VG3', 'VG3_PABYGG': 'VG3 Påbygg',
    '1': '1. året', '2': '2. året',
    'BACHELOR_1': 'Bachelor 1', 'BACHELOR_2': 'Bachelor 2', 'BACHELOR_3': 'Bachelor 3',
    'MASTER_1': 'Master 1', 'MASTER_2': 'Master 2',
    'PHD_1': 'PhD 1', 'PHD_2': 'PhD 2', 'PHD_3': 'PhD 3',
  };

  const gradeToNum = (val: string) => {
    const numVal = parseFloat(val);
    if (!isNaN(numVal)) return numVal;
    const letterMap: Record<string, number> = { A: 6, B: 5, C: 4, D: 3, E: 2, F: 1 };
    return letterMap[val.toUpperCase()] || 0;
  };

  const calcAvg = (grades: Grade[]) => {
    const vals = grades.map(g => gradeToNum(g.value)).filter(v => v > 0);
    return vals.length > 0 ? vals.reduce((a, b) => a + b, 0) / vals.length : 0;
  };

  return (
    <div className="space-y-6">
      <div className="relative">
        {history.map((eduLevel, idx) => {
          // Group grades by trinn — use grade's stored yearLevel, then subjectYearMap
          const byTrinn: Record<string, Grade[]> = {};
          eduLevel.grades.forEach(g => {
            const yr = g.yearLevel || subjectYearMap[g.subject.toUpperCase()] || 'Ukjent';
            if (!byTrinn[yr]) byTrinn[yr] = [];
            byTrinn[yr].push(g);
          });
          const sortedTrinns = Object.keys(byTrinn).sort((a, b) => {
            const na = parseInt(a), nb = parseInt(b);
            if (!isNaN(na) && !isNaN(nb)) return na - nb;
            return a.localeCompare(b);
          });

          return (
            <div key={`${eduLevel.level}-${eduLevel.institutionId}`} className="relative mb-6">
              {idx < history.length - 1 && (
                <div className="absolute left-5 top-12 bottom-0 w-0.5 bg-border" />
              )}

              <div className="flex items-start gap-4">
                <div className={`w-10 h-10 rounded-full flex items-center justify-center text-lg shrink-0 z-10 ${
                  eduLevel.allPassing ? 'bg-green-400/10 border-2 border-green-400/30' : 'bg-red-400/10 border-2 border-red-400/30'
                }`}>
                  {levelIcons[eduLevel.level] || '📚'}
                </div>

                <div className="flex-1 bg-bg-card border border-border rounded-xl overflow-hidden">
                  {/* Level header */}
                  <div className="px-5 py-4 border-b border-border/50 flex items-center justify-between">
                    <div>
                      <h3 className="text-base font-semibold text-text-primary">{eduLevel.levelLabel}</h3>
                      <p className="text-xs text-text-muted mt-0.5">{eduLevel.institutionName} · {eduLevel.grades.length} fag</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="bg-bg-hover px-3 py-1.5 rounded-lg flex items-center gap-2">
                        <span className="text-xs text-text-muted">Totalsnitt:</span>
                        <span className="text-sm font-bold text-accent">{eduLevel.average.toFixed(2)}</span>
                      </div>
                      <span className={`px-2.5 py-1 rounded-lg text-xs font-medium ${
                        eduLevel.allPassing
                          ? 'bg-green-400/10 text-green-400 border border-green-400/20'
                          : 'bg-red-400/10 text-red-400 border border-red-400/20'
                      }`}>
                        {eduLevel.allPassing ? '✓ Bestått' : '✗ Ikke bestått'}
                      </span>
                    </div>
                  </div>

                  {/* Grades grouped by trinn */}
                  <div className="divide-y divide-border/30">
                    {sortedTrinns.map(trinn => {
                      const trinnGrades = byTrinn[trinn];
                      const trinnAvg = calcAvg(trinnGrades);
                      const trinnAllPassing = eduLevel.level === 'UNGDOMSSKOLE' || trinnGrades.every(g => gradeToNum(g.value) >= 2);
                      const label = trinnLabels[trinn] || trinn;

                      return (
                        <div key={trinn}>
                          {/* Trinn header */}
                          <div className="px-5 py-3 bg-bg-primary/40 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <span className="w-2 h-2 rounded-full bg-accent" />
                              <span className="text-sm font-semibold text-text-primary">{label}</span>
                              <span className="text-xs text-text-muted">({trinnGrades.length} fag)</span>
                            </div>
                            <div className="flex items-center gap-3">
                              <div className="bg-bg-hover px-2.5 py-1 rounded-md flex items-center gap-1.5">
                                <span className="text-[11px] text-text-muted">Snitt:</span>
                                <span className="text-sm font-bold text-accent">{trinnAvg.toFixed(2)}</span>
                              </div>
                              <span className={`px-2 py-0.5 rounded-md text-[11px] font-medium ${
                                trinnAllPassing
                                  ? 'bg-green-400/10 text-green-400'
                                  : 'bg-red-400/10 text-red-400'
                              }`}>
                                {trinnAllPassing ? '✓' : '✗'}
                              </span>
                            </div>
                          </div>

                          {/* Grade rows for this trinn */}
                          {trinnGrades.map(g => {
                            const isIV = g.value.toUpperCase() === 'IV';
                            const val = isIV ? 0 : gradeToNum(g.value);
                            const isFailing = !isIV && val < 2;

                            return (
                              <div key={g.id} className={`flex items-center justify-between px-5 py-2.5 ${isIV ? 'bg-red-500/5' : isFailing ? 'bg-red-400/5' : ''}`}>
                                <div className="flex items-center gap-3">
                                  <span className="text-sm font-mono text-accent w-24">{g.subject}</span>
                                  {g.dateGiven && <span className="text-xs text-text-muted">{g.dateGiven}</span>}
                                </div>
                                <div className="flex items-center gap-2">
                                  {isIV ? (
                                    <span className="px-2.5 py-1 rounded-lg font-bold text-sm text-red-500 bg-red-500/15 border border-red-500/30"
                                      title={g.originalValue ? `Opprinnelig: ${g.originalValue}` : undefined}>
                                      IV
                                    </span>
                                  ) : (
                                    <span className={`px-2.5 py-1 rounded-lg font-bold text-sm ${
                                      isFailing ? 'text-red-400 bg-red-400/10' :
                                      val >= 5 ? 'text-green-400 bg-green-400/10' :
                                      val >= 4 ? 'text-yellow-400 bg-yellow-400/10' :
                                      val >= 3 ? 'text-orange-400 bg-orange-400/10' :
                                      'text-red-400 bg-red-400/10'
                                    }`}>
                                      {g.value}
                                    </span>
                                  )}
                                  {isIV && <span className="text-[10px] text-red-500 font-medium">Fraværsgrense</span>}
                                  {isFailing && <span className="text-[10px] text-red-400 font-medium">STRYK</span>}
                                  {g.retake && <span className="text-[10px] text-amber-400 font-medium">Privatist</span>}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

    </div>
  );
}

function GradeFormModal({ grade, subjects, onClose, onSaved }: {
  grade: Grade | null; subjects: Subject[]; onClose: () => void; onSaved: () => void;
}) {
  const { user } = useAuth();
  const isEditing = !!grade;
  const [form, setForm] = useState<GradeRequest>({
    studentUsername: grade?.studentUsername || '',
    subject: grade?.subject || '',
    value: grade?.value || '',
    institutionId: grade?.institutionId || user?.institutionId,
    yearLevel: grade?.yearLevel || '',
  });
  const [selectedProgramId, setSelectedProgramId] = useState<number | ''>('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const instLevel = user?.institutionLevel || '';
  // VGS and higher need program/line selection; ungdomsskole does not
  const needsProgramSelect = instLevel !== 'UNGDOMSSKOLE';

  const { data: users = [] } = useQuery({ queryKey: ['users', 'STUDENT'], queryFn: () => getUsers('STUDENT') });
  const { data: allPrograms = [] } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });

  // Fetch members for all subjects to know which students are in which subjects
  const memberQueries = useQueries({
    queries: subjects.map(s => ({
      queryKey: ['subjectMembers', s.code],
      queryFn: () => getSubjectMembers(s.code),
      staleTime: 30000,
    })),
  });

  // Fetch members for all programs to know which students are in which programs
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

  // Build a map: studentUsername -> [subjectCodes] (from direct subject membership + program subjects)
  const studentSubjectMap = useMemo(() => {
    const map: Record<string, Set<string>> = {};
    // 1) Direct subject membership
    subjects.forEach((s, i) => {
      const members = memberQueries[i]?.data;
      if (!members) return;
      members.students.forEach(st => {
        if (!map[st.username]) map[st.username] = new Set();
        map[st.username].add(s.code);
      });
    });
    // 2) Program membership: student is in program -> gets all program's subjects
    allPrograms.forEach(p => {
      const members = allProgramMembers[p.id];
      if (!members) return;
      members.students.filter(s => !s.graduated).forEach(st => {
        if (!map[st.username]) map[st.username] = new Set();
        p.subjects.forEach(sub => map[st.username].add(sub.code));
      });
    });
    // Convert sets to arrays
    const result: Record<string, string[]> = {};
    Object.entries(map).forEach(([k, v]) => { result[k] = [...v]; });
    return result;
  }, [subjects, memberQueries, allPrograms, allProgramMembers]);

  // Build map: studentUsername -> yearLevel from program membership
  const studentYearLevelMap = useMemo(() => {
    const map: Record<string, string> = {};
    allPrograms.forEach(p => {
      const members = allProgramMembers[p.id];
      if (!members) return;
      members.students.filter(s => !s.graduated).forEach(st => {
        const m = p.name.match(/^(VG\d+|\d+)/);
        if (m) map[st.username] = m[1];
      });
    });
    return map;
  }, [allPrograms, allProgramMembers]);

  // Programs available for the selected yearLevel.
  // A program belongs to a trinn if it has any non-graduated member at that yearLevel,
  // or any subject tagged with that yearLevel. Falls back to a name-prefix match
  // for legacy data where the trinn is encoded in the program name (e.g. "VG1 Studiespesialisering").
  const programsForYear = useMemo(() => {
    if (!form.yearLevel) return [];
    return allPrograms.filter(p => {
      const members = allProgramMembers[p.id];
      const memberMatch = members?.students?.some(s => !s.graduated && s.yearLevel === form.yearLevel);
      if (memberMatch) return true;
      const subjectMatch = p.subjects?.some(s => s.yearLevel === form.yearLevel);
      if (subjectMatch) return true;
      const m = p.name.match(/^(VG\d+|\d+)/);
      return !!(m && m[1] === form.yearLevel);
    });
  }, [allPrograms, allProgramMembers, form.yearLevel]);

  // Get students filtered by selected program (VGS) or yearLevel (ungdomsskole)
  const filteredStudentOptions = useMemo(() => {
    const all = users.map(u => ({ value: u.username, label: `${u.firstName} ${u.lastName}`, sublabel: u.username }));

    if (needsProgramSelect) {
      // VGS/higher: filter by selected program AND the chosen trinn
      if (!selectedProgramId) return [];
      const members = allProgramMembers[selectedProgramId as number];
      if (!members) return [];
      const programStudentUsernames = new Set(
        members.students
          .filter(s => !s.graduated && (!form.yearLevel || !s.yearLevel || s.yearLevel === form.yearLevel))
          .map(s => s.username)
      );
      return all.filter(o => programStudentUsernames.has(o.value));
    } else {
      // Ungdomsskole: filter by yearLevel as before
      if (!form.yearLevel) return all;
      return all.filter(o => studentYearLevelMap[o.value] === form.yearLevel);
    }
  }, [users, form.yearLevel, selectedProgramId, studentYearLevelMap, allProgramMembers, needsProgramSelect]);

  // Filter subjects: for VGS, show only subjects from the selected program
  const assignedCodes = form.studentUsername ? (studentSubjectMap[form.studentUsername] || []) : [];
  const programSubjectCodes = useMemo(() => {
    if (!needsProgramSelect || !selectedProgramId) return null;
    const program = allPrograms.find(p => p.id === selectedProgramId);
    if (!program) return null;
    // If subjects are tagged with yearLevel, narrow to subjects matching the selected trinn.
    // Otherwise fall back to all subjects in the program (legacy data without yearLevel).
    const taggedForYear = form.yearLevel
      ? program.subjects.filter(s => s.yearLevel === form.yearLevel)
      : [];
    const pool = taggedForYear.length > 0 ? taggedForYear : program.subjects;
    return new Set(pool.map(s => s.code));
  }, [needsProgramSelect, selectedProgramId, allPrograms, form.yearLevel]);

  const filteredSubjects = useMemo(() => {
    let result = subjects;
    // If a student is selected, show only their assigned subjects
    if (form.studentUsername && assignedCodes.length > 0) {
      result = result.filter(s => assignedCodes.includes(s.code));
    }
    // If VGS and a program is selected, narrow to program subjects
    if (programSubjectCodes) {
      result = result.filter(s => programSubjectCodes.has(s.code));
    }
    return result;
  }, [subjects, form.studentUsername, assignedCodes, programSubjectCodes]);

  const subjectOptions = filteredSubjects.map(s => ({ value: s.code, label: s.name, sublabel: `${s.code} (${s.level === 'UNGDOMSSKOLE' || s.level === 'VGS' ? '1-6' : 'A-F'})` }));

  const selectedSubject = subjects.find(s => s.code.toUpperCase() === form.subject.toUpperCase());
  const useNumeric = selectedSubject ? isNumericLevel(selectedSubject.level) : false;
  const gradeOptions = useNumeric
    ? ['6', '5', '4', '3', '2', '1']
    : ['A', 'B', 'C', 'D', 'E', 'F'];

  // Year level options based on institution level
  const yearLevelOptions = useMemo(() => {
    if (instLevel === 'UNGDOMSSKOLE') return [{ value: '8', label: '8. klasse' }, { value: '9', label: '9. klasse' }, { value: '10', label: '10. klasse' }];
    if (instLevel === 'VGS') return [{ value: 'VG1', label: 'VG1' }, { value: 'VG2', label: 'VG2' }, { value: 'VG3', label: 'VG3' }];
    if (instLevel === 'FAGSKOLE') return [{ value: '1', label: '1. året' }, { value: '2', label: '2. året' }];
    return [
      { value: 'BACHELOR_1', label: 'Bachelor 1' }, { value: 'BACHELOR_2', label: 'Bachelor 2' }, { value: 'BACHELOR_3', label: 'Bachelor 3' },
      { value: 'MASTER_1', label: 'Master 1' }, { value: 'MASTER_2', label: 'Master 2' },
    ];
  }, [instLevel]);

  const handleYearChange = (year: string) => {
    setForm({ ...form, yearLevel: year, studentUsername: '', subject: '', value: '' });
    setSelectedProgramId('');
  };

  const handleProgramChange = (programId: number | '') => {
    setSelectedProgramId(programId);
    setForm({ ...form, studentUsername: '', subject: '', value: '' });
  };

  const handleStudentChange = (username: string) => {
    // Auto-detect yearLevel from student's program
    let autoYearLevel = form.yearLevel;
    if (!autoYearLevel) {
      allPrograms.forEach(p => {
        const members = allProgramMembers[p.id];
        if (!members) return;
        const isMember = members.students.some(s => s.username === username && !s.graduated);
        if (isMember) {
          const m = p.name.match(/^(VG\d+|\d+)/);
          if (m) autoYearLevel = m[1];
        }
      });
    }
    setForm({ ...form, studentUsername: username, subject: '', value: '', yearLevel: autoYearLevel });
  };

  const handleSubjectChange = (code: string) => {
    const newSubject = subjects.find(s => s.code.toUpperCase() === code.toUpperCase());
    const wasNumeric = selectedSubject ? isNumericLevel(selectedSubject.level) : false;
    const nowNumeric = newSubject ? isNumericLevel(newSubject.level) : false;
    if (wasNumeric !== nowNumeric) {
      setForm({ ...form, subject: code, value: '' });
    } else {
      setForm({ ...form, subject: code });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true);
    try {
      if (isEditing) { await updateGrade(grade!.id, form); toast.success('Karakter oppdatert'); }
      else { await createGrade(form); toast.success('Karakter registrert'); }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.error || 'Kunne ikke lagre'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold mb-4">{isEditing ? 'Rediger Karakter' : 'Sett Karakter'}</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Step 1: Year level */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Trinn</label>
            <select value={form.yearLevel || ''} onChange={e => handleYearChange(e.target.value)} required
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
              <option value="">Velg trinn...</option>
              {yearLevelOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>

          {/* Step 2: Program/Line (VGS and higher only) */}
          {needsProgramSelect && form.yearLevel && (
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
                  const studentCount = members?.students?.filter(s => !s.graduated).length || 0;
                  return <option key={p.id} value={p.id}>{p.name} ({studentCount} elever)</option>;
                })}
              </select>
            </div>
          )}

          {/* Step 3: Student */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Elev
              {needsProgramSelect && !selectedProgramId && form.yearLevel && <span className="text-text-muted font-normal"> (velg linje først)</span>}
              {!needsProgramSelect && !form.yearLevel && <span className="text-text-muted font-normal"> (velg trinn først)</span>}
              {((needsProgramSelect && selectedProgramId) || (!needsProgramSelect && form.yearLevel)) && filteredStudentOptions.length === 0 && <span className="text-text-muted font-normal"> (ingen elever)</span>}
            </label>
            <AutocompleteInput
              value={form.studentUsername}
              onChange={handleStudentChange}
              options={(needsProgramSelect ? !!selectedProgramId : !!form.yearLevel) ? filteredStudentOptions : []}
              placeholder={
                needsProgramSelect
                  ? (selectedProgramId ? "Søk etter elev..." : "Velg linje først...")
                  : (form.yearLevel ? "Søk etter elev..." : "Velg trinn først...")
              }
              required
            />
          </div>

          {/* Step 4: Subject */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Fag {form.studentUsername && subjectOptions.length === 0 && <span className="text-text-muted font-normal">(ingen fag tildelt)</span>}
            </label>
            <AutocompleteInput
              value={form.subject}
              onChange={handleSubjectChange}
              options={subjectOptions}
              placeholder="Søk fag..."
              required
            />
          </div>

          {/* Step 5: Grade value */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Karakter {selectedSubject ? (
                <span className={`font-normal ${useNumeric ? 'text-accent' : 'text-purple-400'}`}>
                  ({useNumeric ? '1-6' : 'A-F'} — {selectedSubject.level.charAt(0) + selectedSubject.level.slice(1).toLowerCase()})
                </span>
              ) : (
                <span className="text-text-muted font-normal">(velg fag først)</span>
              )}
            </label>
            <select value={form.value} onChange={e => setForm({...form, value: e.target.value})} required
              disabled={!selectedSubject}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50">
              <option value="">{selectedSubject ? 'Velg karakter...' : 'Velg fag først...'}</option>
              {gradeOptions.map(g => <option key={g} value={g}>{g}</option>)}
            </select>
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">{loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Registrer'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Vitnemål Summary Card ──────────────────────────────────────────────────────
function VitnemaalCard({ grades, subjectLevelMap, groupedGrades }: {
  grades: Grade[];
  subjectLevelMap: Record<string, SubjectLevel>;
  groupedGrades: Partial<Record<SubjectLevel, Grade[]>>;
}) {
  const overallGpa = numericAverage(grades, subjectLevelMap);

  const levelTitles: Record<SubjectLevel, string> = {
    UNGDOMSSKOLE: 'Ungdomsskole',
    VGS: 'Videregående',
    FAGSKOLE: 'Fagskole',
    UNIVERSITET: 'Universitet',
  };

  const levelBreakdown = (Object.keys(levelTitles) as SubjectLevel[])
    .filter(level => groupedGrades[level] && groupedGrades[level]!.length > 0)
    .map(level => ({
      level,
      title: levelTitles[level],
      count: groupedGrades[level]!.length,
      gpa: numericAverage(groupedGrades[level]!, subjectLevelMap),
    }));

  if (levelBreakdown.length === 0) return null;

  const gpaColor = (gpa: number) => {
    if (gpa >= 5) return 'text-green-400';
    if (gpa >= 4) return 'text-accent';
    if (gpa >= 3) return 'text-yellow-400';
    if (gpa >= 2) return 'text-orange-400';
    return 'text-red-400';
  };

  return (
    <div className="bg-gradient-to-br from-accent/5 via-bg-card to-bg-card border border-accent/20 rounded-xl p-6">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-xl bg-accent/10 flex items-center justify-center">
            <GraduationCap size={24} className="text-accent" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Vitnemål</h2>
            <p className="text-sm text-text-muted">{grades.length} fag registrert</p>
          </div>
        </div>
      </div>

      <div className="mt-5 flex gap-6 flex-wrap">
        {/* Overall GPA */}
        <div className="text-center">
          <p className={`text-3xl font-bold ${overallGpa ? gpaColor(overallGpa) : 'text-text-muted'}`}>
            {overallGpa != null ? overallGpa.toFixed(2) : '-'}
          </p>
          <p className="text-xs text-text-muted mt-1">Samlet snitt</p>
        </div>

        {/* Level breakdown */}
        {levelBreakdown.map(lb => (
          <div key={lb.level} className="text-center px-4 border-l border-border/50">
            <p className={`text-xl font-bold ${lb.gpa ? gpaColor(lb.gpa) : 'text-text-muted'}`}>
              {lb.gpa != null ? lb.gpa.toFixed(2) : '-'}
            </p>
            <p className="text-xs text-text-muted mt-1">{lb.title}</p>
            <p className="text-xs text-text-muted">{lb.count} fag</p>
          </div>
        ))}
      </div>
    </div>
  );
}
