import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getAvailablePeriods, getMyApplications, submitApplications, withdrawApplication } from '../api/admissions';
import type { ApplicationResponse, AdmissionPeriod } from '../api/admissions';
import { getPrograms } from '../api/programs';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { FileSearch, Send, X, ChevronDown, ChevronRight, GripVertical, XCircle, CheckCircle2, Clock, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  PENDING: { label: 'Venter', color: 'text-yellow-400 bg-yellow-400/10', icon: <Clock size={14} /> },
  ACCEPTED: { label: 'Godkjent', color: 'text-green-400 bg-green-400/10', icon: <CheckCircle2 size={14} /> },
  ENROLLED: { label: 'Innmeldt', color: 'text-accent bg-accent/10', icon: <CheckCircle2 size={14} /> },
  WAITLISTED: { label: 'Venteliste', color: 'text-orange-400 bg-orange-400/10', icon: <AlertTriangle size={14} /> },
  REJECTED: { label: 'Avslått', color: 'text-red-400 bg-red-400/10', icon: <XCircle size={14} /> },
  WITHDRAWN: { label: 'Trukket', color: 'text-text-muted bg-bg-hover', icon: <X size={14} /> },
};

export function AdmissionsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [showApply, setShowApply] = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState<AdmissionPeriod | null>(null);
  const [withdrawTarget, setWithdrawTarget] = useState<ApplicationResponse | null>(null);

  const { data: myApps = [], isLoading: loadingApps } = useQuery({
    queryKey: ['myApplications'],
    queryFn: getMyApplications,
  });

  const { data: periods = [], isLoading: loadingPeriods } = useQuery({
    queryKey: ['availablePeriods'],
    queryFn: getAvailablePeriods,
    enabled: showApply,
  });

  const withdrawMut = useMutation({
    mutationFn: (id: number) => withdrawApplication(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myApplications'] });
      toast.success('Søknad trukket');
      setWithdrawTarget(null);
    },
    onError: () => toast.error('Kunne ikke trekke søknad'),
  });

  // Group applications by period
  const groupedApps = myApps.reduce<Record<string, ApplicationResponse[]>>((acc, app) => {
    const key = `${app.periodId}-${app.periodName}`;
    if (!acc[key]) acc[key] = [];
    acc[key].push(app);
    return acc;
  }, {});

  if (loadingApps) return <LoadingState message="Laster søknader..." />;

  return (
    <div>
      <PageHeader title="Mine Søknader" description="Søk på linjer, degrees og utdanninger"
        action={
          <button onClick={() => setShowApply(true)}
            className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors">
            <Send size={16} /> Ny søknad
          </button>
        }
      />

      {Object.keys(groupedApps).length === 0 ? (
        <EmptyState title="Ingen søknader ennå" />
      ) : (
        <div className="space-y-6">
          {Object.entries(groupedApps).map(([key, apps]) => {
            const gpa = apps[0]?.gpaSnapshot;
            return (
              <div key={key} className="bg-bg-card border border-border rounded-xl overflow-hidden">
                <div className="px-5 py-4 border-b border-border/50 flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-text-primary">{apps[0].periodName}</h3>
                    <p className="text-xs text-text-muted">{apps[0].institutionName}{gpa != null ? ` · Snitt: ${gpa}` : ''}</p>
                  </div>
                </div>
                <div className="divide-y divide-border/30">
                  {apps.sort((a, b) => a.priority - b.priority).map(app => {
                    const statusConf = STATUS_CONFIG[app.status] || STATUS_CONFIG.PENDING;
                    return (
                      <div key={app.id} className="flex items-center justify-between px-5 py-3 hover:bg-bg-hover/30 transition-colors">
                        <div className="flex items-center gap-4">
                          <span className="w-8 h-8 rounded-lg bg-accent/10 text-accent flex items-center justify-center text-sm font-bold">{app.priority}</span>
                          <div>
                            <span className="text-sm font-medium text-text-primary">{app.programName}</span>
                            {app.submittedAt && <p className="text-xs text-text-muted">Sendt: {new Date(app.submittedAt).toLocaleDateString('nb-NO')}</p>}
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium ${statusConf.color}`}>
                            {statusConf.icon} {statusConf.label}
                          </span>
                          {(app.status === 'PENDING' || app.status === 'ACCEPTED') && (
                            <button onClick={() => setWithdrawTarget(app)}
                              className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors" title="Trekk søknad">
                              <XCircle size={14} />
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Apply Modal */}
      {showApply && (
        <ApplyModal periods={periods} loadingPeriods={loadingPeriods}
          onClose={() => { setShowApply(false); setSelectedPeriod(null); }}
          onApplied={() => { setShowApply(false); queryClient.invalidateQueries({ queryKey: ['myApplications'] }); }} />
      )}

      <ConfirmDialog open={!!withdrawTarget} title="Trekk søknad"
        message={`Er du sikker på at du vil trekke søknaden til "${withdrawTarget?.programName}"?`}
        onConfirm={() => withdrawTarget && withdrawMut.mutate(withdrawTarget.id)}
        onCancel={() => setWithdrawTarget(null)} loading={withdrawMut.isPending} />
    </div>
  );
}

// ── Apply Modal ────────────────────────────────────────────────────────────────
function ApplyModal({ periods, loadingPeriods, onClose, onApplied }: {
  periods: AdmissionPeriod[]; loadingPeriods: boolean;
  onClose: () => void; onApplied: () => void;
}) {
  const [selectedPeriod, setSelectedPeriod] = useState<AdmissionPeriod | null>(null);
  const [choices, setChoices] = useState<{ programId: number; programName: string }[]>([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { data: programs = [] } = useQuery({
    queryKey: ['allPrograms'],
    queryFn: getPrograms,
    enabled: !!selectedPeriod,
  });

  // Filter programs to those belonging to the selected period's institution
  const availablePrograms = programs.filter(p =>
    selectedPeriod && p.institutionId === selectedPeriod.institutionId &&
    !choices.some(c => c.programId === p.id)
  );

  const addChoice = (programId: number, programName: string) => {
    if (selectedPeriod && choices.length >= selectedPeriod.maxChoices) {
      toast.error(`Maks ${selectedPeriod.maxChoices} valg`);
      return;
    }
    setChoices([...choices, { programId, programName }]);
  };

  const removeChoice = (idx: number) => {
    setChoices(choices.filter((_, i) => i !== idx));
  };

  const moveChoice = (idx: number, dir: -1 | 1) => {
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= choices.length) return;
    const updated = [...choices];
    [updated[idx], updated[newIdx]] = [updated[newIdx], updated[idx]];
    setChoices(updated);
  };

  const handleSubmit = async () => {
    if (!selectedPeriod || choices.length === 0) return;
    setError(''); setSubmitting(true);
    try {
      await submitApplications(selectedPeriod.id,
        choices.map((c, i) => ({ programId: c.programId, priority: i + 1 }))
      );
      toast.success(`Søknad sendt med ${choices.length} valg!`);
      onApplied();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Kunne ikke sende søknad');
    } finally { setSubmitting(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Ny søknad</h2>
            <p className="text-sm text-text-muted mt-0.5">Velg opptaksperiode og prioriter dine valg</p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary"><X size={18} /></button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-5">
          {error && <div className="p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}

          {/* Period selection */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Opptaksperiode</label>
            {loadingPeriods ? (
              <p className="text-sm text-text-muted">Laster...</p>
            ) : periods.length === 0 ? (
              <p className="text-sm text-text-muted italic">Ingen åpne opptaksperioder tilgjengelig</p>
            ) : (
              <div className="space-y-1">
                {periods.map(p => (
                  <button key={p.id} onClick={() => { setSelectedPeriod(p); setChoices([]); }}
                    className={`w-full text-left px-3 py-2.5 rounded-lg border transition-colors ${
                      selectedPeriod?.id === p.id ? 'border-accent bg-accent/10' : 'border-border hover:bg-bg-hover'
                    }`}>
                    <div className="text-sm font-medium text-text-primary">{p.name}</div>
                    <div className="text-xs text-text-muted">{p.institutionName} · Frist: {p.endDate} · Maks {p.maxChoices} valg</div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Choices */}
          {selectedPeriod && (
            <>
              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1.5">
                  Dine valg ({choices.length}/{selectedPeriod.maxChoices})
                </label>
                {choices.length === 0 ? (
                  <p className="text-sm text-text-muted italic">Legg til programmer fra listen under</p>
                ) : (
                  <div className="space-y-1">
                    {choices.map((c, i) => (
                      <div key={c.programId} className="flex items-center gap-2 px-3 py-2 bg-accent/5 border border-accent/20 rounded-lg">
                        <span className="w-7 h-7 rounded bg-accent/20 text-accent flex items-center justify-center text-xs font-bold shrink-0">{i + 1}</span>
                        <span className="text-sm font-medium text-text-primary flex-1">{c.programName}</span>
                        <div className="flex gap-1 shrink-0">
                          <button onClick={() => moveChoice(i, -1)} disabled={i === 0} className="p-1 text-text-muted hover:text-text-primary disabled:opacity-30">↑</button>
                          <button onClick={() => moveChoice(i, 1)} disabled={i === choices.length - 1} className="p-1 text-text-muted hover:text-text-primary disabled:opacity-30">↓</button>
                          <button onClick={() => removeChoice(i)} className="p-1 text-text-muted hover:text-danger"><X size={14} /></button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-text-secondary mb-1.5">Tilgjengelige programmer</label>
                {availablePrograms.length === 0 ? (
                  <p className="text-sm text-text-muted italic">Ingen flere programmer tilgjengelig</p>
                ) : (
                  <div className="border border-border rounded-lg max-h-40 overflow-y-auto">
                    {availablePrograms.map(p => (
                      <button key={p.id} onClick={() => addChoice(p.id, p.name)}
                        className="w-full text-left px-3 py-2.5 hover:bg-bg-hover transition-colors border-b border-border/30 last:border-b-0 flex items-center justify-between">
                        <span className="text-sm font-medium text-text-primary">{p.name}</span>
                        <span className="text-xs text-accent">+ Legg til</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <div className="border-t border-border p-5 flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
          <button onClick={handleSubmit} disabled={!selectedPeriod || choices.length === 0 || submitting}
            className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">
            {submitting ? 'Sender...' : `Send søknad (${choices.length} valg)`}
          </button>
        </div>
      </div>
    </div>
  );
}
