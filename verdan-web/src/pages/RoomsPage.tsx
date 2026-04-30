import { useState } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getRooms, createRoom, updateRoom, deleteRoom } from '../api/rooms';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Plus, Pencil, Trash2, Search, X, ChevronDown, ChevronRight, Building2, DoorOpen } from 'lucide-react';
import { toast } from 'sonner';
import type { Room, RoomRequest } from '../types';

export function RoomsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Room | null>(null);
  const [expandedInstitutions, setExpandedInstitutions] = useState<Set<string>>(new Set());

  const { data: rooms = [], isLoading } = useQuery({ queryKey: ['rooms'], queryFn: getRooms });

  const deleteMutation = useMutation({
    mutationFn: deleteRoom,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['rooms'] }); toast.success('Rom slettet'); setDeleteTarget(null); },
    onError: () => toast.error('Kunne ikke slette rom'),
  });

  const filtered = rooms.filter(r =>
    r.roomNumber.toLowerCase().includes(search.toLowerCase()) ||
    r.roomType?.toLowerCase().includes(search.toLowerCase()) ||
    (r.institutionName || '').toLowerCase().includes(search.toLowerCase())
  );

  // Group rooms by institution
  const grouped = filtered.reduce<Record<string, Room[]>>((acc, room) => {
    const key = room.institutionName || 'Ikke tildelt';
    if (!acc[key]) acc[key] = [];
    acc[key].push(room);
    return acc;
  }, {});

  const institutionNames = Object.keys(grouped).sort();

  const toggleInstitution = (name: string) => {
    setExpandedInstitutions(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  // Auto-expand all when searching
  const effectiveExpanded = search.trim()
    ? new Set(institutionNames)
    : expandedInstitutions;

  if (isLoading) return <LoadingState message="Laster rom..." />;

  return (
    <div>
      <PageHeader title="Rom" description="Administrer klasserom og fasiliteter"
        action={<button onClick={() => { setEditing(null); setShowForm(true); }} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"><Plus size={16} /> Legg til rom</button>} />

      <div className="relative max-w-sm mb-6">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Søk i rom eller institusjoner..."
          className="w-full pl-9 pr-4 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors" />
      </div>

      {institutionNames.length === 0 ? <EmptyState title="Ingen rom funnet" /> : (
        <div className="space-y-3">
          {institutionNames.map(instName => {
            const instRooms = grouped[instName];
            const isExpanded = effectiveExpanded.has(instName);
            return (
              <div key={instName} className="bg-bg-card border border-border rounded-xl overflow-hidden transition-colors">
                {/* Institution header - clickable */}
                <button
                  onClick={() => toggleInstitution(instName)}
                  className="w-full flex items-center justify-between px-5 py-4 hover:bg-bg-hover/50 transition-colors text-left"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-accent/10">
                      <Building2 size={18} className="text-accent" />
                    </div>
                    <div>
                      <h3 className="font-semibold text-text-primary text-base">{instName}</h3>
                      <p className="text-xs text-text-muted mt-0.5">
                        {instRooms.length} {instRooms.length === 1 ? 'rom' : 'rom'} · {instRooms.reduce((s, r) => s + r.capacity, 0)} plasser totalt
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-accent bg-accent/10 px-2.5 py-1 rounded-full">
                      {instRooms.length}
                    </span>
                    {isExpanded
                      ? <ChevronDown size={18} className="text-text-muted" />
                      : <ChevronRight size={18} className="text-text-muted" />
                    }
                  </div>
                </button>

                {/* Rooms list - collapsible */}
                {isExpanded && (
                  <div className="border-t border-border">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 p-4">
                      {instRooms.map(room => (
                        <div key={room.id} className="bg-bg-secondary border border-border/60 rounded-lg p-4 hover:border-accent/30 transition-colors">
                          <div className="flex items-start justify-between">
                            <div className="flex items-center gap-2.5">
                              <DoorOpen size={16} className="text-accent/70 shrink-0" />
                              <div>
                                <h4 className="font-semibold text-text-primary">{room.roomNumber}</h4>
                                <p className="text-xs text-text-muted">{room.roomType || 'Standard'}</p>
                              </div>
                            </div>
                            <div className="flex gap-0.5">
                              <button onClick={() => { setEditing(room); setShowForm(true); }} className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"><Pencil size={13} /></button>
                              <button onClick={() => setDeleteTarget(room)} className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"><Trash2 size={13} /></button>
                            </div>
                          </div>
                          <div className="mt-2.5 flex items-center gap-1.5">
                            <span className="text-xl font-bold text-accent">{room.capacity}</span>
                            <span className="text-xs text-text-muted">plasser</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {showForm && <RoomFormModal room={editing} onClose={() => { setShowForm(false); setEditing(null); }}
        onSaved={() => { setShowForm(false); setEditing(null); queryClient.invalidateQueries({ queryKey: ['rooms'] }); }} />}

      <ConfirmDialog open={!!deleteTarget} title="Slett rom" message={`Slette "${deleteTarget?.roomNumber}"? Alle tilknyttede bookinger vil også bli slettet.`}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)} onCancel={() => setDeleteTarget(null)} loading={deleteMutation.isPending} />
    </div>
  );
}

function RoomFormModal({ room, onClose, onSaved }: { room: Room | null; onClose: () => void; onSaved: () => void }) {
  const { user: currentUser } = useAuth();
  const isEditing = !!room;
  const [form, setForm] = useState<RoomRequest>({
    roomNumber: room?.roomNumber || '',
    roomType: room?.roomType || '',
    capacity: room?.capacity || 30,
    institutionId: room?.institutionId || currentUser?.institutionId,
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true);
    try {
      if (isEditing) { await updateRoom(room!.id, form); toast.success('Rom oppdatert'); }
      else { await createRoom(form); toast.success('Rom opprettet'); }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.error || 'Kunne ikke lagre'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl">
        <button onClick={onClose} className="absolute top-3 right-3 text-text-muted hover:text-text-primary"><X size={18} /></button>
        <h2 className="text-lg font-semibold mb-4">{isEditing ? 'Rediger rom' : 'Legg til rom'}</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Romnummer</label>
            <input value={form.roomNumber} onChange={e => setForm({...form, roomNumber: e.target.value})} required className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" /></div>
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Romtype</label>
            <input value={form.roomType || ''} onChange={e => setForm({...form, roomType: e.target.value})} placeholder="f.eks. Forelesningssal" className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus" /></div>
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Kapasitet</label>
            <input type="number" value={form.capacity} onChange={e => setForm({...form, capacity: parseInt(e.target.value) || 0})} required min={1} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus" /></div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Avbryt</button>
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">{loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Opprett'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
