import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getSubjects, createSubject, updateSubject, deleteSubject, getSubjectMembers, assignMember, removeMember } from '../api/subjects';
import { getPrograms, createProgram, updateProgram, deleteProgram, addSubjectToProgram, removeSubjectFromProgram, getProgramMembers, addProgramMember, removeProgramMember } from '../api/programs';
import { getUsers } from '../api/users';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Plus, Pencil, Trash2, Search, X, Users, UserPlus, GraduationCap, BookOpen, ChevronDown, ChevronRight, FolderOpen, LinkIcon, Unlink, ArrowUpCircle, ArrowRightLeft, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import type { Subject, SubjectRequest, SubjectLevel, User, Program, ProgramRequest, ProgramMember, ProgramSubjectSummary } from '../types';
import { useAuth } from '../auth/AuthProvider';
import { AutocompleteInput } from '../components/AutocompleteInput';
import { getPromotionPreview, executePromotion, undoPromotion, transferStudent } from '../api/promotion';
import type { PromotionPreview } from '../api/promotion';


// ── Institution-level config ───────────────────────────────────────────────────

interface LevelConfig {
  pageTitle: string;
  pageDescription: string;
  programLabel: string;
  programLabelPlural: string;
  programPlaceholder: string;
  yearOptions: { value: string; label: string; group?: string }[];
  addSubjectLabel: string;
  addProgramLabel: string;
}

const LEVEL_CONFIGS: Record<string, LevelConfig> = {
  UNGDOMSSKOLE: {
    pageTitle: 'Klasser & Fag',
    pageDescription: 'Administrer klasser og fag for ungdomsskolen',
    programLabel: 'Klasse',
    programLabelPlural: 'Klasser',
    programPlaceholder: 'F.eks. Klasse 8A, Klasse 9B...',
    yearOptions: [
      { value: '8', label: '8. klasse' },
      { value: '9', label: '9. klasse' },
      { value: '10', label: '10. klasse' },
    ],
    addSubjectLabel: 'Legg til fag',
    addProgramLabel: 'Ny klasse',
  },
  VGS: {
    pageTitle: 'Linjer & Fag',
    pageDescription: 'Administrer linjer og fag for videregående',
    programLabel: 'Linje',
    programLabelPlural: 'Linjer',
    programPlaceholder: 'F.eks. Studiespesialiserende, Elektro...',
    yearOptions: [
      { value: 'VG1', label: 'VG1', group: 'Felles' },
      { value: 'VG2', label: 'VG2', group: 'Felles' },
      { value: 'VG3', label: 'VG3 (Studieforberedende)', group: 'Studieforberedende' },
      { value: 'VG3_PABYGG', label: 'VG3 Påbygg', group: 'Påbygg' },
    ],
    addSubjectLabel: 'Legg til fag',
    addProgramLabel: 'Ny linje',
  },
  FAGSKOLE: {
    pageTitle: 'Fagskole Grader & Fag',
    pageDescription: 'Administrer grader og fag for fagskolen',
    programLabel: 'Fagskolegrad',
    programLabelPlural: 'Fagskolegrader',
    programPlaceholder: 'F.eks. Elektro, Bygg og anlegg...',
    yearOptions: [
      { value: '1', label: '1. året' },
      { value: '2', label: '2. året' },
    ],
    addSubjectLabel: 'Legg til fag',
    addProgramLabel: 'Ny fagskolegrad',
  },
  UNIVERSITET: {
    pageTitle: 'Degrees & Emner',
    pageDescription: 'Administrer grader og emner for høyskole/universitet',
    programLabel: 'Degree',
    programLabelPlural: 'Degrees',
    programPlaceholder: 'F.eks. Informatikk, Sykepleie...',
    yearOptions: [
      { value: 'BACHELOR_1', label: '1. år (Bachelor)', group: 'Bachelor' },
      { value: 'BACHELOR_2', label: '2. år (Bachelor)', group: 'Bachelor' },
      { value: 'BACHELOR_3', label: '3. år (Bachelor)', group: 'Bachelor' },
      { value: 'MASTER_1', label: '1. år (Master)', group: 'Master' },
      { value: 'MASTER_2', label: '2. år (Master)', group: 'Master' },
      { value: 'PHD_1', label: '1. år (PhD)', group: 'Doktorgrad' },
      { value: 'PHD_2', label: '2. år (PhD)', group: 'Doktorgrad' },
      { value: 'PHD_3', label: '3. år (PhD)', group: 'Doktorgrad' },
    ],
    addSubjectLabel: 'Legg til emne',
    addProgramLabel: 'Ny degree',
  },
};

const DEFAULT_CONFIG: LevelConfig = {
  pageTitle: 'Programs & Subjects',
  pageDescription: 'Manage programs and subjects',
  programLabel: 'Program',
  programLabelPlural: 'Programs',
  programPlaceholder: 'Enter program name...',
  yearOptions: [],
  addSubjectLabel: 'Add Subject',
  addProgramLabel: 'New Program',
};

const gradeScale = (level: SubjectLevel) =>
  level === 'UNGDOMSSKOLE' || level === 'VGS' ? '1-6' : 'A-F';

function yearLevelLabel(yearLevel: string, config: LevelConfig): string {
  const opt = config.yearOptions.find(o => o.value === yearLevel);
  return opt?.label || yearLevel;
}

/** Sort subjects by yearLevel (using config order), then alphabetically by code. */
function sortSubjects(subjects: ProgramSubjectSummary[], config: LevelConfig): ProgramSubjectSummary[] {
  const yearOrder = new Map(config.yearOptions.map((o, i) => [o.value, i]));
  return [...subjects].sort((a, b) => {
    const aYear = a.yearLevel ? (yearOrder.get(a.yearLevel) ?? 999) : 999;
    const bYear = b.yearLevel ? (yearOrder.get(b.yearLevel) ?? 999) : 999;
    if (aYear !== bYear) return aYear - bYear;
    return a.code.localeCompare(b.code);
  });
}

// ── Main Component ─────────────────────────────────────────────────────────────

