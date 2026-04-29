import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getAdmissionPeriods, createAdmissionPeriod, closeAdmissionPeriod,
  processAdmissions, getAdmissionOverview, getAdmissionRequirements,
  setAdmissionRequirement, enrollAccepted
} from '../api/admissions';
import { getPrograms } from '../api/programs';
import type { AdmissionPeriod, ProgramApplicantSummary, AdmissionRequirement, ProcessingResult, EnrollmentResult } from '../api/admissions';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import {
  Plus, X, ChevronDown, ChevronRight, Settings, Play, Lock,
  CheckCircle2, Clock, AlertTriangle, XCircle, Users
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';

const STATUS_BADGE: Record<string, { label: string; class: string }> = {
  OPEN: { label: 'Åpen', class: 'text-green-400 bg-green-400/10 border-green-400/20' },
  CLOSED: { label: 'Stengt', class: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/20' },
  PROCESSED: { label: 'Behandlet', class: 'text-accent bg-accent/10 border-accent/20' },
};

const LEVEL_LABELS: Record<string, string> = {
  UNGDOMSSKOLE: 'Ungdomsskole',
  VGS: 'Videregående',
  FAGSKOLE: 'Fagskole',
  UNIVERSITET: 'Universitet/Høyskole',
};

export function AdmissionAdminPage() {
  const queryClient = useQueryClient();
  const [expandedPeriod, setExpandedPeriod] = useState<number | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const { data: periods = [], isLoading } = useQuery({
    queryKey: ['admissionPeriods'],
    queryFn: getAdmissionPeriods,
  });

  if (isLoading) return <LoadingState message="Laster opptaksperioder..." />;

  return (
    <div>
      <PageHeader title="Opptaksadministrasjon" description="Administrer opptaksperioder, krav og søknadsbehandling"
        action={
          <button onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors">
            <Plus size={16} /> Ny opptaksperiode
          </button>
        }
      />

      {periods.length === 0 ? (
        <EmptyState title="Ingen opptaksperioder" />
      ) : (
        <div className="space-y-4">
          {periods.map(period => (
            <PeriodCard key={period.id} period={period} expanded={expandedPeriod === period.id}
              onToggle={() => setExpandedPeriod(expandedPeriod === period.id ? null : period.id)} />
          ))}
        </div>
      )}

      {/* Create Period Modal */}
      {showCreate && (
        <CreatePeriodModal onClose={() => setShowCreate(false)}
          onCreated={() => { setShowCreate(false); queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] }); }} />
      )}
    </div>
  );
}

