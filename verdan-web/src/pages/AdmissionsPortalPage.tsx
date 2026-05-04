import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPortalListings,
  getMyApplications,
  submitApplications,
  withdrawApplication,
  confirmApplication,
  getAdmissionPeriods,
  createAdmissionPeriod,
  closeAdmissionPeriod,
  deleteAdmissionPeriod,
  reopenAdmissionPeriod,
  bulkPublishPrograms,
  processAdmissions,
  getAdmissionOverview,
  getAdmissionRequirements,
  setAdmissionRequirement,
  enrollAccepted,
} from '../api/admissions';
import { getMyGraduation, getPrograms } from '../api/programs';
import type { Program } from '../types';
import type {
  PortalListing,
  ApplicationResponse,
  AdmissionPeriod,
  ProgramApplicantSummary,
  AdmissionRequirement,
  ProcessingResult,
} from '../api/admissions';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  Search,
  X,
  Send,
  XCircle,
  CheckCircle2,
  Clock,
  AlertTriangle,
  GraduationCap,
  Building2,
  Plus,
  ChevronDown,
  ChevronRight,
  Settings,
  Play,
  Lock,
  Users,
  Filter,
  Trash2,
  RotateCcw,
  Upload,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';

const LEVEL_LABELS: Record<string, string> = {
  UNGDOMSSKOLE: 'Ungdomsskole',
  VGS: 'Videregående',
  FAGSKOLE: 'Fagskole',
  UNIVERSITET: 'Universitet/Høyskole',
};

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  PENDING: {
    label: 'Venter',
    color: 'text-yellow-400 bg-yellow-400/10',
    icon: <Clock size={14} />,
  },
  ACCEPTED: {
    label: 'Godkjent',
    color: 'text-green-400 bg-green-400/10',
    icon: <CheckCircle2 size={14} />,
  },
  ENROLLED: {
    label: 'Innmeldt',
    color: 'text-accent bg-accent/10',
    icon: <CheckCircle2 size={14} />,
  },
  WAITLISTED: {
    label: 'Venteliste',
    color: 'text-orange-400 bg-orange-400/10',
    icon: <AlertTriangle size={14} />,
  },
  REJECTED: { label: 'Avslått', color: 'text-red-400 bg-red-400/10', icon: <XCircle size={14} /> },
  WITHDRAWN: { label: 'Trukket', color: 'text-text-muted bg-bg-hover', icon: <X size={14} /> },
  CONFIRMED: {
    label: 'Bekreftet',
    color: 'text-emerald-400 bg-emerald-400/10',
    icon: <CheckCircle2 size={14} />,
  },
};

