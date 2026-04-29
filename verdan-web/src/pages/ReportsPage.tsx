import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getGraduatedStudents, getArchivedStudents, archiveStudent, restoreStudent, bulkArchiveAll } from '../api/programs';
import type { GraduatedStudent } from '../api/programs';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useAuth } from '../auth/AuthProvider';
import { GraduationCap, ChevronDown, ChevronRight, Download, Users, Award, Archive, ArchiveRestore, PackageOpen, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';

export function ReportsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = user?.role === 'INSTITUTION_ADMIN' || user?.role === 'SUPER_ADMIN';
  const [expandedYear, setExpandedYear] = useState<string | null>(null);
  const [viewTab, setViewTab] = useState<'active' | 'archived'>('active');
  const [confirmArchive, setConfirmArchive] = useState<GraduatedStudent | null>(null);
  const [confirmRestore, setConfirmRestore] = useState<GraduatedStudent | null>(null);
  const [confirmBulk, setConfirmBulk] = useState(false);

  const { data: graduated = [], isLoading } = useQuery({
    queryKey: ['graduatedStudents'],
    queryFn: getGraduatedStudents,
    enabled: isAdmin,
  });

  const { data: archived = [], isLoading: isLoadingArchived } = useQuery({
    queryKey: ['archivedStudents'],
    queryFn: getArchivedStudents,
    enabled: isAdmin,
  });

  const archiveMutation = useMutation({
    mutationFn: (s: GraduatedStudent) => archiveStudent(s.programId, s.userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['graduatedStudents'] });
      queryClient.invalidateQueries({ queryKey: ['archivedStudents'] });
      toast.success('Elev arkivert');
      setConfirmArchive(null);
    },
    onError: () => toast.error('Kunne ikke arkivere eleven'),
  });

  const restoreMutation = useMutation({
    mutationFn: (s: GraduatedStudent) => restoreStudent(s.programId, s.userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['graduatedStudents'] });
      queryClient.invalidateQueries({ queryKey: ['archivedStudents'] });
      toast.success('Elev gjenopprettet');
      setConfirmRestore(null);
    },
    onError: () => toast.error('Kunne ikke gjenopprette eleven'),
  });

  const bulkMutation = useMutation({
    mutationFn: bulkArchiveAll,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['graduatedStudents'] });
      queryClient.invalidateQueries({ queryKey: ['archivedStudents'] });
      toast.success(`${data.archivedCount} elever arkivert`);
      setConfirmBulk(false);
    },
    onError: () => toast.error('Kunne ikke arkivere elevene'),
  });

  const displayList = viewTab === 'active' ? graduated : archived;

  // Group by yearLevel
  const groupedByYear = useMemo(() => {
    const groups: Record<string, GraduatedStudent[]> = {};
    for (const s of displayList) {
      const year = s.yearLevel || 'Ukjent';
      if (!groups[year]) groups[year] = [];
      groups[year].push(s);
    }
    return Object.entries(groups).sort(([a], [b]) => a.localeCompare(b));
  }, [displayList]);

  const totalWithDiploma = graduated.filter(s => s.diplomaEligible).length;
  const totalKompetanse = graduated.filter(s => !s.diplomaEligible && s.programType === 'YRKESFAG').length;
  const totalIkkeBestatt = graduated.filter(s => !s.diplomaEligible && s.programType !== 'YRKESFAG').length;

  // CSV export
  const handleExport = () => {
    const all = [...graduated, ...archived];
    const header = 'Navn;Brukernavn;E-post;Program;Årstrinn;Dokumenttype;Status\n';
    const getDocType = (s: GraduatedStudent) => s.diplomaEligible ? 'Vitnemål' : s.programType === 'YRKESFAG' ? 'Kompetansebevis' : 'Ikke bestått';
    const rows = all.map(s =>
      `${s.firstName} ${s.lastName};${s.username};${s.email};${s.programName};${s.yearLevel};${getDocType(s)};${archived.some(a => a.userId === s.userId && a.programId === s.programId) ? 'Arkivert' : 'Aktiv'}`
    ).join('\n');
    const csv = `# Verdan – Uteksaminerte elever\n# Eksportert: ${new Date().toLocaleDateString('nb-NO')}\n\n${header}${rows}\n`;
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `uteksaminerte_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (!isAdmin) {
    return (
      <div>
        <PageHeader title="Uteksaminerte" description="Oversikt over uteksaminerte elever" />
        <EmptyState title="Ingen tilgang" message="Kun administratorer kan se uteksaminerte elever." />
      </div>
    );
  }

  if (isLoading || isLoadingArchived) return <LoadingState message="Laster uteksaminerte elever..." />;

  return (
    <div>
      <PageHeader title="Uteksaminerte" description="Oversikt over alle uteksaminerte elever"
        action={(graduated.length > 0 || archived.length > 0) ? (
          <div className="flex items-center gap-2">
            {viewTab === 'active' && graduated.length > 0 && (
              <button onClick={() => setConfirmBulk(true)}
                className="flex items-center gap-2 px-4 py-2 bg-bg-card border border-border hover:bg-bg-hover text-text-primary text-sm font-medium rounded-lg transition-colors">
                <PackageOpen size={16} /> Arkiver alle
              </button>
            )}
            <button onClick={handleExport}
              className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors">
              <Download size={16} /> Eksporter CSV
            </button>
          </div>
        ) : undefined}
      />

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-bg-secondary border border-border rounded-lg p-1 w-fit">
        <button onClick={() => { setViewTab('active'); setExpandedYear(null); }}
          className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            viewTab === 'active' ? 'bg-accent text-white shadow-sm' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
          }`}>
          <GraduationCap size={15} /> Uteksaminerte
          {graduated.length > 0 && <span className="text-[11px] opacity-80 ml-0.5">({graduated.length})</span>}
        </button>
        <button onClick={() => { setViewTab('archived'); setExpandedYear(null); }}
          className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
            viewTab === 'archived' ? 'bg-accent text-white shadow-sm' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
          }`}>
          <Archive size={15} /> Arkiv
          {archived.length > 0 && <span className="text-[11px] opacity-80 ml-0.5">({archived.length})</span>}
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-5 gap-4 mb-6">
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 text-text-muted mb-2"><Users size={16} /> Totalt uteksaminert</div>
          <p className="text-3xl font-bold text-accent">{graduated.length}</p>
          <p className="text-xs text-text-muted mt-1">aktive elever</p>
        </div>
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 text-text-muted mb-2"><GraduationCap size={16} /> Vitnemål</div>
          <p className="text-3xl font-bold text-green-400">{totalWithDiploma}</p>
          <p className="text-xs text-text-muted mt-1">med studiekompetanse</p>
        </div>
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 text-text-muted mb-2"><Award size={16} /> Kompetansebevis</div>
          <p className="text-3xl font-bold text-yellow-400">{totalKompetanse}</p>
          <p className="text-xs text-text-muted mt-1">yrkesfag (bestått)</p>
        </div>
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 text-text-muted mb-2"><AlertTriangle size={16} /> Ikke bestått</div>
          <p className="text-3xl font-bold text-red-400">{totalIkkeBestatt}</p>
          <p className="text-xs text-text-muted mt-1">mangler karakter</p>
        </div>
        <div className="bg-bg-card border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 text-text-muted mb-2"><Archive size={16} /> Arkiverte</div>
          <p className="text-3xl font-bold text-text-muted">{archived.length}</p>
          <p className="text-xs text-text-muted mt-1">tidligere uteksaminerte</p>
        </div>
      </div>

      {/* Archive info banner */}
      {viewTab === 'archived' && archived.length > 0 && (
        <div className="mb-4 px-4 py-3 bg-bg-hover/50 border border-border/50 rounded-lg text-sm text-text-muted flex items-center gap-2">
          <Archive size={14} className="shrink-0" />
          Arkiverte elever er tidligere uteksaminerte som ikke tar videre utdanning. All data bevares og kan gjenopprettes.
        </div>
      )}

      {/* Grouped list */}
      {displayList.length === 0 ? (
        <EmptyState
          title={viewTab === 'active' ? 'Ingen uteksaminerte elever' : 'Ingen arkiverte elever'}
          message={viewTab === 'active' ? 'Ingen elever har blitt uteksaminert ennå.' : 'Ingen elever har blitt arkivert.'}
        />
      ) : (
        <div className="space-y-3">
          {groupedByYear.map(([year, students]) => {
            const isExpanded = expandedYear === year;
            const diplomaCount = students.filter(s => s.diplomaEligible).length;
            const kompetanseCount = students.length - diplomaCount;

            return (
              <div key={year} className="bg-bg-card border border-border rounded-xl overflow-hidden">
                <button onClick={() => setExpandedYear(isExpanded ? null : year)}
                  className="w-full px-5 py-4 flex items-center justify-between hover:bg-bg-hover/50 transition-colors">
                  <div className="flex items-center gap-3">
                    {isExpanded
                      ? <ChevronDown size={18} className="text-text-muted" />
                      : <ChevronRight size={18} className="text-text-muted" />
                    }
                    <div className="flex items-center gap-2">
                      <GraduationCap size={18} className="text-accent" />
                      <span className="text-base font-semibold text-text-primary">{YEAR_LABELS[year] || year}</span>
                    </div>
                    <span className="text-xs px-2.5 py-1 bg-accent/10 text-accent rounded-full font-medium">
                      {students.length} {students.length === 1 ? 'elev' : 'elever'}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    {diplomaCount > 0 && (
                      <span className="text-xs px-2 py-0.5 bg-green-400/10 text-green-400 rounded-full">
                        🎓 {diplomaCount}
                      </span>
                    )}
                    {kompetanseCount > 0 && (
                      <span className="text-xs px-2 py-0.5 bg-yellow-400/10 text-yellow-400 rounded-full">
                        📄 {kompetanseCount}
                      </span>
                    )}
                  </div>
                </button>

                {isExpanded && (
                  <div className="border-t border-border">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-border bg-bg-primary/30 text-text-secondary text-left">
                          <th className="px-5 py-2.5 font-medium">Elev</th>
                          <th className="px-5 py-2.5 font-medium">Brukernavn</th>
                          <th className="px-5 py-2.5 font-medium">Program</th>
                          <th className="px-5 py-2.5 font-medium">Dokument</th>
                          <th className="px-5 py-2.5 font-medium w-28">Handling</th>
                        </tr>
                      </thead>
                      <tbody>
                        {students.map(s => (
                          <tr key={`${s.userId}-${s.programId}`} className="border-b border-border/30 hover:bg-bg-hover/30 transition-colors">
                            <td className="px-5 py-3">
                              <div className="flex items-center gap-3">
                                <div className="w-8 h-8 rounded-full bg-accent/10 flex items-center justify-center text-accent text-xs font-bold">
                                  {s.firstName?.[0]}{s.lastName?.[0]}
                                </div>
                                <span className="font-medium text-text-primary">{s.firstName} {s.lastName}</span>
                              </div>
                            </td>
                            <td className="px-5 py-3 text-text-muted font-mono text-xs">@{s.username}</td>
                            <td className="px-5 py-3 text-text-secondary">{s.programName}</td>
                            <td className="px-5 py-3">
                              {s.diplomaEligible ? (
                                <span className="text-xs px-2.5 py-1 rounded-full bg-green-400/10 text-green-400 border border-green-400/20 font-medium">
                                  🎓 Vitnemål
                                </span>
                              ) : s.programType === 'YRKESFAG' ? (
                                <span className="text-xs px-2.5 py-1 rounded-full bg-yellow-400/10 text-yellow-400 border border-yellow-400/20 font-medium">
                                  📄 Kompetansebevis
                                </span>
                              ) : (
                                <span className="text-xs px-2.5 py-1 rounded-full bg-red-400/10 text-red-400 border border-red-400/20 font-medium">
                                  ⚠ Ikke bestått
                                </span>
                              )}
                            </td>
                            <td className="px-5 py-3">
                              {viewTab === 'active' ? (
                                <button onClick={() => setConfirmArchive(s)}
                                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border text-text-muted hover:text-text-primary hover:bg-bg-hover transition-colors"
                                  title="Arkiver elev">
                                  <Archive size={13} /> Arkiver
                                </button>
                              ) : (
                                <button onClick={() => setConfirmRestore(s)}
                                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-accent/30 text-accent hover:bg-accent/10 transition-colors"
                                  title="Gjenopprett elev">
                                  <ArchiveRestore size={13} /> Gjenopprett
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Confirm archive dialog */}
      <ConfirmDialog
        open={!!confirmArchive}
        title="Arkiver elev"
        message={confirmArchive ? `Er du sikker på at du vil arkivere ${confirmArchive.firstName} ${confirmArchive.lastName}? Eleven kan gjenopprettes fra arkivet når som helst.` : ''}
        confirmLabel="Arkiver"
        loadingLabel="Arkiverer..."
        variant="accent"
        onConfirm={() => confirmArchive && archiveMutation.mutate(confirmArchive)}
        onCancel={() => setConfirmArchive(null)}
        loading={archiveMutation.isPending}
      />

      {/* Confirm restore dialog */}
      <ConfirmDialog
        open={!!confirmRestore}
        title="Gjenopprett elev"
        message={confirmRestore ? `Er du sikker på at du vil gjenopprette ${confirmRestore.firstName} ${confirmRestore.lastName} til uteksaminerte-listen?` : ''}
        confirmLabel="Gjenopprett"
        loadingLabel="Gjenoppretter..."
        variant="accent"
        onConfirm={() => confirmRestore && restoreMutation.mutate(confirmRestore)}
        onCancel={() => setConfirmRestore(null)}
        loading={restoreMutation.isPending}
      />

      {/* Confirm bulk archive dialog */}
      <ConfirmDialog
        open={confirmBulk}
        title="Arkiver alle uteksaminerte"
        message={`Er du sikker på at du vil arkivere alle ${graduated.length} uteksaminerte elever? Elevene kan gjenopprettes individuelt fra arkivet.`}
        confirmLabel="Arkiver alle"
        loadingLabel="Arkiverer..."
        variant="accent"
        onConfirm={() => bulkMutation.mutate()}
        onCancel={() => setConfirmBulk(false)}
        loading={bulkMutation.isPending}
      />
    </div>
  );
}

const YEAR_LABELS: Record<string, string> = {
  '1': '1. klasse',
  '2': '2. klasse',
  '3': '3. klasse',
  '4': '4. klasse',
  '5': '5. klasse',
  '6': '6. klasse',
  '7': '7. klasse',
  '8': '8. klasse',
  '9': '9. klasse',
  '10': '10. klasse',
  'VG1': 'VG1',
  'VG2': 'VG2',
  'VG3': 'VG3',
  'VG3_PABYGG': 'VG3 Påbygg',
  'BACHELOR_1': 'Bachelor 1. år',
  'BACHELOR_2': 'Bachelor 2. år',
  'BACHELOR_3': 'Bachelor 3. år',
  'MASTER_1': 'Master 1. år',
  'MASTER_2': 'Master 2. år',
  'PHD_1': 'PhD 1. år',
  'PHD_2': 'PhD 2. år',
  'PHD_3': 'PhD 3. år',
};