// ── Period Card ────────────────────────────────────────────────────────────────
function PeriodCard({ period, expanded, onToggle }: {
  period: AdmissionPeriod; expanded: boolean; onToggle: () => void;
}) {
  const queryClient = useQueryClient();
  const [showRequirements, setShowRequirements] = useState(false);
  const [processingResult, setProcessingResult] = useState<ProcessingResult | null>(null);
  const [enrollmentResult, setEnrollmentResult] = useState<EnrollmentResult | null>(null);

  const closeMut = useMutation({
    mutationFn: () => closeAdmissionPeriod(period.id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] }); toast.success('Opptaksperiode stengt'); },
    onError: () => toast.error('Kunne ikke stenge periode'),
  });

  const processMut = useMutation({
    mutationFn: () => processAdmissions(period.id),
    onSuccess: (result) => { setProcessingResult(result); queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] }); toast.success('Opptak behandlet!'); },
    onError: () => toast.error('Kunne ikke behandle opptak'),
  });

  const enrollMut = useMutation({
    mutationFn: () => enrollAccepted(period.id),
    onSuccess: (result) => {
      setEnrollmentResult(result);
      queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] });
      if (result.enrolled > 0) toast.success(`${result.enrolled} elever registrert i programmer`);
      else toast.info('Ingen nye elever å registrere');
    },
    onError: () => toast.error('Kunne ikke registrere elever'),
  });

  const badge = STATUS_BADGE[period.status] || STATUS_BADGE.OPEN;

  return (
    <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
      <button onClick={onToggle} className="w-full text-left px-5 py-4 flex items-center justify-between hover:bg-bg-hover/30 transition-colors">
        <div className="flex items-center gap-3">
          {expanded ? <ChevronDown size={16} className="text-text-muted" /> : <ChevronRight size={16} className="text-text-muted" />}
          <div>
            <h3 className="text-base font-semibold text-text-primary">{period.name}</h3>
            <p className="text-xs text-text-muted mt-0.5">
              {LEVEL_LABELS[period.fromLevel] || period.fromLevel} → {LEVEL_LABELS[period.toLevel] || period.toLevel} ·
              {period.startDate} – {period.endDate} · {period.applicationCount} søknader
            </p>
          </div>
        </div>
        <span className={`px-2.5 py-1 rounded-lg text-xs font-medium border ${badge.class}`}>{badge.label}</span>
      </button>

      {expanded && (
        <div className="border-t border-border px-5 py-4 space-y-4">
          {/* Action buttons */}
          <div className="flex gap-2 flex-wrap">
            {period.status === 'OPEN' && (
              <button onClick={() => closeMut.mutate()} disabled={closeMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors">
                <Lock size={13} /> Steng søknadsfrist
              </button>
            )}
            <button onClick={() => setShowRequirements(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors">
              <Settings size={13} /> Sett krav
            </button>
            {(period.status === 'CLOSED' || period.status === 'OPEN') && (
              <button onClick={() => processMut.mutate()} disabled={processMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50">
                <Play size={13} /> {processMut.isPending ? 'Behandler...' : 'Kjør opptaksalgoritme'}
              </button>
            )}
          </div>

          {/* Processing result */}
          {processingResult && (
            <div className="p-4 bg-accent/5 border border-accent/20 rounded-lg">
              <h4 className="text-sm font-semibold text-accent mb-2">Opptaksresultat</h4>
              <div className="flex gap-6">
                <div className="text-center"><p className="text-xl font-bold text-green-400">{processingResult.accepted}</p><p className="text-xs text-text-muted">Godkjent</p></div>
                <div className="text-center"><p className="text-xl font-bold text-orange-400">{processingResult.waitlisted}</p><p className="text-xs text-text-muted">Venteliste</p></div>
                <div className="text-center"><p className="text-xl font-bold text-red-400">{processingResult.rejected}</p><p className="text-xs text-text-muted">Avslått</p></div>
                <div className="text-center"><p className="text-xl font-bold text-text-primary">{processingResult.total}</p><p className="text-xs text-text-muted">Totalt</p></div>
              </div>
            </div>
          )}

          {/* Enroll accepted students button */}
          {(period.status === 'PROCESSED' || processingResult) && (
            <div className="flex items-center gap-3">
              <button onClick={() => enrollMut.mutate()} disabled={enrollMut.isPending}
                className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors disabled:opacity-50">
                <CheckCircle2 size={15} />
                {enrollMut.isPending ? 'Registrerer...' : 'Registrer godkjente elever i programmer'}
              </button>
            </div>
          )}

          {/* Enrollment result */}
          {enrollmentResult && (
            <div className="p-4 bg-green-400/5 border border-green-400/20 rounded-lg">
              <h4 className="text-sm font-semibold text-green-400 mb-2">Innmeldingsresultat</h4>
              <div className="flex gap-6">
                <div className="text-center"><p className="text-xl font-bold text-green-400">{enrollmentResult.enrolled}</p><p className="text-xs text-text-muted">Innmeldt</p></div>
                <div className="text-center"><p className="text-xl font-bold text-yellow-400">{enrollmentResult.skipped}</p><p className="text-xs text-text-muted">Allerede registrert</p></div>
                <div className="text-center"><p className="text-xl font-bold text-text-primary">{enrollmentResult.total}</p><p className="text-xs text-text-muted">Totalt godkjent</p></div>
              </div>
            </div>
          )}

          {/* Applicant overview */}
          <ApplicantOverview periodId={period.id} />

          {/* Requirements modal */}
          {showRequirements && (
            <RequirementsModal periodId={period.id} onClose={() => setShowRequirements(false)} />
          )}
        </div>
      )}
    </div>
  );
}

// ── Applicant Overview ─────────────────────────────────────────────────────────
function ApplicantOverview({ periodId }: { periodId: number }) {
  const { data: overview, isLoading } = useQuery({
    queryKey: ['admissionOverview', periodId],
    queryFn: () => getAdmissionOverview(periodId),
  });

  if (isLoading) return <p className="text-sm text-text-muted">Laster søkeroversikt...</p>;
  if (!overview || overview.length === 0) return <p className="text-sm text-text-muted italic">Ingen programmer med krav satt</p>;

  const appStatusIcon = (status: string) => {
    switch (status) {
      case 'ACCEPTED': return <CheckCircle2 size={13} className="text-green-400" />;
      case 'ENROLLED': return <CheckCircle2 size={13} className="text-accent" />;
      case 'WAITLISTED': return <AlertTriangle size={13} className="text-orange-400" />;
      case 'REJECTED': return <XCircle size={13} className="text-red-400" />;
      case 'WITHDRAWN': return <X size={13} className="text-text-muted" />;
      default: return <Clock size={13} className="text-yellow-400" />;
    }
  };

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-semibold text-text-secondary uppercase tracking-wide flex items-center gap-2">
        <Users size={14} /> Søkeroversikt
      </h4>
      {overview.map(prog => (
        <div key={prog.programId} className="border border-border rounded-lg overflow-hidden">
          <div className="px-4 py-3 bg-bg-hover/30 flex items-center justify-between">
            <div>
              <span className="text-sm font-medium text-text-primary">{prog.programName}</span>
              <span className="ml-2 text-xs text-text-muted">
                {prog.totalApplicants} søkere
                {prog.maxStudents != null ? ` / ${prog.maxStudents} plasser` : ''}
                {prog.minGpa != null ? ` · Min snitt: ${prog.minGpa}` : ''}
              </span>
            </div>
          </div>
          {prog.applicants.length > 0 && (
            <div className="divide-y divide-border/30">
              {prog.applicants.map(a => (
                <div key={a.applicationId} className="flex items-center justify-between px-4 py-2.5">
                  <div className="flex items-center gap-3">
                    {appStatusIcon(a.status)}
                    <div>
                      <span className="text-sm text-text-primary">{a.fullName}</span>
                      <span className="text-xs text-text-muted ml-2">@{a.username}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 text-xs">
                    <span className="text-text-muted">Prioritet {a.priority}</span>
                    <span className="font-mono text-text-primary">{a.gpaSnapshot != null ? a.gpaSnapshot.toFixed(2) : '-'}</span>
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      a.status === 'ACCEPTED' ? 'bg-green-400/10 text-green-400' :
                      a.status === 'ENROLLED' ? 'bg-accent/10 text-accent' :
                      a.status === 'WAITLISTED' ? 'bg-orange-400/10 text-orange-400' :
                      a.status === 'REJECTED' ? 'bg-red-400/10 text-red-400' :
                      'bg-bg-hover text-text-muted'
                    }`}>{a.status === 'ENROLLED' ? 'Innmeldt' : a.status}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

// ── Requirements Modal ─────────────────────────────────────────────────────────
function RequirementsModal({ periodId, onClose }: { periodId: number; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selectedProgram, setSelectedProgram] = useState<number | null>(null);
  const [minGpa, setMinGpa] = useState('');
  const [maxStudents, setMaxStudents] = useState('');

  const { data: requirements = [], isLoading } = useQuery({
    queryKey: ['admissionRequirements', periodId],
    queryFn: () => getAdmissionRequirements(periodId),
  });

  const { data: programs = [] } = useQuery({
    queryKey: ['programs'],
    queryFn: getPrograms,
  });

  const existingProgramIds = new Set(requirements.map(r => r.programId));
  const availablePrograms = programs.filter(p => !existingProgramIds.has(p.id));

  const saveMut = useMutation({
    mutationFn: () => setAdmissionRequirement(periodId, {
      programId: selectedProgram!,
      minGpa: minGpa ? parseFloat(minGpa) : null,
      maxStudents: maxStudents ? parseInt(maxStudents) : null,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admissionRequirements', periodId] });
      queryClient.invalidateQueries({ queryKey: ['admissionOverview', periodId] });
      toast.success('Krav lagret');
      setSelectedProgram(null); setMinGpa(''); setMaxStudents('');
    },
    onError: () => toast.error('Kunne ikke lagre krav'),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-md shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <h2 className="text-lg font-semibold text-text-primary">Opptakskrav</h2>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary"><X size={18} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-4">
          {/* Existing requirements */}
          {requirements.length > 0 && (
            <div className="space-y-2">
              {requirements.map(r => (
                <div key={r.id} className="flex items-center justify-between px-3 py-2.5 border border-border rounded-lg">
                  <span className="text-sm font-medium text-text-primary">{r.programName}</span>
                  <div className="text-xs text-text-muted space-x-2">
                    {r.minGpa != null && <span>Min snitt: {r.minGpa}</span>}
                    {r.maxStudents != null && <span>Maks: {r.maxStudents}</span>}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Add new requirement */}
          <div className="border-t border-border pt-4 space-y-3">
            <h3 className="text-sm font-semibold text-text-secondary">Legg til krav</h3>
            <select value={selectedProgram ?? ''} onChange={e => setSelectedProgram(e.target.value ? parseInt(e.target.value) : null)}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary">
              <option value="">Velg program...</option>
              {availablePrograms.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            <div className="flex gap-3">
              <div className="flex-1">
                <label className="block text-xs text-text-muted mb-1">Min. snittkarakter</label>
                <input type="number" step="0.1" min="1" max="6" value={minGpa} onChange={e => setMinGpa(e.target.value)}
                  placeholder="f.eks. 3.0" className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
              </div>
              <div className="flex-1">
                <label className="block text-xs text-text-muted mb-1">Maks antall plasser</label>
                <input type="number" min="1" value={maxStudents} onChange={e => setMaxStudents(e.target.value)}
                  placeholder="f.eks. 30" className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
              </div>
            </div>
            <button onClick={() => saveMut.mutate()} disabled={!selectedProgram || saveMut.isPending}
              className="px-4 py-2 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50">
              {saveMut.isPending ? 'Lagrer...' : 'Lagre krav'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Create Period Modal ────────────────────────────────────────────────────────
function CreatePeriodModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { user } = useAuth();
  const instLevel = user?.institutionLevel || '';

  // Context-aware level options based on the admin's institution
  const FROM_LEVELS: Record<string, { value: string; label: string }[]> = {
    VGS: [
      { value: 'UNGDOMSSKOLE', label: 'Ungdomsskole' },
      { value: 'VGS', label: 'Videregående' },
    ],
    FAGSKOLE: [
      { value: 'VGS', label: 'Videregående' },
      { value: 'FAGSKOLE', label: 'Fagskole' },
    ],
    UNIVERSITET: [
      { value: 'VGS', label: 'Videregående' },
      { value: 'UNIVERSITET', label: 'Universitet/Høyskole' },
    ],
  };
  const TO_LEVELS: Record<string, { value: string; label: string }[]> = {
    VGS: [{ value: 'VGS', label: 'Videregående' }],
    FAGSKOLE: [{ value: 'FAGSKOLE', label: 'Fagskole' }],
    UNIVERSITET: [{ value: 'UNIVERSITET', label: 'Universitet/Høyskole' }],
  };

  const fromOptions = FROM_LEVELS[instLevel] || Object.values(LEVEL_LABELS).map((label, i) => ({
    value: Object.keys(LEVEL_LABELS)[i], label
  }));
  const toOptions = TO_LEVELS[instLevel] || Object.entries(LEVEL_LABELS)
    .filter(([k]) => k !== 'UNGDOMSSKOLE')
    .map(([value, label]) => ({ value, label }));

  const [form, setForm] = useState({
    name: '', fromLevel: '', toLevel: toOptions.length === 1 ? toOptions[0].value : '', startDate: '', endDate: '', maxChoices: 5,
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      await createAdmissionPeriod(form);
      toast.success('Opptaksperiode opprettet');
      onCreated();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Kunne ikke opprette');
    } finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">Ny opptaksperiode</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-sm text-text-secondary mb-1">Navn</label>
            <input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
              placeholder="f.eks. Opptak VGS 2026" className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
          </div>
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Fra nivå</label>
              <select required value={form.fromLevel} onChange={e => setForm({ ...form, fromLevel: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary">
                <option value="">Velg...</option>
                {fromOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </div>
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Til nivå</label>
              <select required value={form.toLevel} onChange={e => setForm({ ...form, toLevel: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary">
                <option value="">Velg...</option>
                {toOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </div>
          </div>
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Startdato</label>
              <input type="date" required value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
            </div>
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Sluttdato</label>
              <input type="date" required value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
            </div>
          </div>
          <div>
            <label className="block text-sm text-text-secondary mb-1">Maks antall valg</label>
            <input type="number" min="1" max="10" value={form.maxChoices} onChange={e => setForm({ ...form, maxChoices: parseInt(e.target.value) || 5 })}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary" />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm border border-border text-text-secondary rounded-lg hover:bg-bg-hover">Avbryt</button>
            <button type="submit" disabled={loading}
              className="px-4 py-2 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg disabled:opacity-50">
              {loading ? 'Oppretter...' : 'Opprett'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