export function AdmissionsPortalPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = user?.role === 'INSTITUTION_ADMIN' || user?.role === 'SUPER_ADMIN';
  const isStudent = user?.role === 'STUDENT';

  const [tab, setTab] = useState<'portal' | 'mine' | 'admin'>(isAdmin ? 'admin' : 'portal');
  const [search, setSearch] = useState('');
  const [levelFilter, setLevelFilter] = useState('');
  const [showCreate, setShowCreate] = useState(false);

  // For students: check if they are graduated (uteksaminert)
  const { data: graduationStatus, isLoading: gradLoading } = useQuery({
    queryKey: ['myGraduation'],
    queryFn: getMyGraduation,
    enabled: isStudent,
  });

  const isGraduated = !isStudent || graduationStatus?.graduated === true;

  // If student is not graduated, show restricted message
  if (isStudent && !gradLoading && !isGraduated) {
    return (
      <div>
        <PageHeader
          title="Søknadsportal"
          description="Felles opptaksplattform for alle institusjoner"
        />
        <div className="flex flex-col items-center justify-center py-20">
          <div className="w-16 h-16 rounded-2xl bg-accent/10 flex items-center justify-center mb-4">
            <Lock size={32} className="text-accent" />
          </div>
          <h2 className="text-xl font-semibold text-text-primary mb-2">
            Søknadsportalen er ikke tilgjengelig ennå
          </h2>
          <p className="text-text-muted text-center max-w-md">
            Søknadsportalen åpnes når du er uteksaminert fra din nåværende institusjon. Dine
            karakterer vil automatisk brukes når du søker.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Søknadsportal"
        description="Felles opptaksplattform for alle institusjoner"
        action={
          isAdmin && tab === 'admin' ? (
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"
            >
              <Plus size={16} /> Ny opptaksperiode
            </button>
          ) : undefined
        }
      />

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-bg-secondary border border-border rounded-lg p-1 w-fit">
        <TabButton active={tab === 'portal'} onClick={() => setTab('portal')}>
          <Building2 size={15} /> Utforsk
        </TabButton>
        {isStudent && (
          <TabButton active={tab === 'mine'} onClick={() => setTab('mine')}>
            <GraduationCap size={15} /> Mine søknader
          </TabButton>
        )}
        {isAdmin && (
          <TabButton active={tab === 'admin'} onClick={() => setTab('admin')}>
            <Settings size={15} /> Administrer
          </TabButton>
        )}
      </div>

      {tab === 'portal' && (
        <PortalBrowse
          search={search}
          setSearch={setSearch}
          levelFilter={levelFilter}
          setLevelFilter={setLevelFilter}
        />
      )}
      {tab === 'mine' && <MyApplications />}
      {tab === 'admin' && <AdminPanel showCreate={showCreate} setShowCreate={setShowCreate} />}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
        active
          ? 'bg-accent text-white shadow-sm'
          : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
      }`}
    >
      {children}
    </button>
  );
}

// ══════════════════════════════════════════════════════════════════════════════
//  TAB 1: Portal Browse — search all institutions and programs
// ══════════════════════════════════════════════════════════════════════════════
function PortalBrowse({
  search,
  setSearch,
  levelFilter,
  setLevelFilter,
}: {
  search: string;
  setSearch: (s: string) => void;
  levelFilter: string;
  setLevelFilter: (s: string) => void;
}) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [applyTarget, setApplyTarget] = useState<PortalListing | null>(null);
  const [ownershipFilter, setOwnershipFilter] = useState<'' | 'PUBLIC' | 'PRIVATE'>('');

  const { data: listings = [], isLoading } = useQuery({
    queryKey: ['portalListings', user?.institutionLevel],
    queryFn: () => getPortalListings(user?.role === 'STUDENT' ? user?.institutionLevel : undefined),
  });

  const filtered = useMemo(() => {
    const s = search.toLowerCase();
    return listings.filter((l) => {
      const matchSearch =
        !s ||
        l.institutionName.toLowerCase().includes(s) ||
        l.programName.toLowerCase().includes(s) ||
        l.periodName.toLowerCase().includes(s);
      const matchLevel = !levelFilter || l.toLevel === levelFilter;
      const matchOwnership = !ownershipFilter || l.ownership === ownershipFilter;
      return matchSearch && matchLevel && matchOwnership;
    });
  }, [listings, search, levelFilter, ownershipFilter]);

  const publicListings = useMemo(
    () => filtered.filter((l) => l.ownership !== 'PRIVATE'),
    [filtered],
  );
  const privateListings = useMemo(
    () => filtered.filter((l) => l.ownership === 'PRIVATE'),
    [filtered],
  );

  // Group by institution
  const groupByInstitution = (items: PortalListing[]) => {
    const map = new Map<string, PortalListing[]>();
    for (const l of items) {
      const key = l.institutionName;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(l);
    }
    return map;
  };

  const publicGrouped = useMemo(() => groupByInstitution(publicListings), [publicListings]);
  const privateGrouped = useMemo(() => groupByInstitution(privateListings), [privateListings]);

  const levels = [...new Set(listings.map((l) => l.toLevel))];

  if (isLoading) return <LoadingState message="Laster søknadsportal..." />;

  const renderProgramCard = (p: PortalListing) => (
    <div
      key={`${p.periodId}-${p.programId}`}
      className="flex items-center justify-between px-5 py-3.5 hover:bg-bg-hover/30 transition-colors"
    >
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-text-primary">{p.programName}</span>
          <span className="text-xs px-2 py-0.5 rounded-full bg-accent/10 text-accent font-medium">
            {LEVEL_LABELS[p.toLevel] || p.toLevel}
          </span>
        </div>
        <div className="flex items-center gap-3 mt-1 text-xs text-text-muted">
          <span>Frist: {p.endDate}</span>
          {p.minGpa != null && <span>Min. snitt: {p.minGpa}</span>}
          {p.maxStudents != null && <span>Maks: {p.maxStudents} plasser</span>}
          <span>{p.applicantCount} søkere</span>
        </div>
        {p.prerequisites && (
          <div className="flex flex-wrap gap-1 mt-1.5">
            {p.prerequisites
              .split(',')
              .map((t) => t.trim())
              .filter(Boolean)
              .map((tag, i) => (
                <span
                  key={i}
                  className="px-2 py-0.5 bg-orange-400/10 text-orange-400 text-[11px] font-medium rounded-full"
                >
                  {tag}
                </span>
              ))}
          </div>
        )}
      </div>
      {user?.role === 'STUDENT' && (
        <button
          onClick={() => setApplyTarget(p)}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors shrink-0"
        >
          <Send size={13} /> Søk
        </button>
      )}
    </div>
  );

  const renderInstitutionGroup = (
    instName: string,
    programs: PortalListing[],
    ownership: 'PUBLIC' | 'PRIVATE',
  ) => (
    <div key={instName} className="bg-bg-card border border-border rounded-xl overflow-hidden">
      <div className="px-5 py-4 border-b border-border/50 flex items-center gap-3">
        <div
          className={`w-10 h-10 rounded-lg flex items-center justify-center ${
            ownership === 'PRIVATE' ? 'bg-amber-400/10' : 'bg-accent/10'
          }`}
        >
          <Building2
            size={20}
            className={ownership === 'PRIVATE' ? 'text-amber-400' : 'text-accent'}
          />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="text-base font-semibold text-text-primary">{instName}</h3>
            <span
              className={`text-[10px] font-medium px-1.5 py-0.5 rounded ${
                ownership === 'PRIVATE'
                  ? 'bg-amber-400/10 text-amber-400'
                  : 'bg-blue-400/10 text-blue-400'
              }`}
            >
              {ownership === 'PRIVATE' ? 'Privat' : 'Offentlig'}
            </span>
          </div>
          <p className="text-xs text-text-muted">{programs.length} åpne programmer</p>
        </div>
      </div>
      <div className="divide-y divide-border/30">{programs.map(renderProgramCard)}</div>
    </div>
  );

  return (
    <div className="space-y-6">
      {/* Search and filter bar */}
      <div className="flex gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px] max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Søk etter institusjon, program eller linje..."
            className="w-full pl-9 pr-4 py-2.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={15} className="text-text-muted" />
          <select
            value={levelFilter}
            onChange={(e) => setLevelFilter(e.target.value)}
            className="px-3 py-2.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
          >
            <option value="">Alle nivåer</option>
            {levels.map((l) => (
              <option key={l} value={l}>
                {LEVEL_LABELS[l] || l}
              </option>
            ))}
          </select>
          <select
            value={ownershipFilter}
            onChange={(e) => setOwnershipFilter(e.target.value as '' | 'PUBLIC' | 'PRIVATE')}
            className="px-3 py-2.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
          >
            <option value="">Alle typer</option>
            <option value="PUBLIC">Offentlig</option>
            <option value="PRIVATE">Privat</option>
          </select>
        </div>
      </div>

      {/* Stats */}
      <div className="flex gap-4 text-sm text-text-muted">
        <span>{publicGrouped.size + privateGrouped.size} institusjoner</span>
        <span>·</span>
        <span>{filtered.length} programmer</span>
        <span>·</span>
        <span className="text-blue-400">{publicListings.length} offentlig</span>
        <span>·</span>
        <span className="text-amber-400">{privateListings.length} privat</span>
      </div>

      {filtered.length === 0 ? (
        <EmptyState title="Ingen åpne opptak funnet" />
      ) : (
        <div className="space-y-8">
          {/* ── Public institutions section ── */}
          {publicGrouped.size > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-blue-400/10 flex items-center justify-center">
                  <Building2 size={16} className="text-blue-400" />
                </div>
                <div>
                  <h2 className="text-base font-semibold text-text-primary">
                    Offentlige institusjoner
                  </h2>
                  <p className="text-xs text-text-muted">
                    Felles opptakspool — du kan velge opptil 5 programmer på tvers av alle
                    offentlige institusjoner
                  </p>
                </div>
              </div>
              <div className="space-y-4">
                {[...publicGrouped.entries()].map(([instName, programs]) =>
                  renderInstitutionGroup(instName, programs, 'PUBLIC'),
                )}
              </div>
            </div>
          )}

          {/* ── Private institutions section ── */}
          {privateGrouped.size > 0 && (
            <div>
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-amber-400/10 flex items-center justify-center">
                  <Building2 size={16} className="text-amber-400" />
                </div>
                <div>
                  <h2 className="text-base font-semibold text-text-primary">
                    Private institusjoner
                  </h2>
                  <p className="text-xs text-text-muted">
                    Uavhengig opptak — du kan søke fritt på hver privat institusjon
                  </p>
                </div>
              </div>
              <div className="space-y-4">
                {[...privateGrouped.entries()].map(([instName, programs]) =>
                  renderInstitutionGroup(instName, programs, 'PRIVATE'),
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Quick apply modal */}
      {applyTarget && (
        <QuickApplyModal
          listing={applyTarget}
          onClose={() => setApplyTarget(null)}
          onApplied={() => {
            setApplyTarget(null);
            queryClient.invalidateQueries({ queryKey: ['portalListings'] });
          }}
        />
      )}
    </div>
  );
}

// ── Quick Apply Modal ──────────────────────────────────────────────────────────
function QuickApplyModal({
  listing,
  onClose,
  onApplied,
}: {
  listing: PortalListing;
  onClose: () => void;
  onApplied: () => void;
}) {
  const queryClient = useQueryClient();
  const [choices, setChoices] = useState<
    { programId: number; programName: string; institutionName: string }[]
  >([
    {
      programId: listing.programId,
      programName: listing.programName,
      institutionName: listing.institutionName,
    },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isPublic = listing.ownership !== 'PRIVATE';

  const { user } = useAuth();

  // Load all listings (reuse cached filtered query)
  const { data: listings = [] } = useQuery({
    queryKey: ['portalListings', user?.institutionLevel],
    queryFn: () => getPortalListings(user?.role === 'STUDENT' ? user?.institutionLevel : undefined),
  });

  // For PUBLIC: show programs from ALL public institutions with same fromLevel/toLevel
  // For PRIVATE: show programs from the same institution/period only
  const availablePrograms = useMemo(() => {
    if (isPublic) {
      // All public institution programs at the same level that aren't already chosen
      return listings.filter(
        (l) =>
          l.ownership !== 'PRIVATE' &&
          l.fromLevel === listing.fromLevel &&
          l.toLevel === listing.toLevel &&
          !choices.some((c) => c.programId === l.programId),
      );
    } else {
      // Same institution/period only
      return listings.filter(
        (l) => l.periodId === listing.periodId && !choices.some((c) => c.programId === l.programId),
      );
    }
  }, [listings, listing, choices, isPublic]);

  const maxChoices = listing.maxChoices;

  const addChoice = (l: PortalListing) => {
    if (choices.length >= maxChoices) {
      toast.error(`Maks ${maxChoices} valg`);
      return;
    }
    setChoices([
      ...choices,
      { programId: l.programId, programName: l.programName, institutionName: l.institutionName },
    ]);
  };

  const removeChoice = (idx: number) => setChoices(choices.filter((_, i) => i !== idx));
  const moveChoice = (idx: number, dir: -1 | 1) => {
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= choices.length) return;
    const updated = [...choices];
    [updated[idx], updated[newIdx]] = [updated[newIdx], updated[idx]];
    setChoices(updated);
  };

  const handleSubmit = async () => {
    setError('');
    setSubmitting(true);
    try {
      await submitApplications(
        listing.periodId,
        choices.map((c, i) => ({ programId: c.programId, priority: i + 1 })),
      );
      toast.success(`Søknad sendt med ${choices.length} valg!`);
      queryClient.invalidateQueries({ queryKey: ['myApplications'] });
      onApplied();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Kunne ikke sende søknad');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-text-primary">
                {isPublic ? 'Samordna opptak' : `Søk til ${listing.institutionName}`}
              </h2>
              <span
                className={`text-[10px] font-medium px-1.5 py-0.5 rounded ${
                  isPublic ? 'bg-blue-400/10 text-blue-400' : 'bg-amber-400/10 text-amber-400'
                }`}
              >
                {isPublic ? 'Offentlig' : 'Privat'}
              </span>
            </div>
            <p className="text-sm text-text-muted mt-0.5">
              {isPublic
                ? `Velg opptil ${maxChoices} programmer på tvers av offentlige institusjoner`
                : `${listing.periodName} · Maks ${maxChoices} valg`}
            </p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary">
            <X size={18} />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-5">
          {error && (
            <div className="p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
              {error}
            </div>
          )}

          {/* Info about which grades are used */}
          <div className="p-3 bg-accent/5 border border-accent/20 rounded-lg text-xs text-text-secondary">
            <span className="font-medium text-accent">ℹ️ Karaktergrunnlag:</span>{' '}
            {listing.fromLevel === 'UNGDOMSSKOLE' && 'Dine ungdomsskolekarakterer brukes.'}
            {listing.fromLevel === 'VGS' && 'Dine videregående-karakterer brukes.'}
            {listing.fromLevel === 'FAGSKOLE' && 'Dine fagskolekarakterer brukes.'}
            {listing.fromLevel === 'UNIVERSITET' &&
              'Dine universitets-/høyskolekarakterer brukes.'}{' '}
            Alle fag må være bestått (karakter ≥ 2 / ≥ E).
          </div>

          {/* Selected choices */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">
              Dine valg ({choices.length}/{maxChoices})
            </label>
            <div className="space-y-1">
              {choices.map((c, i) => (
                <div
                  key={c.programId}
                  className="flex items-center gap-2 px-3 py-2 bg-accent/5 border border-accent/20 rounded-lg"
                >
                  <span className="w-7 h-7 rounded bg-accent/20 text-accent flex items-center justify-center text-xs font-bold shrink-0">
                    {i + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <span className="text-sm font-medium text-text-primary block truncate">
                      {c.programName}
                    </span>
                    {isPublic && (
                      <span className="text-[11px] text-text-muted">{c.institutionName}</span>
                    )}
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <button
                      onClick={() => moveChoice(i, -1)}
                      disabled={i === 0}
                      className="p-1 text-text-muted hover:text-text-primary disabled:opacity-30"
                    >
                      ↑
                    </button>
                    <button
                      onClick={() => moveChoice(i, 1)}
                      disabled={i === choices.length - 1}
                      className="p-1 text-text-muted hover:text-text-primary disabled:opacity-30"
                    >
                      ↓
                    </button>
                    <button
                      onClick={() => removeChoice(i)}
                      className="p-1 text-text-muted hover:text-danger"
                    >
                      <X size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Add more programs */}
          {availablePrograms.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">
                {isPublic
                  ? 'Legg til fra alle offentlige institusjoner'
                  : 'Legg til flere programmer'}
              </label>
              <div className="border border-border rounded-lg max-h-48 overflow-y-auto">
                {availablePrograms.map((l) => (
                  <button
                    key={l.programId}
                    onClick={() => addChoice(l)}
                    className="w-full text-left px-3 py-2.5 hover:bg-bg-hover transition-colors border-b border-border/30 last:border-b-0 flex items-center justify-between"
                  >
                    <div className="min-w-0">
                      <span className="text-sm font-medium text-text-primary block truncate">
                        {l.programName}
                      </span>
                      {isPublic && (
                        <span className="text-[11px] text-text-muted">{l.institutionName}</span>
                      )}
                    </div>
                    <span className="text-xs text-accent shrink-0 ml-2">+ Legg til</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="border-t border-border p-5 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
          >
            Avbryt
          </button>
          <button
            onClick={handleSubmit}
            disabled={choices.length === 0 || submitting}
            className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50"
          >
            {submitting ? 'Sender...' : `Send søknad (${choices.length} valg)`}
          </button>
        </div>
      </div>
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════════════════
//  TAB 2: My Applications (Student)
// ══════════════════════════════════════════════════════════════════════════════
function MyApplications() {
  const queryClient = useQueryClient();
  const [withdrawTarget, setWithdrawTarget] = useState<ApplicationResponse | null>(null);

  const { data: myApps = [], isLoading } = useQuery({
    queryKey: ['myApplications'],
    queryFn: getMyApplications,
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

  const confirmMut = useMutation({
    mutationFn: (id: number) => confirmApplication(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myApplications'] });
      toast.success('Tilbud bekreftet! Andre aksepterte tilbud er trukket automatisk.');
    },
    onError: () => toast.error('Kunne ikke bekrefte tilbud'),
  });

  // Group by period
  const grouped = useMemo(() => {
    const map = new Map<string, ApplicationResponse[]>();
    for (const a of myApps) {
      const key = `${a.periodId}`;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(a);
    }
    return map;
  }, [myApps]);

  if (isLoading) return <LoadingState message="Laster søknader..." />;

  if (myApps.length === 0) return <EmptyState title="Du har ikke sendt noen søknader ennå" />;

  return (
    <div className="space-y-6">
      {[...grouped.entries()].map(([periodId, apps]) => {
        const gpa = apps[0]?.gpaSnapshot;
        return (
          <div
            key={periodId}
            className="bg-bg-card border border-border rounded-xl overflow-hidden"
          >
            <div className="px-5 py-4 border-b border-border/50 flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold text-text-primary">{apps[0].periodName}</h3>
                <p className="text-xs text-text-muted">
                  {apps[0].institutionName}
                  {gpa != null ? ` · Snitt: ${gpa}` : ''}
                </p>
              </div>
            </div>
            <div className="divide-y divide-border/30">
              {apps
                .sort((a, b) => a.priority - b.priority)
                .map((app) => {
                  const sc = STATUS_CONFIG[app.status] || STATUS_CONFIG.PENDING;
                  return (
                    <div
                      key={app.id}
                      className="flex items-center justify-between px-5 py-3 hover:bg-bg-hover/30 transition-colors"
                    >
                      <div className="flex items-center gap-4">
                        <span className="w-8 h-8 rounded-lg bg-accent/10 text-accent flex items-center justify-center text-sm font-bold">
                          {app.priority}
                        </span>
                        <div>
                          <span className="text-sm font-medium text-text-primary">
                            {app.programName}
                          </span>
                          {app.submittedAt && (
                            <p className="text-xs text-text-muted">
                              Sendt: {new Date(app.submittedAt).toLocaleDateString('nb-NO')}
                            </p>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span
                          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium ${sc.color}`}
                        >
                          {sc.icon} {sc.label}
                        </span>
                        {app.status === 'ACCEPTED' && (
                          <>
                            <button
                              onClick={() => confirmMut.mutate(app.id)}
                              disabled={confirmMut.isPending}
                              className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium bg-green-500 hover:bg-green-600 text-white transition-colors disabled:opacity-50"
                            >
                              <CheckCircle2 size={12} /> Bekreft tilbud
                            </button>
                            <button
                              onClick={() => setWithdrawTarget(app)}
                              className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 transition-colors"
                            >
                              <XCircle size={12} /> Avslå tilbud
                            </button>
                          </>
                        )}
                        {app.status === 'PENDING' && (
                          <button
                            onClick={() => setWithdrawTarget(app)}
                            className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"
                            title="Trekk søknad"
                          >
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

      <ConfirmDialog
        open={!!withdrawTarget}
        title="Trekk søknad"
        message={`Er du sikker på at du vil trekke søknaden til "${withdrawTarget?.programName}"?`}
        onConfirm={() => withdrawTarget && withdrawMut.mutate(withdrawTarget.id)}
        onCancel={() => setWithdrawTarget(null)}
        loading={withdrawMut.isPending}
      />
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════════════════
//  TAB 3: Admin Panel — manage periods, requirements, processing
// ══════════════════════════════════════════════════════════════════════════════
function AdminPanel({
  showCreate,
  setShowCreate,
}: {
  showCreate: boolean;
  setShowCreate: (v: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [expandedPeriod, setExpandedPeriod] = useState<number | null>(null);

  const { data: periods = [], isLoading } = useQuery({
    queryKey: ['admissionPeriods'],
    queryFn: getAdmissionPeriods,
  });

  if (isLoading) return <LoadingState message="Laster opptaksperioder..." />;

  return (
    <div className="space-y-4">
      {periods.length === 0 ? (
        <EmptyState title="Ingen opptaksperioder opprettet ennå" />
      ) : (
        <div className="space-y-4">
          {periods.map((period) => (
            <PeriodCard
              key={period.id}
              period={period}
              expanded={expandedPeriod === period.id}
              onToggle={() => setExpandedPeriod(expandedPeriod === period.id ? null : period.id)}
            />
          ))}
        </div>
      )}

      {showCreate && (
        <CreatePeriodModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] });
          }}
        />
      )}
    </div>
  );
}

