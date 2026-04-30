import { useState, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getInstitutions, createInstitution, updateInstitution, deleteInstitution } from '../api/institutions';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { Plus, Building2, MapPin, X, Pencil, Trash2, Search, GraduationCap, School, BookOpen, Building } from 'lucide-react';
import { toast } from 'sonner';
import type { Institution, InstitutionLevel } from '../types';

const LEVEL_CONFIG: Record<InstitutionLevel, { label: string; icon: React.ReactNode; color: string }> = {
  GENERAL:      { label: 'Generell', icon: <Building2 size={20} />, color: 'text-slate-400 bg-slate-400/10' },
  UNGDOMSSKOLE: { label: 'Ungdomsskole', icon: <School size={20} />, color: 'text-blue-400 bg-blue-400/10' },
  VGS:          { label: 'Videregående skole', icon: <BookOpen size={20} />, color: 'text-emerald-400 bg-emerald-400/10' },
  FAGSKOLE:     { label: 'Fagskole', icon: <Building size={20} />, color: 'text-amber-400 bg-amber-400/10' },
  UNIVERSITET:  { label: 'Universitet og høyskole', icon: <GraduationCap size={20} />, color: 'text-purple-400 bg-purple-400/10' },
};

const LEVEL_ORDER: InstitutionLevel[] = ['GENERAL', 'UNGDOMSSKOLE', 'VGS', 'FAGSKOLE', 'UNIVERSITET'];