export function SubjectsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [expandedPrograms, setExpandedPrograms] = useState<Set<number>>(new Set());
  // Program CRUD modals
  const [showProgramForm, setShowProgramForm] = useState(false);
  const [editingProgram, setEditingProgram] = useState<Program | null>(null);
  const [deleteProgramTarget, setDeleteProgramTarget] = useState<Program | null>(null);
  // Subject CRUD modals
  const [showSubjectForm, setShowSubjectForm] = useState(false);
  const [editingSubject, setEditingSubject] = useState<Subject | null>(null);
  const [deleteSubjectTarget, setDeleteSubjectTarget] = useState<Subject | null>(null);
  const [membersSubject, setMembersSubject] = useState<Subject | null>(null);
  // Link subject to program
  const [linkingProgram, setLinkingProgram] = useState<Program | null>(null);
  // Program members
  const [membersProgram, setMembersProgram] = useState<Program | null>(null);
  // Promotion
  const [showPromotion, setShowPromotion] = useState(false);

  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'INSTITUTION_ADMIN';
  const instLevel = user?.institutionLevel || '';
  const config = LEVEL_CONFIGS[instLevel] || DEFAULT_CONFIG;

  const { data: programs = [], isLoading: loadingPrograms } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });
  const { data: subjects = [], isLoading: loadingSubjects } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });

  const deleteProgramMut = useMutation({
    mutationFn: deleteProgram,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['programs'] }); toast.success(`${config.programLabel} slettet`); setDeleteProgramTarget(null); },
    onError: () => toast.error(`Kunne ikke slette ${config.programLabel.toLowerCase()}`),
  });

  const deleteSubjectMut = useMutation({
    mutationFn: deleteSubject,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['subjects'] }); queryClient.invalidateQueries({ queryKey: ['programs'] }); toast.success('Fag slettet'); setDeleteSubjectTarget(null); },
    onError: () => toast.error('Kunne ikke slette fag'),
  });

  const unlinkMut = useMutation({
    mutationFn: ({ programId, subjectId }: { programId: number; subjectId: number }) => removeSubjectFromProgram(programId, subjectId),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['programs'] }); toast.success('Fag fjernet fra program'); },
    onError: () => toast.error('Kunne ikke fjerne fag'),
  });

  const filteredPrograms = programs.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.subjects.some(s => s.code.toLowerCase().includes(search.toLowerCase()) || s.name.toLowerCase().includes(search.toLowerCase()))
  ).sort((a, b) => {
    const ma = a.name.match(/^(\d+)(.*)/), mb = b.name.match(/^(\d+)(.*)/);
    if (ma && mb) { const diff = parseInt(ma[1]) - parseInt(mb[1]); if (diff !== 0) return diff; return ma[2].localeCompare(mb[2]); }
    if (ma) return -1; if (mb) return 1; return a.name.localeCompare(b.name);
  });

  // Subjects not linked to ANY program (orphans)
  const linkedSubjectIds = new Set(programs.flatMap(p => p.subjects.map(s => s.id)));
  const unlinkedSubjects = subjects.filter(s =>
    !linkedSubjectIds.has(s.id) &&
    (s.code.toLowerCase().includes(search.toLowerCase()) || s.name.toLowerCase().includes(search.toLowerCase()))
  );

  const toggleProgram = (id: number) => {
    setExpandedPrograms(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  if (loadingPrograms || loadingSubjects) return <LoadingState message="Laster..." />;

  return (
    <div>
      <PageHeader title={config.pageTitle} description={config.pageDescription}
        action={isAdmin ? (
          <div className="flex gap-2">
            <button onClick={() => setShowPromotion(true)} className="flex items-center gap-2 px-4 py-2 bg-bg-secondary hover:bg-bg-hover border border-border text-text-primary text-sm font-medium rounded-lg transition-colors">
              <ArrowUpCircle size={16} /> Flytt opp elever
            </button>
            <button onClick={() => { setEditingSubject(null); setShowSubjectForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-bg-secondary hover:bg-bg-hover border border-border text-text-primary text-sm font-medium rounded-lg transition-colors">
              <Plus size={16} /> {config.addSubjectLabel}
            </button>
            <button onClick={() => { setEditingProgram(null); setShowProgramForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors">
              <Plus size={16} /> {config.addProgramLabel}
            </button>
          </div>
        ) : undefined}
      />

      <div className="relative max-w-sm mb-6">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Søk etter fag eller program..."
          className="w-full pl-9 pr-4 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors" />
      </div>

      {filteredPrograms.length === 0 && unlinkedSubjects.length === 0 ? (
        <EmptyState title={`Ingen ${config.programLabelPlural.toLowerCase()} eller fag funnet`} />
      ) : (
        <div className="space-y-4">
          {/* Programs */}
          {filteredPrograms.map(program => {
            const isOpen = expandedPrograms.has(program.id);
            return (
              <div key={program.id} className="bg-bg-card border border-border rounded-xl overflow-hidden">
                {/* Program Header */}
                <div className="flex items-center justify-between px-5 py-4 hover:bg-bg-hover/50 transition-colors">
                  <button onClick={() => toggleProgram(program.id)} className="flex items-center gap-3 flex-1 text-left">
                    {isOpen ? <ChevronDown size={18} className="text-accent shrink-0" /> : <ChevronRight size={18} className="text-text-muted shrink-0" />}
                    <FolderOpen size={18} className="text-accent shrink-0" />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <h3 className="text-sm font-semibold text-text-primary truncate">{program.name}</h3>
                        {program.programType === 'STUDIEFORBEREDENDE' && (
                          <span className="shrink-0 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-accent/10 text-accent">Studieforberedende</span>
                        )}
                        {program.programType === 'YRKESFAG' && (
                          <span className="shrink-0 text-[10px] font-semibold px-2 py-0.5 rounded-full bg-orange-400/10 text-orange-400">Yrkesfag</span>
                        )}
                      </div>
                      <p className="text-xs text-text-muted">{program.subjects.length} {program.subjects.length === 1 ? 'fag' : 'fag'}{program.description ? ` · ${program.description}` : ''}</p>
                    </div>
                  </button>
                  {isAdmin && (
                    <div className="flex items-center gap-1 ml-3">
                      <button onClick={() => setMembersProgram(program)} title="Administrer medlemmer"
                        className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-info transition-colors"><Users size={14} /></button>
                      <button onClick={() => setLinkingProgram(program)} title={`Legg til fag i ${config.programLabel.toLowerCase()}`}
                        className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><LinkIcon size={14} /></button>
                      <button onClick={() => { setEditingProgram(program); setShowProgramForm(true); }}
                        className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                      <button onClick={() => setDeleteProgramTarget(program)}
                        className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                    </div>
                  )}
                </div>

                {/* Subjects within program */}
                {isOpen && (
                  <div className="border-t border-border/50">
                    {program.subjects.length === 0 ? (
                      <p className="px-5 py-4 text-sm text-text-muted italic">Ingen fag lagt til ennå</p>
                    ) : (
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-border/40 text-text-secondary text-left">
                            <th className="px-5 pl-12 py-2 font-medium text-xs">Kode</th>
                            <th className="px-4 py-2 font-medium text-xs">Navn</th>
                            <th className="px-4 py-2 font-medium text-xs">Årstrinn</th>
                            <th className="px-4 py-2 font-medium text-xs w-28">Handlinger</th>
                          </tr>
                        </thead>
                        <tbody>
                          {sortSubjects(program.subjects, config).map(s => {
                            const fullSubject = subjects.find(fs => fs.id === s.id);
                            return (
                              <tr key={s.id} className="border-b border-border/20 hover:bg-bg-hover/30 transition-colors">
                                <td className="px-5 pl-12 py-2.5 font-mono font-medium text-accent text-xs">{s.code}</td>
                                <td className="px-4 py-2.5 font-medium text-text-primary">{s.name}</td>
                                <td className="px-4 py-2.5 text-text-secondary text-xs">{s.yearLevel ? yearLevelLabel(s.yearLevel, config) : '—'}</td>
                                <td className="px-4 py-2.5">
                                  <div className="flex gap-1">
                                    {fullSubject && (
                                      <button onClick={() => setMembersSubject(fullSubject)} title="Medlemmer"
                                        className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-info transition-colors"><Users size={14} /></button>
                                    )}
                                    {isAdmin && (
                                      <>
                                        {fullSubject && (
                                          <button onClick={() => { setEditingSubject(fullSubject); setShowSubjectForm(true); }}
                                            className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                                        )}
                                        <button onClick={() => unlinkMut.mutate({ programId: program.id, subjectId: s.id })} title="Fjern fra dette programmet"
                                          className="p-1.5 rounded-md hover:bg-warning/10 text-text-muted hover:text-warning transition-colors"><Unlink size={14} /></button>
                                      </>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}
              </div>
            );
          })}

          {/* Unlinked subjects */}
          {unlinkedSubjects.length > 0 && (
            <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
              <div className="flex items-center gap-3 px-5 py-4 border-b border-border/50">
                <BookOpen size={18} className="text-text-muted shrink-0" />
                <div>
                  <h3 className="text-sm font-semibold text-text-secondary">Fag uten program</h3>
                  <p className="text-xs text-text-muted">{unlinkedSubjects.length} fag som ikke er tilknyttet noen {config.programLabel.toLowerCase()}</p>
                </div>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/40 text-text-secondary text-left">
                    <th className="px-5 py-2 font-medium text-xs">Kode</th>
                    <th className="px-4 py-2 font-medium text-xs">Navn</th>
                    <th className="px-4 py-2 font-medium text-xs">Beskrivelse</th>
                    <th className="px-4 py-2 font-medium text-xs w-28">Handlinger</th>
                  </tr>
                </thead>
                <tbody>
                  {unlinkedSubjects.map(s => (
                    <tr key={s.id} className="border-b border-border/20 hover:bg-bg-hover/30 transition-colors">
                      <td className="px-5 py-2.5 font-mono font-medium text-accent text-xs">{s.code}</td>
                      <td className="px-4 py-2.5 font-medium text-text-primary">{s.name}</td>
                      <td className="px-4 py-2.5 text-text-secondary text-xs">{s.description || '—'}</td>
                      <td className="px-4 py-2.5">
                        <div className="flex gap-1">
                          <button onClick={() => setMembersSubject(s)} title="Medlemmer"
                            className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-info transition-colors"><Users size={14} /></button>
                          {isAdmin && (
                            <>
                              <button onClick={() => { setEditingSubject(s); setShowSubjectForm(true); }}
                                className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                              <button onClick={() => setDeleteSubjectTarget(s)} title="Slett fag permanent"
                                className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* All subjects – admin global list for permanent deletion */}
          {isAdmin && subjects.length > 0 && (
            <AllSubjectsSection
              subjects={subjects}
              programs={programs}
              config={config}
              search={search}
              onEdit={(s) => { setEditingSubject(s); setShowSubjectForm(true); }}
              onDelete={(s) => setDeleteSubjectTarget(s)}
              onMembers={(s) => setMembersSubject(s)}
            />
          )}

        </div>
      )}

      {/* Program Form Modal */}
      {showProgramForm && (
        <ProgramFormModal program={editingProgram} config={config} instLevel={instLevel}
          onClose={() => { setShowProgramForm(false); setEditingProgram(null); }}
          onSaved={() => { setShowProgramForm(false); setEditingProgram(null); queryClient.invalidateQueries({ queryKey: ['programs'] }); }} />
      )}

      {/* Subject Form Modal */}
      {showSubjectForm && (
        <SubjectFormModal subject={editingSubject} config={config} instLevel={instLevel}
          onClose={() => { setShowSubjectForm(false); setEditingSubject(null); }}
          onSaved={() => { setShowSubjectForm(false); setEditingSubject(null); queryClient.invalidateQueries({ queryKey: ['subjects'] }); queryClient.invalidateQueries({ queryKey: ['programs'] }); }} />
      )}

      {/* Program Members Modal */}
      {membersProgram && (
        <ProgramMembersModal program={membersProgram} config={config} isAdmin={isAdmin} programs={programs}
          onClose={() => setMembersProgram(null)} />
      )}

      {/* Link Subject to Program Modal */}
      {linkingProgram && (
        <LinkSubjectModal program={linkingProgram} subjects={subjects} config={config}
          onClose={() => setLinkingProgram(null)} />
      )}

      {/* Members Modal */}
      {membersSubject && (
        <SubjectMembersModal subject={membersSubject} isAdmin={isAdmin} onClose={() => setMembersSubject(null)} />
      )}

      {/* Confirm Dialogs */}
      <ConfirmDialog open={!!deleteProgramTarget} title={`Slett ${config.programLabel.toLowerCase()}`}
        message={`Er du sikker på at du vil slette "${deleteProgramTarget?.name}"? Fagene som tilhører denne ${config.programLabel.toLowerCase()} blir IKKE slettet.`}
        onConfirm={() => deleteProgramTarget && deleteProgramMut.mutate(deleteProgramTarget.id)} onCancel={() => setDeleteProgramTarget(null)} loading={deleteProgramMut.isPending} />

      <ConfirmDialog open={!!deleteSubjectTarget} title="Slett fag"
        message={`Slette "${deleteSubjectTarget?.name}" (${deleteSubjectTarget?.code})? Faget fjernes fra alle programmer. Denne handlingen kan ikke angres.`}
        onConfirm={() => deleteSubjectTarget && deleteSubjectMut.mutate(deleteSubjectTarget.id)} onCancel={() => setDeleteSubjectTarget(null)} loading={deleteSubjectMut.isPending} />

      {/* Promotion Modal */}
      {showPromotion && (
        <PromotionModal config={config} onClose={() => setShowPromotion(false)} />
      )}
    </div>
  );
}

// ── Promotion Modal ────────────────────────────────────────────────────────────
function PromotionModal({ config, onClose }: { config: LevelConfig; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [executing, setExecuting] = useState(false);
  const [undoing, setUndoing] = useState(false);
  const [result, setResult] = useState<{ promoted: number; graduated: number } | null>(null);
  const [undoResult, setUndoResult] = useState<{ promoted: number; graduated: number } | null>(null);
  const [showUndoConfirm, setShowUndoConfirm] = useState(false);

  const { data: preview, isLoading } = useQuery({
    queryKey: ['promotionPreview'],
    queryFn: getPromotionPreview,
  });

  const handleExecute = async () => {
    setExecuting(true);
    try {
      const res = await executePromotion();
      setResult(res);
      queryClient.invalidateQueries({ queryKey: ['programs'] });
      queryClient.invalidateQueries({ queryKey: ['programMembers'] });
      toast.success(`${res.promoted} elever flyttet opp, ${res.graduated} uteksaminert`);
    } catch {
      toast.error('Kunne ikke flytte opp elever');
    } finally {
      setExecuting(false);
    }
  };

  const handleUndo = async () => {
    setUndoing(true);
    setShowUndoConfirm(false);
    try {
      const res = await undoPromotion();
      setUndoResult(res);
      setResult(null);
      queryClient.invalidateQueries({ queryKey: ['programs'] });
      queryClient.invalidateQueries({ queryKey: ['programMembers'] });
      queryClient.invalidateQueries({ queryKey: ['promotionPreview'] });
      toast.success(`Oppflytting angret: ${res.promoted} flyttet tilbake, ${res.graduated} gjenåpnet`);
    } catch {
      toast.error('Kunne ikke angre oppflytting');
    } finally {
      setUndoing(false);
    }
  };

  const totalActions = (preview?.promotions.length ?? 0) + (preview?.graduations.length ?? 0);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Flytt opp elever</h2>
            <p className="text-sm text-text-muted mt-0.5">Forhåndsvisning av årsoppflytting</p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary transition-colors"><X size={18} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-5">
          {isLoading ? (
            <p className="text-text-muted text-sm text-center py-6">Beregner oppflytting...</p>
          ) : undoResult ? (
            <div className="text-center py-6 space-y-3">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-orange-500/10 text-orange-400">
                <RotateCcw size={28} />
              </div>
              <h3 className="text-lg font-semibold text-text-primary">Oppflytting angret!</h3>
              <div className="flex justify-center gap-6">
                <div className="text-center">
                  <p className="text-2xl font-bold text-accent">{undoResult.promoted}</p>
                  <p className="text-xs text-text-muted">Flyttet tilbake</p>
                </div>
                <div className="text-center">
                  <p className="text-2xl font-bold text-orange-400">{undoResult.graduated}</p>
                  <p className="text-xs text-text-muted">Gjenåpnet</p>
                </div>
              </div>
            </div>
          ) : result ? (
            <div className="text-center py-6 space-y-3">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-green-500/10 text-green-400">
                <GraduationCap size={28} />
              </div>
              <h3 className="text-lg font-semibold text-text-primary">Oppflytting fullført!</h3>
              <div className="flex justify-center gap-6">
                <div className="text-center">
                  <p className="text-2xl font-bold text-accent">{result.promoted}</p>
                  <p className="text-xs text-text-muted">Flyttet opp</p>
                </div>
                <div className="text-center">
                  <p className="text-2xl font-bold text-green-400">{result.graduated}</p>
                  <p className="text-xs text-text-muted">Uteksaminert</p>
                </div>
              </div>
              <div className="pt-3">
                <button onClick={() => setShowUndoConfirm(true)} disabled={undoing}
                  className="flex items-center gap-2 mx-auto px-4 py-2 text-sm border border-orange-400/30 text-orange-400 rounded-lg hover:bg-orange-400/10 transition-colors disabled:opacity-50">
                  <RotateCcw size={14} />
                  {undoing ? 'Angrer...' : 'Angre oppflytting'}
                </button>
              </div>
            </div>
          ) : totalActions === 0 ? (
            <p className="text-text-muted text-sm text-center py-6">Ingen elever å flytte opp</p>
          ) : (
            <>
              {/* Promotions */}
              {preview!.promotions.length > 0 && (
                <div>
                  <h3 className="text-sm font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center gap-2">
                    <ArrowUpCircle size={14} className="text-accent" />
                    Flyttes opp ({preview!.promotions.length})
                  </h3>
                  <div className="rounded-lg border border-border overflow-hidden">
                    {preview!.promotions.map((a, i) => (
                      <div key={`p-${a.userId}-${a.programId}`} className={`flex items-center justify-between px-3 py-2.5 ${i < preview!.promotions.length - 1 ? 'border-b border-border/50' : ''}`}>
                        <div>
                          <span className="text-sm font-medium text-text-primary">{a.fullName}</span>
                          <span className="ml-2 text-xs text-text-muted">{a.programName}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-xs">
                          <span className="px-1.5 py-0.5 rounded bg-bg-hover text-text-secondary font-medium">{yearLevelLabel(a.fromYear, config)}</span>
                          <span className="text-text-muted">→</span>
                          <span className="px-1.5 py-0.5 rounded bg-accent/10 text-accent font-medium">{a.toYear ? yearLevelLabel(a.toYear, config) : '—'}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Graduations */}
              {preview!.graduations.length > 0 && (
                <div>
                  <h3 className="text-sm font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center gap-2">
                    <GraduationCap size={14} className="text-green-400" />
                    Uteksamineres ({preview!.graduations.length})
                  </h3>
                  <div className="rounded-lg border border-green-500/30 overflow-hidden">
                    {preview!.graduations.map((a, i) => (
                      <div key={`g-${a.userId}-${a.programId}`} className={`flex items-center justify-between px-3 py-2.5 bg-green-500/5 ${i < preview!.graduations.length - 1 ? 'border-b border-green-500/20' : ''}`}>
                        <div>
                          <span className="text-sm font-medium text-text-primary">{a.fullName}</span>
                          <span className="ml-2 text-xs text-text-muted">{a.programName}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="px-1.5 py-0.5 rounded bg-bg-hover text-text-secondary text-xs font-medium">{yearLevelLabel(a.fromYear, config)}</span>
                          <span className="text-xs text-green-400 font-medium">🎓 Ferdig</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <div className="border-t border-border p-5 flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">
            {result || undoResult ? 'Lukk' : 'Avbryt'}
          </button>
          {!result && !undoResult && totalActions > 0 && (
            <button onClick={handleExecute} disabled={executing}
              className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">
              {executing ? 'Flytter opp...' : `Flytt opp ${totalActions} elever`}
            </button>
          )}
        </div>

        {/* Undo confirm dialog */}
        <ConfirmDialog
          open={showUndoConfirm}
          title="Angre oppflytting?"
          message="Er du sikker på at du vil angre oppflyttingen? Alle elever flyttes tilbake til forrige årstrinn, og uteksaminerte studenter gjenåpnes."
          onConfirm={handleUndo}
          onCancel={() => setShowUndoConfirm(false)}
          loading={undoing}
        />
      </div>
    </div>
  );
}

// ── All Subjects Section (global list for permanent deletion) ──────────────────
function AllSubjectsSection({ subjects, programs, config, search, onEdit, onDelete, onMembers }: {
  subjects: Subject[];
  programs: Program[];
  config: LevelConfig;
  search: string;
  onEdit: (s: Subject) => void;
  onDelete: (s: Subject) => void;
  onMembers: (s: Subject) => void;
}) {
  const [expanded, setExpanded] = useState(false);

  const filtered = subjects.filter(s =>
    s.code.toLowerCase().includes(search.toLowerCase()) ||
    s.name.toLowerCase().includes(search.toLowerCase())
  );

  // Build a map: subjectId -> program names
  const subjectProgramMap = useMemo(() => {
    const map = new Map<number, string[]>();
    for (const p of programs) {
      for (const s of p.subjects) {
        if (!map.has(s.id)) map.set(s.id, []);
        map.get(s.id)!.push(p.name);
      }
    }
    return map;
  }, [programs]);

  return (
    <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
      <button onClick={() => setExpanded(!expanded)}
        className="flex items-center justify-between w-full px-5 py-4 hover:bg-bg-hover/50 transition-colors text-left">
        <div className="flex items-center gap-3">
          {expanded ? <ChevronDown size={18} className="text-danger shrink-0" /> : <ChevronRight size={18} className="text-text-muted shrink-0" />}
          <Trash2 size={18} className="text-danger shrink-0" />
          <div>
            <h3 className="text-sm font-semibold text-text-primary">Alle fag ({subjects.length})</h3>
            <p className="text-xs text-text-muted">Administrer og slett fag permanent fra alle {config.programLabelPlural.toLowerCase()}</p>
          </div>
        </div>
      </button>

      {expanded && (
        <div className="border-t border-border/50">
          {filtered.length === 0 ? (
            <p className="px-5 py-4 text-sm text-text-muted italic">Ingen fag funnet</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border/40 text-text-secondary text-left">
                  <th className="px-5 py-2 font-medium text-xs">Kode</th>
                  <th className="px-4 py-2 font-medium text-xs">Navn</th>
                  <th className="px-4 py-2 font-medium text-xs">Tilknyttet</th>
                  <th className="px-4 py-2 font-medium text-xs w-28">Handlinger</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(s => {
                  const linkedPrograms = subjectProgramMap.get(s.id) || [];
                  return (
                    <tr key={s.id} className="border-b border-border/20 hover:bg-bg-hover/30 transition-colors">
                      <td className="px-5 py-2.5 font-mono font-medium text-accent text-xs">{s.code}</td>
                      <td className="px-4 py-2.5 font-medium text-text-primary">{s.name}</td>
                      <td className="px-4 py-2.5 text-xs">
                        {linkedPrograms.length > 0 ? (
                          <div className="flex flex-wrap gap-1">
                            {linkedPrograms.map((name, i) => (
                              <span key={i} className="px-2 py-0.5 bg-accent/10 text-accent rounded-full text-[11px] font-medium">{name}</span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-text-muted italic">Ingen</span>
                        )}
                      </td>
                      <td className="px-4 py-2.5">
                        <div className="flex gap-1">
                          <button onClick={() => onMembers(s)} title="Medlemmer"
                            className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-info transition-colors"><Users size={14} /></button>
                          <button onClick={() => onEdit(s)} title="Rediger"
                            className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={14} /></button>
                          <button onClick={() => onDelete(s)} title="Slett fag permanent fra alle programmer"
                            className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={14} /></button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

// ── Program Form Modal ─────────────────────────────────────────────────────────
function ProgramFormModal({ program, config, instLevel, onClose, onSaved }: {
  program: Program | null; config: LevelConfig; instLevel: string; onClose: () => void; onSaved: () => void;
}) {
  const isEditing = !!program;
  const isVGS = config.yearOptions.some(o => o.value === 'VG1');
  const isUngdomsskole = instLevel === 'UNGDOMSSKOLE';
  const showAdvancedFields = !isUngdomsskole; // Admission reqs, prerequisites, attendance limits
  const [form, setForm] = useState<ProgramRequest>({
    name: program?.name || '',
    description: program?.description || '',
    minGpa: program?.minGpa ?? null,
    maxStudents: program?.maxStudents ?? null,
    prerequisites: program?.prerequisites || '',
    attendanceRequired: program?.attendanceRequired ?? isVGS,
    minAttendancePct: program?.minAttendancePct ?? (isVGS ? 90 : null),
    programType: program?.programType || (isVGS ? '' : null),
  });
  const [tagInput, setTagInput] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Parse tags from comma-separated string
  const tags = (form.prerequisites || '').split(',').map(t => t.trim()).filter(Boolean);

  const addTag = () => {
    const val = tagInput.trim();
    if (!val) return;
    if (tags.includes(val)) { setTagInput(''); return; }
    setForm({ ...form, prerequisites: [...tags, val].join(', ') });
    setTagInput('');
  };

  const removeTag = (idx: number) => {
    setForm({ ...form, prerequisites: tags.filter((_, i) => i !== idx).join(', ') || null });
  };

  const handleTagKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addTag(); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true);
    // Validate programType for VGS
    if (isVGS && !form.programType) { setError('Du må velge programtype (Studieforberedende eller Yrkesfag)'); setLoading(false); return; }
    try {
      if (isEditing) { await updateProgram(program!.id, form); toast.success(`${config.programLabel} oppdatert`); }
      else { await createProgram(form); toast.success(`${config.programLabel} opprettet`); }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.errors?.join('. ') || err.response?.data?.error || 'Kunne ikke lagre'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">{isEditing ? `Rediger ${config.programLabel.toLowerCase()}` : `Opprett ${config.programLabel.toLowerCase()}`}</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Navn</label>
            <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} required placeholder={config.programPlaceholder}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" /></div>

          {/* Program type selector — VGS only */}
          {isVGS && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">
                Programtype <span className="text-danger">*</span>
              </label>
              <div className="flex gap-2">
                <button type="button"
                  onClick={() => setForm({...form, programType: 'STUDIEFORBEREDENDE'})}
                  className={`flex-1 px-3 py-2.5 rounded-lg text-sm font-medium border transition-all ${
                    form.programType === 'STUDIEFORBEREDENDE'
                      ? 'bg-accent/15 border-accent text-accent ring-1 ring-accent/30'
                      : 'bg-bg-input border-border text-text-secondary hover:bg-bg-hover'
                  }`}>
                  📚 Studieforberedende
                </button>
                <button type="button"
                  onClick={() => setForm({...form, programType: 'YRKESFAG'})}
                  className={`flex-1 px-3 py-2.5 rounded-lg text-sm font-medium border transition-all ${
                    form.programType === 'YRKESFAG'
                      ? 'bg-orange-400/15 border-orange-400 text-orange-400 ring-1 ring-orange-400/30'
                      : 'bg-bg-input border-border text-text-secondary hover:bg-bg-hover'
                  }`}>
                  🔧 Yrkesfag
                </button>
              </div>
              <p className="text-xs text-text-muted mt-1.5">
                {form.programType === 'STUDIEFORBEREDENDE' && 'VG1 → VG2 → VG3 (generell studiekompetanse)'}
                {form.programType === 'YRKESFAG' && 'VG1 → VG2 → uteksaminert (fagbrev / kompetansebevis)'}
                {!form.programType && 'Velg om linjen er studieforberedende eller yrkesfag'}
              </p>
            </div>
          )}

          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Beskrivelse (valgfri)</label>
            <textarea value={form.description || ''} onChange={e => setForm({...form, description: e.target.value})} rows={3}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus resize-none" /></div>

          {/* Admission requirements section — only for VGS, Fagskole, Universitet */}
          {showAdvancedFields && (
          <div className="border-t border-border pt-4">
            <p className="text-sm font-medium text-text-secondary mb-3">Opptakskrav (for søknadsportalen)</p>
            <div className="flex gap-3 mb-3">
              <div className="flex-1">
                <label className="block text-xs text-text-muted mb-1">Min. snittkarakter</label>
                <input type="number" step="0.1" min="1" max="6"
                  value={form.minGpa ?? ''} onChange={e => setForm({...form, minGpa: e.target.value ? parseFloat(e.target.value) : null})}
                  placeholder="Ingen"
                  className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" />
              </div>
              <div className="flex-1">
                <label className="block text-xs text-text-muted mb-1">Maks plasser</label>
                <input type="number" min="1"
                  value={form.maxStudents ?? ''} onChange={e => setForm({...form, maxStudents: e.target.value ? parseInt(e.target.value) : null})}
                  placeholder="Ubegrenset"
                  className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" />
              </div>
            </div>

            {/* Prerequisites tags */}
            <div>
              <label className="block text-xs text-text-muted mb-1">Forutsetninger / fagkrav</label>
              <div className="flex gap-2 mb-2">
                <input value={tagInput} onChange={e => setTagInput(e.target.value)} onKeyDown={handleTagKey}
                  placeholder="f.eks. Studiespesialisering, R-matte..."
                  className="flex-1 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" />
                <button type="button" onClick={addTag}
                  className="px-3 py-2 text-xs font-medium bg-accent/10 text-accent hover:bg-accent/20 rounded-lg transition-colors">
                  + Legg til
                </button>
              </div>
              {tags.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {tags.map((tag, i) => (
                    <span key={i} className="flex items-center gap-1 px-2.5 py-1 bg-accent/10 text-accent text-xs font-medium rounded-full">
                      {tag}
                      <button type="button" onClick={() => removeTag(i)} className="hover:text-danger transition-colors"><X size={12} /></button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
          )}

          {/* Attendance settings — only for VGS, Fagskole, Universitet */}
          {showAdvancedFields && (
          <div className="pt-3 border-t border-border/50">
            <h4 className="text-sm font-semibold text-text-secondary mb-3">Fraværsregler</h4>
            <label className="flex items-center gap-2 mb-3">
              <input type="checkbox" checked={form.attendanceRequired || false}
                disabled={isVGS}
                onChange={e => setForm({...form, attendanceRequired: e.target.checked, minAttendancePct: e.target.checked ? (form.minAttendancePct || 80) : null})}
                className="w-4 h-4 rounded border-border" />
              <span className="text-sm text-text-secondary">
                Fraværsgrense aktiv {isVGS && <span className="text-xs text-text-muted">(VGS: alltid 10% grense)</span>}
              </span>
            </label>
            {form.attendanceRequired && (
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1.5">
                  Minimum oppmøte %
                </label>
                <input type="number" min={50} max={100}
                  value={form.minAttendancePct ?? ''}
                  disabled={isVGS}
                  onChange={e => setForm({...form, minAttendancePct: e.target.value ? parseInt(e.target.value) : null})}
                  className="w-32 px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50"
                  placeholder="90" />
                <p className="text-xs text-text-muted mt-1">
                  {form.minAttendancePct ? `Maks ${100 - form.minAttendancePct}% ugyldig fravær tillatt` : 'Sett grensen'}
                </p>
              </div>
            )}
          </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">
              {loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Opprett'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Program Members Modal ──────────────────────────────────────────────────────
function ProgramMembersModal({ program, config, isAdmin, programs, onClose }: {
  program: Program; config: LevelConfig; isAdmin: boolean; programs: Program[]; onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [addRole, setAddRole] = useState<'STUDENT' | 'TEACHER'>('STUDENT');
  const [addYearLevel, setAddYearLevel] = useState(config.yearOptions[0]?.value || '');
  const [searchUser, setSearchUser] = useState('');
  const [selectedUserIds, setSelectedUserIds] = useState<Set<number>>(new Set());
  const [bulkLoading, setBulkLoading] = useState(false);
  const [transferTarget, setTransferTarget] = useState<{ userId: number; name: string } | null>(null);
  const [transferProgramId, setTransferProgramId] = useState<number | ''>('');

  const { data: members, isLoading } = useQuery({
    queryKey: ['programMembers', program.id],
    queryFn: () => getProgramMembers(program.id),
  });

  const { data: allUsers = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => getUsers(),
    enabled: isAdmin,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['programMembers', program.id] });

  const removeMut = useMutation({
    mutationFn: (userId: number) => removeProgramMember(program.id, userId),
    onSuccess: () => { toast.success('Medlem fjernet (fag-koblinger oppdatert)'); invalidate(); },
    onError: () => toast.error('Kunne ikke fjerne medlem'),
  });

  const transferMut = useMutation({
    mutationFn: ({ userId, toProgramId }: { userId: number; toProgramId: number }) =>
      transferStudent(userId, program.id, toProgramId),
    onSuccess: () => {
      toast.success('Elev flyttet til ny klasse');
      invalidate();
      queryClient.invalidateQueries({ queryKey: ['programMembers'] });
      setTransferTarget(null);
      setTransferProgramId('');
    },
    onError: () => toast.error('Kunne ikke flytte eleven'),
  });

  // Other programs to transfer to — SAME year level only (e.g. 8A → 8B, not 8A → 9A)
  // Extract the year prefix from the current program name using yearOptions
  const sameYearPrograms = useMemo(() => {
    const name = program.name;
    // Find which year prefix this program starts with (longest match first)
    const sortedYears = config.yearOptions.map(y => y.value).sort((a, b) => b.length - a.length);
    const yearPrefix = sortedYears.find(y => name.startsWith(y)) || '';
    if (!yearPrefix) return programs.filter(p => p.id !== program.id);
    // Only show programs that start with the same year prefix but are different programs
    return programs.filter(p => p.id !== program.id && p.name.startsWith(yearPrefix));
  }, [programs, program.id, program.name, config.yearOptions]);

  const memberUserIds = new Set([
    ...(members?.students.map(m => m.userId) ?? []),
    ...(members?.teachers.map(m => m.userId) ?? []),
  ]);

  const availableUsers = allUsers.filter(u =>
    !memberUserIds.has(u.id) &&
    (addRole === 'STUDENT' ? u.role === 'STUDENT' : u.role === 'TEACHER') &&
    (!searchUser || u.username.toLowerCase().includes(searchUser.toLowerCase()) ||
     `${u.firstName} ${u.lastName}`.toLowerCase().includes(searchUser.toLowerCase()))
  );

  const toggleUser = (userId: number) => {
    setSelectedUserIds(prev => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId); else next.add(userId);
      return next;
    });
  };

  const selectAll = () => setSelectedUserIds(new Set(availableUsers.map(u => u.id)));
  const deselectAll = () => setSelectedUserIds(new Set());

  const handleBulkAdd = async () => {
    if (selectedUserIds.size === 0) return;
    setBulkLoading(true);
    let added = 0;
    let failed = 0;
    for (const userId of selectedUserIds) {
      try {
        await addProgramMember(program.id, { userId, role: addRole, yearLevel: addRole === 'STUDENT' ? (addYearLevel || undefined) : undefined });
        added++;
      } catch {
        failed++;
      }
    }
    setBulkLoading(false);
    if (added > 0) toast.success(`${added} ${added === 1 ? 'medlem' : 'medlemmer'} lagt til`);
    if (failed > 0) toast.error(`${failed} kunne ikke legges til`);
    invalidate();
    setAdding(false);
    setSelectedUserIds(new Set());
    setSearchUser('');
  };

  const renderMemberList = (title: string, icon: React.ReactNode, memberList: ProgramMember[]) => (
    <div>
      <div className="flex items-center gap-2 mb-2">
        {icon}
        <h3 className="text-sm font-semibold text-text-secondary uppercase tracking-wide">{title}</h3>
        <span className="text-xs text-text-muted bg-bg-hover rounded-full px-2 py-0.5">{memberList.length}</span>
      </div>
      {memberList.length === 0 ? (
        <p className="text-text-muted text-sm italic pl-1">Ingen {title.toLowerCase()} tilknyttet</p>
      ) : (
        <div className="rounded-lg border border-border overflow-hidden">
          {memberList.map((m, i) => (
            <div key={m.id} className={`flex items-center justify-between px-3 py-2.5 ${i < memberList.length - 1 ? 'border-b border-border/50' : ''} hover:bg-bg-hover/40 transition-colors`}>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-text-primary">{m.firstName} {m.lastName}</span>
                  <span className="text-xs font-mono text-text-muted">{m.username}</span>
                </div>
                {m.yearLevel && (
                  <span className="text-xs text-accent">{yearLevelLabel(m.yearLevel, config)}</span>
                )}
              </div>
              {isAdmin && (
                <div className="flex gap-1 shrink-0">
                  {title.toLowerCase().includes('elev') && (
                    <button onClick={() => setTransferTarget({ userId: m.userId, name: `${m.firstName} ${m.lastName}` })}
                      className="p-1 rounded hover:bg-accent/10 text-text-muted hover:text-accent transition-colors" title="Bytt klasse">
                      <ArrowRightLeft size={13} />
                    </button>
                  )}
                  <button onClick={() => removeMut.mutate(m.userId)} disabled={removeMut.isPending}
                    className="p-1 rounded hover:bg-danger/10 text-text-muted hover:text-danger transition-colors disabled:opacity-50 shrink-0" title="Fjern">
                    <X size={13} />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Medlemmer</h2>
            <p className="text-sm text-text-muted mt-0.5">
              <span className="text-accent font-medium">{program.name}</span>
              {program.subjects.length > 0 && (
                <span> · {program.subjects.length} fag</span>
              )}
            </p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary transition-colors"><X size={18} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-5">
          {isLoading ? (
            <p className="text-text-muted text-sm text-center py-6">Laster medlemmer...</p>
          ) : (
            <>
              {renderMemberList('Lærere', <BookOpen size={14} className="text-warning" />, members?.teachers ?? [])}
              {renderMemberList('Elever', <GraduationCap size={14} className="text-info" />, members?.students ?? [])}
            </>
          )}
        </div>

        {isAdmin && (
          <div className="border-t border-border p-5">
            {!adding ? (
              <button onClick={() => setAdding(true)} className="flex items-center gap-2 text-sm text-accent hover:text-accent-hover transition-colors">
                <UserPlus size={15} /> Legg til medlemmer
              </button>
            ) : (
              <div className="space-y-3">
                <div className="flex gap-2">
                  <select value={addRole} onChange={e => { setAddRole(e.target.value as 'STUDENT' | 'TEACHER'); setSelectedUserIds(new Set()); setSearchUser(''); }}
                    className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
                    <option value="STUDENT">Elev</option>
                    <option value="TEACHER">Lærer</option>
                  </select>
                  {config.yearOptions.length > 0 && addRole === 'STUDENT' && (
                    <select value={addYearLevel} onChange={e => setAddYearLevel(e.target.value)}
                      className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
                      {config.yearOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                  )}
                </div>

                {/* Search */}
                <input value={searchUser} onChange={e => setSearchUser(e.target.value)} placeholder="Søk etter brukere..."
                  className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" />

                {/* User list with checkboxes */}
                {availableUsers.length > 0 ? (
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-xs text-text-muted">{availableUsers.length} tilgjengelige · {selectedUserIds.size} valgt</span>
                      <div className="flex gap-2">
                        <button type="button" onClick={selectAll} className="text-xs text-accent hover:text-accent-hover transition-colors">Velg alle</button>
                        {selectedUserIds.size > 0 && (
                          <button type="button" onClick={deselectAll} className="text-xs text-text-muted hover:text-text-primary transition-colors">Fjern valg</button>
                        )}
                      </div>
                    </div>
                    <div className="rounded-lg border border-border overflow-hidden max-h-48 overflow-y-auto">
                      {availableUsers.map((u, i) => (
                        <label key={u.id} className={`flex items-center gap-3 px-3 py-2.5 cursor-pointer hover:bg-bg-hover/50 transition-colors ${i < availableUsers.length - 1 ? 'border-b border-border/30' : ''}`}>
                          <input type="checkbox" checked={selectedUserIds.has(u.id)} onChange={() => toggleUser(u.id)}
                            className="w-4 h-4 rounded border-border text-accent focus:ring-accent/50 bg-bg-input" />
                          <div className="flex-1 min-w-0">
                            <span className="text-sm text-text-primary font-medium">{u.firstName} {u.lastName}</span>
                            <span className="text-xs text-text-muted font-mono ml-2">{u.username}</span>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-xs text-text-muted italic">Ingen tilgjengelige {addRole === 'STUDENT' ? 'elever' : 'lærere'} funnet</p>
                )}

                <div className="flex gap-2 justify-end">
                  <button onClick={() => { setAdding(false); setSelectedUserIds(new Set()); setSearchUser(''); }}
                    className="px-3 py-1.5 text-sm border border-border text-text-secondary rounded-lg hover:bg-bg-hover transition-colors">Avbryt</button>
                  <button onClick={handleBulkAdd} disabled={selectedUserIds.size === 0 || bulkLoading}
                    className="px-3 py-1.5 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50">
                    {bulkLoading ? 'Legger til...' : `Legg til ${selectedUserIds.size > 0 ? `(${selectedUserIds.size})` : ''}`}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Transfer dialog */}
      {transferTarget && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setTransferTarget(null)} />
          <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <button onClick={() => setTransferTarget(null)} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
            <h3 className="text-base font-semibold text-text-primary mb-1">Bytt klasse</h3>
            <p className="text-sm text-text-muted mb-4">
              Flytt <span className="text-text-primary font-medium">{transferTarget.name}</span> fra <span className="text-accent font-medium">{program.name}</span> til:
            </p>
            <select value={transferProgramId} onChange={e => setTransferProgramId(e.target.value ? Number(e.target.value) : '')}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus mb-4">
              <option value="">Velg målklasse...</option>
              {sameYearPrograms.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            <div className="flex justify-end gap-3">
              <button onClick={() => setTransferTarget(null)} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
              <button onClick={() => transferProgramId && transferMut.mutate({ userId: transferTarget.userId, toProgramId: transferProgramId as number })}
                disabled={!transferProgramId || transferMut.isPending}
                className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">
                {transferMut.isPending ? 'Flytter...' : 'Flytt'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}


// ── Link Subject to Program Modal ──────────────────────────────────────────────
function LinkSubjectModal({ program, subjects, config, onClose }: {
  program: Program; subjects: Subject[]; config: LevelConfig; onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const linkedIds = new Set(program.subjects.map(s => s.id));

  const available = subjects.filter(s =>
    !linkedIds.has(s.id) &&
    (s.code.toLowerCase().includes(search.toLowerCase()) || s.name.toLowerCase().includes(search.toLowerCase()))
  );

  const linkMut = useMutation({
    mutationFn: (subjectId: number) => addSubjectToProgram(program.id, subjectId),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['programs'] }); toast.success('Fag lagt til'); },
    onError: () => toast.error('Kunne ikke legge til fag'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Legg til fag</h2>
            <p className="text-sm text-text-muted mt-0.5">Koble eksisterende fag til <span className="text-accent font-medium">{program.name}</span></p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary transition-colors"><X size={18} /></button>
        </div>
        <div className="p-4">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Søk etter fag..."
              className="w-full pl-9 pr-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          {available.length === 0 ? (
            <p className="text-center text-text-muted text-sm py-6">Ingen tilgjengelige fag funnet</p>
          ) : (
            <div className="space-y-1">
              {available.map(s => (
                <div key={s.id} className="flex items-center justify-between px-3 py-2.5 rounded-lg hover:bg-bg-hover transition-colors">
                  <div>
                    <span className="text-sm font-medium text-text-primary">{s.name}</span>
                    <span className="ml-2 text-xs font-mono text-text-muted">{s.code}</span>
                  </div>
                  <button onClick={() => linkMut.mutate(s.id)} disabled={linkMut.isPending}
                    className="px-3 py-1 text-xs bg-accent hover:bg-accent-hover text-white rounded-md transition-colors disabled:opacity-50">
                    Legg til
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Subject Members Modal ──────────────────────────────────────────────────────
function SubjectMembersModal({ subject, isAdmin, onClose }: { subject: Subject; isAdmin: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [addUsername, setAddUsername] = useState('');
  const [addRole, setAddRole] = useState<'STUDENT' | 'TEACHER'>('STUDENT');
  const [search, setSearch] = useState('');

  const { data: members, isLoading } = useQuery({
    queryKey: ['subjectMembers', subject.code],
    queryFn: () => getSubjectMembers(subject.code),
  });

  const { data: allUsers = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => getUsers(),
    enabled: isAdmin,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['subjectMembers', subject.code] });

  const assignMutation = useMutation({
    mutationFn: () => assignMember(subject.code, { username: addUsername, role: addRole }),
    onSuccess: () => { toast.success(`${addUsername} lagt til som ${addRole.toLowerCase()}`); invalidate(); setAdding(false); setAddUsername(''); setAddRole('STUDENT'); },
    onError: () => toast.error('Kunne ikke legge til medlem'),
  });

  const removeMutation = useMutation({
    mutationFn: (username: string) => removeMember(subject.code, username),
    onSuccess: () => { toast.success('Medlem fjernet'); invalidate(); },
    onError: () => toast.error('Kunne ikke fjerne medlem'),
  });

  const assignedUsernames = new Set([
    ...(members?.students.map(u => u.username) ?? []),
    ...(members?.teachers.map(u => u.username) ?? []),
  ]);

  const availableUsers = allUsers.filter(u =>
    u.role === addRole &&
    !assignedUsernames.has(u.username) &&
    (u.username.toLowerCase().includes(search.toLowerCase()) ||
     `${u.firstName} ${u.lastName}`.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Medlemmer</h2>
            <p className="text-sm text-text-muted mt-0.5">
              <span className="font-mono text-accent">{subject.code}</span> — {subject.name}
            </p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary transition-colors"><X size={18} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-5">
          {isLoading ? (
            <p className="text-text-muted text-sm text-center py-6">Laster medlemmer...</p>
          ) : (
            <>
              <MemberSection title="Lærere" icon={<BookOpen size={14} className="text-warning" />}
                members={members?.teachers ?? []} isAdmin={isAdmin}
                onRemove={username => removeMutation.mutate(username)} removing={removeMutation.isPending} />
              <MemberSection title="Elever" icon={<GraduationCap size={14} className="text-info" />}
                members={members?.students ?? []} isAdmin={isAdmin}
                onRemove={username => removeMutation.mutate(username)} removing={removeMutation.isPending} />
            </>
          )}
        </div>

        {isAdmin && (
          <div className="border-t border-border p-5">
            {!adding ? (
              <button onClick={() => setAdding(true)} className="flex items-center gap-2 text-sm text-accent hover:text-accent-hover transition-colors">
                <UserPlus size={15} /> Legg til medlem
              </button>
            ) : (
              <div className="space-y-3">
                <div className="flex gap-2">
                  <div className="flex-1">
                    <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Søk etter brukere..."
                      className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" />
                    {search && availableUsers.length > 0 && (
                      <div className="mt-1 bg-bg-card border border-border rounded-lg overflow-hidden shadow-lg max-h-40 overflow-y-auto">
                        {availableUsers.slice(0, 8).map(u => (
                          <button key={u.username} onClick={() => { setAddUsername(u.username); setSearch(''); }}
                            className="w-full text-left px-3 py-2 text-sm hover:bg-bg-hover transition-colors flex items-center justify-between">
                            <span className="text-text-primary">{u.firstName} {u.lastName}</span>
                            <span className="text-text-muted font-mono text-xs">{u.username}</span>
                          </button>
                        ))}
                      </div>
                    )}
                    {addUsername && <p className="text-xs text-accent mt-1">Valgt: <span className="font-mono">{addUsername}</span></p>}
                  </div>
                  <select value={addRole} onChange={e => { setAddRole(e.target.value as 'STUDENT' | 'TEACHER'); setAddUsername(''); setSearch(''); }}
                    className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
                    <option value="STUDENT">Elev</option>
                    <option value="TEACHER">Lærer</option>
                  </select>
                </div>
                <div className="flex gap-2 justify-end">
                  <button onClick={() => { setAdding(false); setAddUsername(''); setSearch(''); }} className="px-3 py-1.5 text-sm border border-border text-text-secondary rounded-lg hover:bg-bg-hover transition-colors">Avbryt</button>
                  <button onClick={() => assignMutation.mutate()} disabled={!addUsername || assignMutation.isPending}
                    className="px-3 py-1.5 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50">
                    {assignMutation.isPending ? 'Legger til...' : 'Legg til'}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function MemberSection({ title, icon, members, isAdmin, onRemove, removing }: {
  title: string; icon: React.ReactNode; members: User[]; isAdmin: boolean;
  onRemove: (username: string) => void; removing: boolean;
}) {
  return (
    <div>
      <div className="flex items-center gap-2 mb-2">
        {icon}
        <h3 className="text-sm font-semibold text-text-secondary uppercase tracking-wide">{title}</h3>
        <span className="text-xs text-text-muted bg-bg-hover rounded-full px-2 py-0.5">{members.length}</span>
      </div>
      {members.length === 0 ? (
        <p className="text-text-muted text-sm italic pl-1">Ingen {title.toLowerCase()} tilknyttet</p>
      ) : (
        <div className="rounded-lg border border-border overflow-hidden">
          {members.map((u, i) => (
            <div key={u.username} className={`flex items-center justify-between px-3 py-2.5 ${i < members.length - 1 ? 'border-b border-border/50' : ''} hover:bg-bg-hover/40 transition-colors`}>
              <div>
                <span className="text-sm font-medium text-text-primary">{u.firstName} {u.lastName}</span>
                <span className="ml-2 text-xs font-mono text-text-muted">{u.username}</span>
              </div>
              {isAdmin && (
                <button onClick={() => onRemove(u.username)} disabled={removing}
                  className="p-1 rounded hover:bg-danger/10 text-text-muted hover:text-danger transition-colors disabled:opacity-50" title="Fjern">
                  <X size={13} />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Subject Form Modal ─────────────────────────────────────────────────────────
function SubjectFormModal({ subject, config, instLevel, onClose, onSaved }: {
  subject: Subject | null; config: LevelConfig; instLevel: string;
  onClose: () => void; onSaved: () => void;
}) {
  const { user: currentUser } = useAuth();
  const isEditing = !!subject;
  const [form, setForm] = useState<SubjectRequest>({
    code: subject?.code || '', name: subject?.name || '',
    description: subject?.description || '', level: subject?.level || 'UNIVERSITET',
    teacherUsername: '',
    institutionId: subject?.institutionId || currentUser?.institutionId,
    program: subject?.program || '',
    yearLevel: subject?.yearLevel || '',
  });
  const [selectedProgramIds, setSelectedProgramIds] = useState<Set<number>>(new Set());
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => getUsers(),
    enabled: !isEditing,
  });
  const teachers = users
    .filter(u => u.role === 'TEACHER')
    .map(u => ({ value: u.username, label: `${u.firstName} ${u.lastName} (${u.username})` }));

  // Load programs for the checkbox list
  const { data: programs = [] } = useQuery({
    queryKey: ['programs'],
    queryFn: getPrograms,
    enabled: !isEditing,
  });

  const toggleProgram = (id: number) => {
    setSelectedProgramIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    // Validate required fields when creating
    if (!isEditing) {
      if (selectedProgramIds.size === 0) { setError(`Du må velge minst én ${config.programLabel.toLowerCase()}`); setLoading(false); return; }
      if (!form.teacherUsername || form.teacherUsername.trim() === '') { setError('Du må tildele en lærer'); setLoading(false); return; }
    }
    try {
      if (isEditing) {
        await updateSubject(subject!.id, form);
        toast.success('Fag oppdatert');
      } else {
        const created = await createSubject(form);
        // Link to selected programs
        if (selectedProgramIds.size > 0) {
          const linkPromises = [...selectedProgramIds].map(pid =>
            addSubjectToProgram(pid, created.id)
          );
          await Promise.all(linkPromises);
        }
        toast.success('Fag opprettet' + (selectedProgramIds.size > 0 ? ` og lagt til i ${selectedProgramIds.size} ${config.programLabel.toLowerCase()}${selectedProgramIds.size > 1 ? 'er' : ''}` : ''));
      }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.errors?.join('. ') || err.response?.data?.error || 'Kunne ikke lagre'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl max-h-[90vh] overflow-y-auto">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">{isEditing ? 'Rediger fag' : 'Opprett fag'}</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Kode</label>
            <input value={form.code} onChange={e => setForm({...form, code: e.target.value})} required disabled={isEditing} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Navn</label>
            <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} required className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" /></div>

          {/* Year Level selector */}
          {config.yearOptions.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">Årstrinn</label>
              <select value={form.yearLevel || ''} onChange={e => setForm({...form, yearLevel: e.target.value})} required
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
                <option value="">Velg årstrinn...</option>
                {config.yearOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </div>
          )}

          {/* Program multi-select (only when creating) */}
          {!isEditing && programs.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">
                Legg til i {config.programLabel.toLowerCase()} <span className="text-danger">*</span>
              </label>
              <div className="border border-border rounded-lg overflow-hidden max-h-40 overflow-y-auto">
                {programs.map(p => (
                  <label key={p.id}
                    className={`flex items-center gap-3 px-3 py-2.5 cursor-pointer transition-colors border-b border-border/30 last:border-b-0 ${
                      selectedProgramIds.has(p.id) ? 'bg-accent/10' : 'hover:bg-bg-hover'
                    }`}>
                    <input
                      type="checkbox"
                      checked={selectedProgramIds.has(p.id)}
                      onChange={() => toggleProgram(p.id)}
                      className="rounded border-border text-accent focus:ring-accent/30 w-4 h-4 shrink-0"
                    />
                    <div className="min-w-0">
                      <span className="text-sm font-medium text-text-primary">{p.name}</span>
                      {p.subjects.length > 0 && (
                        <span className="ml-2 text-xs text-text-muted">({p.subjects.length} fag)</span>
                      )}
                    </div>
                  </label>
                ))}
              </div>
              {selectedProgramIds.size > 0 && (
                <p className="text-xs text-accent mt-1.5">{selectedProgramIds.size} {config.programLabel.toLowerCase()}{selectedProgramIds.size > 1 ? 'er' : ''} valgt</p>
              )}
            </div>
          )}

          {!isEditing && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">Tildel lærer <span className="text-danger">*</span></label>
              <AutocompleteInput
                value={form.teacherUsername || ''}
                onChange={(val) => setForm({ ...form, teacherUsername: val })}
                options={teachers}
                placeholder="Søk etter lærer..."
                required
              />
            </div>
          )}

          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Beskrivelse</label>
            <textarea value={form.description || ''} onChange={e => setForm({...form, description: e.target.value})} rows={3}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus resize-none" /></div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">{loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Opprett'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