// ── Period Card ────────────────────────────────────────────────────────────────
const STATUS_BADGE: Record<string, { label: string; class: string }> = {
  OPEN: { label: 'Åpen', class: 'text-green-400 bg-green-400/10 border-green-400/20' },
  CLOSED: { label: 'Stengt', class: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/20' },
  PROCESSED: { label: 'Behandlet', class: 'text-accent bg-accent/10 border-accent/20' },
};

function PeriodCard({
  period,
  expanded,
  onToggle,
}: {
  period: AdmissionPeriod;
  expanded: boolean;
  onToggle: () => void;
}) {
  const queryClient = useQueryClient();
  const [showRequirements, setShowRequirements] = useState(false);
  const [processingResult, setProcessingResult] = useState<ProcessingResult | null>(null);
  const [enrollmentResult, setEnrollmentResult] = useState<{
    enrolled: number;
    skipped: number;
    total: number;
  } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admissionPeriods'] });
    queryClient.invalidateQueries({ queryKey: ['portalListings'] });
  };

  const closeMut = useMutation({
    mutationFn: () => closeAdmissionPeriod(period.id),
    onSuccess: () => {
      invalidateAll();
      toast.success('Opptaksperiode stengt');
    },
    onError: () => toast.error('Kunne ikke stenge periode'),
  });

  const reopenMut = useMutation({
    mutationFn: () => reopenAdmissionPeriod(period.id),
    onSuccess: () => {
      invalidateAll();
      toast.success('Opptaksperiode gjenåpnet');
    },
    onError: () => toast.error('Kunne ikke gjenåpne'),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteAdmissionPeriod(period.id),
    onSuccess: () => {
      invalidateAll();
      toast.success('Opptaksperiode slettet');
      setConfirmDelete(false);
    },
    onError: () => toast.error('Kunne ikke slette'),
  });

  const processMut = useMutation({
    mutationFn: () => processAdmissions(period.id),
    onSuccess: (result) => {
      setProcessingResult(result);
      invalidateAll();
      toast.success('Opptak behandlet!');
    },
    onError: () => toast.error('Kunne ikke behandle opptak'),
  });

  const enrollMut = useMutation({
    mutationFn: () => enrollAccepted(period.id),
    onSuccess: (result) => {
      setEnrollmentResult(result);
      invalidateAll();
      toast.success(`${result.enrolled} elever innmeldt!`);
    },
    onError: () => toast.error('Kunne ikke melde inn elever'),
  });

  const badge = STATUS_BADGE[period.status] || STATUS_BADGE.OPEN;

  return (
    <div className="bg-bg-card border border-border rounded-xl overflow-hidden">
      <button
        onClick={onToggle}
        className="w-full text-left px-5 py-4 flex items-center justify-between hover:bg-bg-hover/30 transition-colors"
      >
        <div className="flex items-center gap-3">
          {expanded ? (
            <ChevronDown size={16} className="text-text-muted" />
          ) : (
            <ChevronRight size={16} className="text-text-muted" />
          )}
          <div>
            <h3 className="text-base font-semibold text-text-primary">{period.name}</h3>
            <p className="text-xs text-text-muted mt-0.5">
              {LEVEL_LABELS[period.fromLevel] || period.fromLevel} →{' '}
              {LEVEL_LABELS[period.toLevel] || period.toLevel} ·{period.startDate} –{' '}
              {period.endDate} · {period.applicationCount} søknader
            </p>
          </div>
        </div>
        <span className={`px-2.5 py-1 rounded-lg text-xs font-medium border ${badge.class}`}>
          {badge.label}
        </span>
      </button>

      {expanded && (
        <div className="border-t border-border px-5 py-4 space-y-4">
          <div className="flex gap-2 flex-wrap">
            {period.status === 'OPEN' && (
              <button
                onClick={() => closeMut.mutate()}
                disabled={closeMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors"
              >
                <Lock size={13} /> Steng søknadsfrist
              </button>
            )}
            {(period.status === 'CLOSED' || period.status === 'PROCESSED') && (
              <button
                onClick={() => reopenMut.mutate()}
                disabled={reopenMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors"
              >
                <RotateCcw size={13} /> Gjenåpne
              </button>
            )}
            <button
              onClick={() => setShowRequirements(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors"
            >
              <Settings size={13} /> Publiser programmer
            </button>
            {(period.status === 'CLOSED' || period.status === 'OPEN') && (
              <button
                onClick={() => processMut.mutate()}
                disabled={processMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50"
              >
                <Play size={13} /> {processMut.isPending ? 'Behandler...' : 'Kjør opptaksalgoritme'}
              </button>
            )}
            {period.status === 'PROCESSED' && (
              <button
                onClick={() => enrollMut.mutate()}
                disabled={enrollMut.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-green-500 hover:bg-green-600 text-white rounded-lg transition-colors disabled:opacity-50"
              >
                <CheckCircle2 size={13} />{' '}
                {enrollMut.isPending ? 'Melder inn...' : 'Meld inn bekreftede elever'}
              </button>
            )}
            <button
              onClick={() => setConfirmDelete(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-danger/30 rounded-lg text-danger hover:bg-danger/10 transition-colors ml-auto"
            >
              <Trash2 size={13} /> Slett
            </button>
          </div>

          {processingResult && (
            <div className="p-4 bg-accent/5 border border-accent/20 rounded-lg">
              <h4 className="text-sm font-semibold text-accent mb-2">Opptaksresultat</h4>
              <div className="flex gap-6">
                <div className="text-center">
                  <p className="text-xl font-bold text-green-400">{processingResult.accepted}</p>
                  <p className="text-xs text-text-muted">Godkjent</p>
                </div>
                <div className="text-center">
                  <p className="text-xl font-bold text-orange-400">{processingResult.waitlisted}</p>
                  <p className="text-xs text-text-muted">Venteliste</p>
                </div>
                <div className="text-center">
                  <p className="text-xl font-bold text-red-400">{processingResult.rejected}</p>
                  <p className="text-xs text-text-muted">Avslått</p>
                </div>
                <div className="text-center">
                  <p className="text-xl font-bold text-text-primary">{processingResult.total}</p>
                  <p className="text-xs text-text-muted">Totalt</p>
                </div>
              </div>
            </div>
          )}

          {enrollmentResult && (
            <div className="p-4 bg-green-400/5 border border-green-400/20 rounded-lg">
              <h4 className="text-sm font-semibold text-green-400 mb-2">Innmeldingsresultat</h4>
              <div className="flex gap-6">
                <div className="text-center">
                  <p className="text-xl font-bold text-green-400">{enrollmentResult.enrolled}</p>
                  <p className="text-xs text-text-muted">Innmeldt</p>
                </div>
                <div className="text-center">
                  <p className="text-xl font-bold text-orange-400">{enrollmentResult.skipped}</p>
                  <p className="text-xs text-text-muted">Hoppet over</p>
                </div>
                <div className="text-center">
                  <p className="text-xl font-bold text-text-primary">{enrollmentResult.total}</p>
                  <p className="text-xs text-text-muted">Totalt akseptert</p>
                </div>
              </div>
              <p className="text-xs text-text-muted mt-2">
                Elever er overført til ny institusjon og innmeldt i programmer.
              </p>
            </div>
          )}

          <ApplicantOverview periodId={period.id} />

          {showRequirements && (
            <RequirementsModal periodId={period.id} onClose={() => setShowRequirements(false)} />
          )}

          <ConfirmDialog
            open={confirmDelete}
            title="Slett opptaksperiode"
            message={`Er du sikker på at du vil slette "${period.name}"? Alle søknader og krav slettes permanent.`}
            onConfirm={() => deleteMut.mutate()}
            onCancel={() => setConfirmDelete(false)}
            loading={deleteMut.isPending}
          />
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
  if (!overview || overview.length === 0)
    return (
      <p className="text-sm text-text-muted italic">
        Ingen programmer publisert ennå. Trykk "Publiser programmer" for å legge ut opptak.
      </p>
    );

  const appStatusIcon = (status: string) => {
    switch (status) {
      case 'ACCEPTED':
        return <CheckCircle2 size={13} className="text-green-400" />;
      case 'CONFIRMED':
        return <CheckCircle2 size={13} className="text-emerald-400" />;
      case 'WAITLISTED':
        return <AlertTriangle size={13} className="text-orange-400" />;
      case 'REJECTED':
        return <XCircle size={13} className="text-red-400" />;
      case 'WITHDRAWN':
        return <X size={13} className="text-text-muted" />;
      default:
        return <Clock size={13} className="text-yellow-400" />;
    }
  };

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-semibold text-text-secondary uppercase tracking-wide flex items-center gap-2">
        <Users size={14} /> Søkeroversikt
      </h4>
      {overview.map((prog) => (
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
              {prog.applicants.map((a) => (
                <div
                  key={a.applicationId}
                  className="flex items-center justify-between px-4 py-2.5"
                >
                  <div className="flex items-center gap-3">
                    {appStatusIcon(a.status)}
                    <div>
                      <span className="text-sm text-text-primary">{a.fullName}</span>
                      <span className="text-xs text-text-muted ml-2">@{a.username}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 text-xs">
                    <span className="text-text-muted">Prioritet {a.priority}</span>
                    <span className="font-mono text-text-primary">
                      {a.gpaSnapshot != null ? a.gpaSnapshot.toFixed(2) : '-'}
                    </span>
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-medium ${
                        a.status === 'CONFIRMED'
                          ? 'bg-emerald-400/10 text-emerald-400'
                          : a.status === 'ACCEPTED'
                            ? 'bg-green-400/10 text-green-400'
                            : a.status === 'WAITLISTED'
                              ? 'bg-orange-400/10 text-orange-400'
                              : a.status === 'REJECTED'
                                ? 'bg-red-400/10 text-red-400'
                                : 'bg-bg-hover text-text-muted'
                      }`}
                    >
                      {a.status === 'CONFIRMED' ? 'BEKREFTET' : a.status}
                    </span>
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

// ── Requirements/Publish Modal ─────────────────────────────────────────────────
function RequirementsModal({ periodId, onClose }: { periodId: number; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selectedProgram, setSelectedProgram] = useState<number | null>(null);
  const [minGpa, setMinGpa] = useState('');
  const [maxStudents, setMaxStudents] = useState('');
  const [bulkLoading, setBulkLoading] = useState(false);

  const { data: requirements = [] } = useQuery({
    queryKey: ['admissionRequirements', periodId],
    queryFn: () => getAdmissionRequirements(periodId),
  });

  const { data: programs = [] } = useQuery<Program[]>({
    queryKey: ['programs'],
    queryFn: getPrograms,
  });

  const existingProgramIds = new Set(requirements.map((r) => r.programId));
  const availablePrograms = programs.filter((p) => !existingProgramIds.has(p.id));

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['admissionRequirements', periodId] });
    queryClient.invalidateQueries({ queryKey: ['admissionOverview', periodId] });
    queryClient.invalidateQueries({ queryKey: ['portalListings'] });
  };

  const saveMut = useMutation({
    mutationFn: () =>
      setAdmissionRequirement(periodId, {
        programId: selectedProgram!,
        minGpa: minGpa ? parseFloat(minGpa) : null,
        maxStudents: maxStudents ? parseInt(maxStudents) : null,
      }),
    onSuccess: () => {
      invalidateAll();
      toast.success('Program publisert');
      setSelectedProgram(null);
      setMinGpa('');
      setMaxStudents('');
    },
    onError: () => toast.error('Kunne ikke publisere'),
  });

  const handleBulkPublish = async () => {
    if (availablePrograms.length === 0) return;
    setBulkLoading(true);
    try {
      const count = await bulkPublishPrograms(
        periodId,
        availablePrograms.map((p) => p.id),
      );
      invalidateAll();
      toast.success(`${count} programmer publisert med forhåndsinnstilte krav`);
    } catch {
      toast.error('Kunne ikke publisere');
    } finally {
      setBulkLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl w-full max-w-lg shadow-2xl flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Publiser programmer</h2>
            <p className="text-sm text-text-muted mt-0.5">
              Velg programmer å legge ut på søknadsportalen
            </p>
          </div>
          <button onClick={onClose} className="text-text-muted hover:text-text-primary">
            <X size={18} />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-4">
          {/* Already published */}
          {requirements.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-text-secondary mb-2">
                Publiserte programmer
              </h3>
              <div className="space-y-1.5">
                {requirements.map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center justify-between px-3 py-2.5 border border-green-400/20 bg-green-400/5 rounded-lg"
                  >
                    <div className="flex items-center gap-2">
                      <CheckCircle2 size={14} className="text-green-400" />
                      <span className="text-sm font-medium text-text-primary">{r.programName}</span>
                    </div>
                    <div className="text-xs text-text-muted space-x-2">
                      {r.minGpa != null && <span>Snitt ≥ {r.minGpa}</span>}
                      {r.maxStudents != null && <span>Maks {r.maxStudents}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Bulk publish */}
          {availablePrograms.length > 0 && (
            <div className="border-t border-border pt-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-text-secondary">
                  Publiser alle på én gang
                </h3>
                <button
                  onClick={handleBulkPublish}
                  disabled={bulkLoading}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50"
                >
                  <Upload size={13} />{' '}
                  {bulkLoading ? 'Publiserer...' : `Publiser alle (${availablePrograms.length})`}
                </button>
              </div>
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {availablePrograms.map((p) => (
                  <div key={p.id} className="px-3 py-2 border border-border/50 rounded-lg text-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-text-primary font-medium">{p.name}</span>
                      <div className="text-xs text-text-muted space-x-2">
                        {p.minGpa != null && <span>Snitt ≥ {p.minGpa}</span>}
                        {p.maxStudents != null && <span>Maks {p.maxStudents}</span>}
                        {p.minGpa == null && p.maxStudents == null && !p.prerequisites && (
                          <span className="italic">Ingen krav satt</span>
                        )}
                      </div>
                    </div>
                    {p.prerequisites && (
                      <div className="flex flex-wrap gap-1 mt-1">
                        {p.prerequisites
                          .split(',')
                          .map((t) => t.trim())
                          .filter(Boolean)
                          .map((tag, i) => (
                            <span
                              key={i}
                              className="px-1.5 py-0.5 bg-orange-400/10 text-orange-400 text-[10px] font-medium rounded-full"
                            >
                              {tag}
                            </span>
                          ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Add single */}
          {availablePrograms.length > 0 && (
            <div className="border-t border-border pt-4 space-y-3">
              <h3 className="text-sm font-semibold text-text-secondary">
                Eller legg til enkeltvis
              </h3>
              <select
                value={selectedProgram ?? ''}
                onChange={(e) =>
                  setSelectedProgram(e.target.value ? parseInt(e.target.value) : null)
                }
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
              >
                <option value="">Velg program...</option>
                {availablePrograms.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              <div className="flex gap-3">
                <div className="flex-1">
                  <label className="block text-xs text-text-muted mb-1">
                    Min. snittkarakter{' '}
                    {(() => {
                      const prog = programs.find(p => p.id === selectedProgram);
                      return prog?.programType === 'MASTER' || prog?.programType === 'PHD' ? '(A-F)' : '(1-6)';
                    })()}
                  </label>
                  {(() => {
                    const prog = programs.find(p => p.id === selectedProgram);
                    const useLetterGrade = prog?.programType === 'MASTER' || prog?.programType === 'PHD';
                    if (useLetterGrade) {
                      return (
                        <select
                          value={minGpa}
                          onChange={(e) => setMinGpa(e.target.value)}
                          className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
                        >
                          <option value="">Valgfritt</option>
                          <option value="6">A</option>
                          <option value="5">B</option>
                          <option value="4">C</option>
                          <option value="3">D</option>
                          <option value="2">E</option>
                        </select>
                      );
                    }
                    return (
                      <input
                        type="number"
                        step="0.1"
                        min="1"
                        max="6"
                        value={minGpa}
                        onChange={(e) => setMinGpa(e.target.value)}
                        placeholder="Valgfritt"
                        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
                      />
                    );
                  })()}
                </div>
                <div className="flex-1">
                  <label className="block text-xs text-text-muted mb-1">Maks antall plasser</label>
                  <input
                    type="number"
                    min="1"
                    value={maxStudents}
                    onChange={(e) => setMaxStudents(e.target.value)}
                    placeholder="Valgfritt"
                    className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
                  />
                </div>
              </div>
              <button
                onClick={() => saveMut.mutate()}
                disabled={!selectedProgram || saveMut.isPending}
                className="w-full px-4 py-2 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors disabled:opacity-50"
              >
                {saveMut.isPending ? 'Publiserer...' : 'Publiser på portalen'}
              </button>
            </div>
          )}

          {availablePrograms.length === 0 && requirements.length > 0 && (
            <p className="text-sm text-text-muted italic border-t border-border pt-4">
              Alle programmer er allerede publisert ✓
            </p>
          )}
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

  const fromOptions =
    FROM_LEVELS[instLevel] ||
    Object.values(LEVEL_LABELS).map((label, i) => ({
      value: Object.keys(LEVEL_LABELS)[i],
      label,
    }));
  const toOptions =
    TO_LEVELS[instLevel] ||
    Object.entries(LEVEL_LABELS)
      .filter(([k]) => k !== 'UNGDOMSSKOLE')
      .map(([value, label]) => ({ value, label }));

  const [form, setForm] = useState({
    name: '',
    fromLevel: '',
    toLevel: toOptions.length === 1 ? toOptions[0].value : '',
    startDate: '',
    endDate: '',
    maxChoices: 5,
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await createAdmissionPeriod(form);
      toast.success('Opptaksperiode opprettet');
      onCreated();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Kunne ikke opprette');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl">
        <button
          onClick={onClose}
          className="absolute top-3 right-3 text-text-muted hover:text-text-primary"
        >
          <X size={18} />
        </button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">Ny opptaksperiode</h2>
        {error && (
          <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="block text-sm text-text-secondary mb-1">Navn</label>
            <input
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="f.eks. Opptak VGS 2026"
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
            />
          </div>
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Fra nivå</label>
              <select
                required
                value={form.fromLevel}
                onChange={(e) => setForm({ ...form, fromLevel: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
              >
                <option value="">Velg...</option>
                {fromOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Til nivå</label>
              <select
                required
                value={form.toLevel}
                onChange={(e) => setForm({ ...form, toLevel: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
              >
                <option value="">Velg...</option>
                {toOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Startdato</label>
              <input
                type="date"
                required
                value={form.startDate}
                onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
              />
            </div>
            <div className="flex-1">
              <label className="block text-sm text-text-secondary mb-1">Sluttdato</label>
              <input
                type="date"
                required
                value={form.endDate}
                onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm text-text-secondary mb-1">Maks antall valg</label>
            <input
              type="number"
              min="1"
              max="10"
              value={form.maxChoices}
              onChange={(e) => setForm({ ...form, maxChoices: parseInt(e.target.value) || 5 })}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm border border-border text-text-secondary rounded-lg hover:bg-bg-hover"
            >
              Avbryt
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 text-sm bg-accent hover:bg-accent-hover text-white rounded-lg disabled:opacity-50"
            >
              {loading ? 'Oppretter...' : 'Opprett'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