export function InstitutionsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Institution | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Institution | null>(null);
  const [search, setSearch] = useState('');

  const { data: institutions = [], isLoading } = useQuery({ queryKey: ['institutions'], queryFn: getInstitutions });

  // Filter by search
  const filtered = useMemo(() => {
    if (!search.trim()) return institutions;
    const q = search.toLowerCase();
    return institutions.filter(i =>
      i.name.toLowerCase().includes(q) ||
      (i.location && i.location.toLowerCase().includes(q)) ||
      (i.level && LEVEL_CONFIG[i.level]?.label.toLowerCase().includes(q))
    );
  }, [institutions, search]);

  // Group by level
  const grouped = useMemo(() => {
    const groups: Record<string, Institution[]> = {};
    for (const level of LEVEL_ORDER) {
      const items = filtered.filter(i => i.level === level);
      if (items.length > 0) groups[level] = items;
    }
    // Uncategorized
    const uncategorized = filtered.filter(i => !i.level || !LEVEL_ORDER.includes(i.level));
    if (uncategorized.length > 0) groups['UNCATEGORIZED'] = uncategorized;
    return groups;
  }, [filtered]);

  const handleDelete = async () => {
    if (!confirmDelete) return;
    try {
      await deleteInstitution(confirmDelete.id);
      toast.success(`"${confirmDelete.name}" er deaktivert`);
      queryClient.invalidateQueries({ queryKey: ['institutions'] });
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Kunne ikke deaktivere');
    } finally {
      setConfirmDelete(null);
    }
  };

  if (isLoading) return <LoadingState message="Laster institusjoner..." />;

  return (
    <div>
      <PageHeader
        title="Institusjoner"
        description="Administrer skoler, høyskoler og universitetscampuser"
        action={
          <button onClick={() => { setEditing(null); setShowForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors">
            <Plus size={16} /> Ny institusjon
          </button>
        }
      />

      {/* Search */}
      <div className="mb-6">
        <div className="relative max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Søk etter institusjoner..."
            className="w-full pl-9 pr-4 py-2.5 bg-bg-secondary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors"
          />
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState title={search ? 'Ingen treff' : 'Ingen institusjoner funnet'} message={search ? 'Prøv et annet søkeord' : 'Legg til en ny institusjon for å komme i gang'} />
      ) : (
        <div className="space-y-8">
          {Object.entries(grouped).map(([level, items]) => {
            const config = LEVEL_CONFIG[level as InstitutionLevel];
            const isUncategorized = level === 'UNCATEGORIZED';

            return (
              <div key={level}>
                {/* Level header */}
                <div className="flex items-center gap-3 mb-4">
                  <div className={`p-2 rounded-lg ${isUncategorized ? 'text-text-muted bg-bg-hover' : config.color}`}>
                    {isUncategorized ? <Building2 size={20} /> : config.icon}
                  </div>
                  <div>
                    <h2 className="text-base font-semibold text-text-primary">
                      {isUncategorized ? 'Ikke kategorisert' : config.label}
                    </h2>
                    <p className="text-xs text-text-muted">{items.length} {items.length === 1 ? 'institusjon' : 'institusjoner'}</p>
                  </div>
                </div>

                {/* Institution cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 pl-2">
                  {items.map(inst => (
                    <div key={inst.id} className="bg-bg-secondary p-4 rounded-xl border border-border group hover:border-accent/30 transition-colors">
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className={`p-2 rounded-lg shrink-0 ${isUncategorized ? 'text-text-muted bg-bg-hover' : config.color}`}>
                            <Building2 size={20} />
                          </div>
                          <div className="min-w-0">
                            <h3 className="font-semibold text-text-primary text-sm truncate">{inst.name}</h3>
                            <div className="flex items-center gap-2 mt-0.5">
                              <p className="text-xs text-text-muted flex items-center gap-1">
                                <MapPin size={11} /> {inst.location || 'Ingen lokasjon'}
                              </p>
                              <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded ${
                                inst.ownership === 'PRIVATE' ? 'bg-amber-400/10 text-amber-400' : 'bg-blue-400/10 text-blue-400'
                              }`}>
                                {inst.ownership === 'PRIVATE' ? 'Privat' : 'Offentlig'}
                              </span>
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity shrink-0 ml-2">
                          <button
                            onClick={() => { setEditing(inst); setShowForm(true); }}
                            className="p-1.5 rounded-lg text-text-muted hover:text-accent hover:bg-accent/10 transition-colors"
                            title="Rediger"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => setConfirmDelete(inst)}
                            className="p-1.5 rounded-lg text-text-muted hover:text-danger hover:bg-danger/10 transition-colors"
                            title="Deaktiver"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {showForm && (
        <InstitutionFormModal
          institution={editing}
          onClose={() => { setShowForm(false); setEditing(null); }}
          onSaved={() => { setShowForm(false); setEditing(null); queryClient.invalidateQueries({ queryKey: ['institutions'] }); }}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setConfirmDelete(null)} />
          <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
            <h2 className="text-lg font-semibold text-text-primary mb-2">Deaktiver institusjon</h2>
            <p className="text-sm text-text-secondary mb-2">
              Er du sikker på at du vil deaktivere <strong className="text-text-primary">"{confirmDelete.name}"</strong>?
            </p>
            <p className="text-xs text-text-muted mb-5">
              Brukere som tilhører denne institusjonen beholder tilknytningen sin som historisk referanse, men institusjonen forsvinner fra valglistene.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDelete(null)} className="px-4 py-2 text-sm font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
              <button onClick={handleDelete} className="px-4 py-2 text-sm font-medium bg-danger hover:bg-danger/80 text-white rounded-lg transition-colors">Deaktiver</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function InstitutionFormModal({ institution, onClose, onSaved }: { institution: Institution | null; onClose: () => void; onSaved: () => void }) {
  const isEditing = !!institution;
  const [form, setForm] = useState({
    name: institution?.name || '',
    location: institution?.location || '',
    level: institution?.level || '',
    ownership: institution?.ownership || 'PUBLIC',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const payload = { name: form.name, location: form.location, level: form.level || undefined, ownership: form.ownership };
      if (isEditing) {
        await updateInstitution(institution!.id, payload);
        toast.success('Institusjon oppdatert');
      } else {
        await createInstitution(payload);
        toast.success('Institusjon opprettet');
      }
      onSaved();
    } catch (err: any) {
      setError(err.response?.data?.error || err.response?.data?.errors?.join(', ') || 'Kunne ikke lagre');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">{isEditing ? 'Rediger institusjon' : 'Ny institusjon'}</h2>

        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Navn</label>
            <input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} required autoFocus
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus transition-colors" />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Lokasjon</label>
            <input value={form.location} onChange={e => setForm(p => ({ ...p, location: e.target.value }))}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus transition-colors" />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Nivå</label>
            <select value={form.level} onChange={e => setForm(p => ({ ...p, level: e.target.value }))}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus transition-colors">
              <option value="">Velg nivå...</option>
              <option value="GENERAL">Generell</option>
              <option value="UNGDOMSSKOLE">Ungdomsskole</option>
              <option value="VGS">Videregående skole</option>
              <option value="FAGSKOLE">Fagskole</option>
              <option value="UNIVERSITET">Universitet og høyskole</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Type</label>
            <div className="flex gap-2">
              <button type="button" onClick={() => setForm(p => ({ ...p, ownership: 'PUBLIC' }))}
                className={`flex-1 px-3 py-2 text-sm font-medium rounded-lg border transition-colors ${
                  form.ownership === 'PUBLIC'
                    ? 'bg-blue-500/10 border-blue-500/30 text-blue-400'
                    : 'bg-bg-input border-border text-text-secondary hover:border-border-focus'
                }`}>
                Offentlig
              </button>
              <button type="button" onClick={() => setForm(p => ({ ...p, ownership: 'PRIVATE' }))}
                className={`flex-1 px-3 py-2 text-sm font-medium rounded-lg border transition-colors ${
                  form.ownership === 'PRIVATE'
                    ? 'bg-amber-500/10 border-amber-500/30 text-amber-400'
                    : 'bg-bg-input border-border text-text-secondary hover:border-border-focus'
                }`}>
                Privat
              </button>
            </div>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium border border-border rounded-lg text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm font-medium bg-accent hover:bg-accent-hover text-white rounded-lg disabled:opacity-50 transition-colors">
              {loading ? 'Lagrer...' : (isEditing ? 'Oppdater' : 'Opprett')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
