import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getBookings, createBooking, deleteBooking, updateBooking, cancelBooking } from '../api/bookings';
import { getRooms } from '../api/rooms';
import { getSubjects } from '../api/subjects';
import { getPrograms } from '../api/programs';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Plus, Trash2, X, ChevronLeft, ChevronRight, Calendar, Pencil, Ban } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../auth/AuthProvider';
import { AutocompleteInput } from '../components/AutocompleteInput';
import type { Booking, BookingRequest, Room } from '../types';

function getMonday(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  date.setDate(diff);
  date.setHours(0, 0, 0, 0);
  return date;
}

function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + n);
  return r;
}

function formatShort(d: Date): string {
  return d.toLocaleDateString('no-NO', { day: '2-digit', month: 'short' });
}

function formatTime(dt: string): string {
  return new Date(dt).toLocaleTimeString('no-NO', { hour: '2-digit', minute: '2-digit' });
}

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];

export function BookingsPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const isStudent = user?.role === 'STUDENT';
  const canCreate = user?.role === 'SUPER_ADMIN' || user?.role === 'INSTITUTION_ADMIN' || user?.role === 'TEACHER';

  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()));
  const [selectedRoom, setSelectedRoom] = useState<number | 'all'>('all');
  const [showForm, setShowForm] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Booking | null>(null);
  const [editTarget, setEditTarget] = useState<Booking | null>(null);

  const { data: bookings = [], isLoading: bookingsLoading } = useQuery({ queryKey: ['bookings'], queryFn: getBookings });
  const { data: rooms = [], isLoading: roomsLoading } = useQuery({
    queryKey: ['rooms'], queryFn: getRooms,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteBooking,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['bookings'] }); toast.success('Booking deleted'); setDeleteTarget(null); },
    onError: () => toast.error('Failed to delete booking'),
  });

  const updateMutation = useMutation({
    mutationFn: updateBooking,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['bookings'] }); toast.success('Booking updated'); setEditTarget(null); },
    onError: () => toast.error('Failed to update booking'),
  });

  const cancelMutation = useMutation({
    mutationFn: cancelBooking,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['bookings'] });
      toast.success(data.status === 'CANCELLED' ? 'Avspasering satt' : 'Avspasering fjernet');
    },
    onError: () => toast.error('Kunne ikke oppdatere'),
  });

  const isAllRooms = selectedRoom === 'all';

  // Filter bookings for current week
  const weekEnd = addDays(weekStart, 5);
  const weekBookings = useMemo(() => {
    return bookings.filter(b => {
      const start = new Date(b.startDateTime);
      const inWeek = start >= weekStart && start < weekEnd;
      if (!inWeek) return false;
      if (isAllRooms) return true;
      const roomObj = rooms.find(rm => rm.id === selectedRoom);
      return roomObj && b.rooms?.some(r => r === roomObj.roomNumber);
    });
  }, [bookings, weekStart, selectedRoom, rooms, isAllRooms]);

  // Group bookings by day index (0=Mon, 4=Fri)
  const dayBookings = useMemo(() => {
    const result: Booking[][] = [[], [], [], [], []];
    weekBookings.forEach(b => {
      const d = new Date(b.startDateTime);
      const dayIdx = (d.getDay() + 6) % 7; // Mon=0
      if (dayIdx >= 0 && dayIdx < 5) result[dayIdx].push(b);
    });
    result.forEach(arr => arr.sort((a, b) => new Date(a.startDateTime).getTime() - new Date(b.startDateTime).getTime()));
    return result;
  }, [weekBookings]);

  // For "All Rooms" view: build a map room -> day -> bookings
  const allRoomsGrid = useMemo(() => {
    if (!isAllRooms) return null;
    const grid: Record<number, Booking[][]> = {};
    rooms.forEach(r => {
      grid[r.id] = [[], [], [], [], []];
    });
    weekBookings.forEach(b => {
      const d = new Date(b.startDateTime);
      const dayIdx = (d.getDay() + 6) % 7;
      if (dayIdx < 0 || dayIdx >= 5) return;
      rooms.forEach(r => {
        if (b.rooms?.includes(r.roomNumber)) {
          grid[r.id]?.[dayIdx]?.push(b);
        }
      });
    });
    return grid;
  }, [isAllRooms, rooms, weekBookings]);

  const prevWeek = () => setWeekStart(addDays(weekStart, -7));
  const nextWeek = () => setWeekStart(addDays(weekStart, 7));
  const today = () => setWeekStart(getMonday(new Date()));

  if (bookingsLoading || roomsLoading) return <LoadingState message="Loading bookings..." />;

  return (
    <div>
      <PageHeader title={isStudent ? 'Schedule' : 'Bookings'} description="Weekly room booking calendar"
        action={canCreate ? <button onClick={() => setShowForm(true)} className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"><Plus size={16} /> New Booking</button> : undefined} />

      {/* Week navigation + room selector */}
      <div className="flex flex-wrap items-center gap-4 mb-4">
        {/* Room filter */}
        <div className="flex items-center gap-2">
          <label className="text-sm text-text-secondary">Room:</label>
          <select value={selectedRoom} onChange={e => setSelectedRoom(e.target.value === 'all' ? 'all' : parseInt(e.target.value))}
            className="px-3 py-1.5 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus">
            <option value="all">📋 All Rooms</option>
            {rooms.map(r => <option key={r.id} value={r.id}>{r.roomNumber} ({r.roomType})</option>)}
          </select>
        </div>

        {/* Week controls */}
        <div className="flex items-center gap-1 ml-auto">
          <button onClick={prevWeek} className="p-1.5 rounded-md hover:bg-bg-hover text-text-secondary transition-colors"><ChevronLeft size={18} /></button>
          <button onClick={today} className="px-3 py-1.5 text-xs font-medium rounded-md border border-border hover:bg-bg-hover text-text-secondary transition-colors">Today</button>
          <button onClick={nextWeek} className="p-1.5 rounded-md hover:bg-bg-hover text-text-secondary transition-colors"><ChevronRight size={18} /></button>
          <span className="ml-2 text-sm font-medium text-text-primary flex items-center gap-1.5">
            <Calendar size={14} className="text-accent" />
            {formatShort(weekStart)} – {formatShort(addDays(weekStart, 4))}
          </span>
        </div>
      </div>

      {/* All Rooms overview */}
      {isAllRooms && allRoomsGrid ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm border border-border rounded-xl overflow-hidden">
            <thead>
              <tr className="bg-bg-card">
                <th className="px-3 py-2.5 text-left font-medium text-text-secondary border-b border-r border-border w-32">Room</th>
                {DAYS.map((day, i) => {
                  const date = addDays(weekStart, i);
                  const isToday = date.toDateString() === new Date().toDateString();
                  return (
                    <th key={day} className={`px-3 py-2.5 text-center font-medium text-sm border-b border-r border-border last:border-r-0 ${isToday ? 'bg-accent/20 text-accent' : 'text-text-secondary'}`}>
                      <div>{day}</div>
                      <div className="text-xs mt-0.5 font-normal">{formatShort(date)}</div>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {rooms.map(room => (
                <tr key={room.id} className="border-b border-border/50 last:border-b-0 hover:bg-bg-hover/30 transition-colors">
                  <td className="px-3 py-2 font-medium text-text-primary border-r border-border">
                    <div className="font-semibold">{room.roomNumber}</div>
                    <div className="text-[10px] text-text-muted">{room.roomType} · {room.capacity}</div>
                  </td>
                  {DAYS.map((_, i) => {
                    const date = addDays(weekStart, i);
                    const isToday = date.toDateString() === new Date().toDateString();
                    const bks = allRoomsGrid[room.id]?.[i] || [];
                    const hasBooking = bks.length > 0;
                    return (
                      <td key={i} className={`px-1.5 py-1.5 border-r border-border last:border-r-0 align-top ${isToday ? 'bg-accent/5' : hasBooking ? 'bg-bg-secondary' : 'bg-bg-secondary'}`}>
                        {hasBooking ? (
                          <div className="space-y-1">
                            {bks.map(b => {
                              const isCancelled = b.status === 'CANCELLED';
                              return (
                              <div key={b.id}
                                className={`group relative rounded px-1.5 py-1 cursor-pointer transition-colors ${
                                  isCancelled
                                    ? 'bg-red-500/15 border border-red-500/40 hover:bg-red-500/25'
                                    : 'bg-accent/10 border border-accent/30 hover:bg-accent/20'
                                }`}
                                onClick={() => canCreate ? setEditTarget(b) : undefined}
                              >
                                <p className={`text-[10px] font-mono font-bold truncate ${isCancelled ? 'text-red-400 line-through' : 'text-accent'}`}>{b.subject}</p>
                                <p className={`text-[9px] ${isCancelled ? 'text-red-400/60 line-through' : 'text-text-muted'}`}>{formatTime(b.startDateTime)}–{formatTime(b.endDateTime)}</p>
                                {isCancelled && <p className="text-[8px] text-red-400 font-semibold">Avspasering</p>}
                                {!isCancelled && b.programName && <p className="text-[8px] text-purple-400 font-medium truncate">{b.programName}</p>}
                                {canCreate && (
                                  <div className="absolute top-0.5 right-0.5 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-all">
                                    <button onClick={(e) => { e.stopPropagation(); cancelMutation.mutate(b.id); }}
                                      title={isCancelled ? 'Fjern avspasering' : 'Sett avspasering'}
                                      className={`p-0.5 rounded transition-colors ${isCancelled ? 'hover:bg-green-500/20 text-red-400 hover:text-green-400' : 'hover:bg-red-500/20 text-text-muted hover:text-red-400'}`}>
                                      <Ban size={10} />
                                    </button>
                                    <button onClick={(e) => { e.stopPropagation(); setEditTarget(b); }}
                                      className="p-0.5 rounded hover:bg-accent/20 text-text-muted hover:text-accent transition-colors">
                                      <Pencil size={10} />
                                    </button>
                                    <button onClick={(e) => { e.stopPropagation(); setDeleteTarget(b); }}
                                      className="p-0.5 rounded hover:bg-danger/20 text-text-muted hover:text-danger transition-colors">
                                      <Trash2 size={10} />
                                    </button>
                                  </div>
                                )}
                              </div>
                            );})}
                          </div>
                        ) : (
                          <div className="h-full min-h-[40px] flex items-center justify-center">
                            <span className="text-[10px] text-green-500/70 font-medium">Ledig</span>
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        /* Single room calendar grid */
        <div className="grid grid-cols-5 gap-px bg-border rounded-xl overflow-hidden border border-border">
          {/* Day headers */}
          {DAYS.map((day, i) => {
            const date = addDays(weekStart, i);
            const isToday = date.toDateString() === new Date().toDateString();
            return (
              <div key={day} className={`px-3 py-2.5 text-center font-medium text-sm ${isToday ? 'bg-accent/20 text-accent' : 'bg-bg-card text-text-secondary'}`}>
                <div>{day}</div>
                <div className="text-xs mt-0.5">{formatShort(date)}</div>
              </div>
            );
          })}

          {/* Day cells with bookings */}
          {DAYS.map((_, i) => {
            const date = addDays(weekStart, i);
            const isToday = date.toDateString() === new Date().toDateString();
            const bks = dayBookings[i];
            return (
              <div key={i} className={`min-h-[160px] p-2 ${isToday ? 'bg-accent/5' : 'bg-bg-secondary'}`}>
                {bks.length === 0 ? (
                  <span className="text-xs text-text-muted italic">No bookings</span>
                ) : (
                  <div className="space-y-1.5">
                    {bks.map(b => {
                      const isCancelled = b.status === 'CANCELLED';
                      return (
                      <div key={b.id} className={`group relative rounded-lg px-2.5 py-2 transition-colors ${
                        isCancelled
                          ? 'bg-red-500/10 border border-red-500/30 hover:border-red-500/50'
                          : 'bg-bg-card/80 border border-border hover:border-accent/50'
                      }`}>
                        <div className="flex items-start justify-between gap-1">
                          <div>
                            <p className={`text-xs font-mono font-semibold ${isCancelled ? 'text-red-400 line-through' : 'text-accent'}`}>{b.subject}</p>
                            <div className="flex items-center gap-2 mt-0.5">
                              <p className={`text-[11px] ${isCancelled ? 'text-red-400/60 line-through' : 'text-text-secondary'}`}>{formatTime(b.startDateTime)} – {formatTime(b.endDateTime)}</p>
                              {!isCancelled && <span className="text-[9px] text-accent/70 font-bold uppercase tracking-tighter opacity-70">
                                {b.institutionName || 'Default'}
                              </span>}
                            </div>
                            {isCancelled && <p className="text-[10px] text-red-400 font-semibold mt-0.5">Avspasering</p>}
                            {!isCancelled && b.description && <p className="text-[10px] text-text-muted mt-0.5 truncate max-w-[120px]">{b.description}</p>}
                            {!isCancelled && b.programName && <p className="text-[9px] text-purple-400 font-medium mt-0.5">{b.programName}</p>}
                          </div>
                          {canCreate && (
                            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all">
                              <button onClick={() => cancelMutation.mutate(b.id)}
                                title={isCancelled ? 'Fjern avspasering' : 'Sett avspasering'}
                                className={`p-1 rounded transition-colors ${isCancelled ? 'hover:bg-green-500/10 text-red-400 hover:text-green-400' : 'hover:bg-red-500/10 text-text-muted hover:text-red-400'}`}>
                                <Ban size={12} />
                              </button>
                              <button onClick={() => setEditTarget(b)}
                                className="p-1 rounded hover:bg-accent/10 text-text-muted hover:text-accent transition-colors">
                                <Pencil size={12} />
                              </button>
                              <button onClick={() => setDeleteTarget(b)}
                                className="p-1 rounded hover:bg-danger/10 text-text-muted hover:text-danger transition-colors">
                                <Trash2 size={12} />
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    );})}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {showForm && <BookingFormModal onClose={() => setShowForm(false)}
        onSaved={() => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ['bookings'] }); }} />}

      {editTarget && <EditBookingModal booking={editTarget} onClose={() => setEditTarget(null)}
        onConfirm={(data, series) => updateMutation.mutate({ id: editTarget.id, booking: data, series })}
        loading={updateMutation.isPending} />}

      {deleteTarget && <DeleteBookingModal booking={deleteTarget} onClose={() => setDeleteTarget(null)}
        onConfirm={(series) => deleteMutation.mutate({ id: deleteTarget.id, series })}
        loading={deleteMutation.isPending} />}
    </div>
  );
}

function DeleteBookingModal({ booking, onClose, onConfirm, loading }: { booking: Booking; onClose: () => void; onConfirm: (series: boolean) => void; loading: boolean }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={loading ? undefined : onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
        <h2 className="text-lg font-semibold text-danger mb-2">Delete Booking</h2>
        <p className="text-sm text-text-secondary mb-6">
          Do you want to delete only this booking, or the whole series (all items with subject <span className="font-mono text-accent">{booking.subject}</span> at the same time)?
        </p>
        <div className="flex flex-col gap-3">
          <button onClick={() => onConfirm(true)} disabled={loading} className="w-full py-2.5 px-4 bg-danger hover:bg-danger/90 text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50">
            Delete Whole Series
          </button>
          <button onClick={() => onConfirm(false)} disabled={loading} className="w-full py-2.5 px-4 border border-danger/50 text-danger hover:bg-danger/10 rounded-lg text-sm font-medium transition-colors disabled:opacity-50">
            Delete Only This Day
          </button>
          <button onClick={onClose} disabled={loading} className="w-full py-2.5 px-4 mt-2 text-text-secondary hover:bg-bg-hover rounded-lg text-sm font-medium transition-colors disabled:opacity-50">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

function EditBookingModal({ booking, onClose, onConfirm, loading }: { booking: Booking; onClose: () => void; onConfirm: (data: any, series: boolean) => void; loading: boolean }) {
  const oldStartDate = booking.startDateTime.split('T')[0];
  const [form, setForm] = useState({
    startDate: oldStartDate,
    endDate: oldStartDate,
    startTime: booking.startDateTime.split('T')[1].substring(0, 5),
    endTime: booking.endDateTime.split('T')[1].substring(0, 5)
  });

  const handleSubmit = (series: boolean) => {
    const startDateTime = `${form.startDate}T${form.startTime}:00`;
    const endDateTime = `${form.endDate}T${form.endTime}:00`;
    onConfirm({
      roomId: 0,
      subject: booking.subject,
      description: booking.description || '',
      startDateTime,
      endDateTime
    }, series);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={loading ? undefined : onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
        <button onClick={onClose} disabled={loading} className="absolute top-3 right-3 text-text-muted hover:text-text-primary disabled:opacity-50"><X size={18} /></button>
        <h2 className="text-lg font-semibold mb-4">Edit Booking</h2>
        <div className="mb-4">
          <p className="text-sm font-medium text-text-secondary mb-1">Subject</p>
          <p className="text-sm font-mono p-2 bg-bg-input rounded border border-border opacity-70">{booking.subject} (Locked)</p>
        </div>
        <div className="grid grid-cols-2 gap-3 mb-4">
          <div><label className="block text-xs font-medium text-text-secondary mb-1">Date</label>
            <input type="date" value={form.startDate} onChange={e => { setForm({...form, startDate: e.target.value, endDate: e.target.value }); }} disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
          <div className="opacity-0"><label className="block text-xs font-medium text-text-secondary mb-1">_</label>
            <input type="date" disabled className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm disabled:opacity-50" /></div>
        </div>
        <div className="grid grid-cols-2 gap-3 mb-6">
          <div><label className="block text-xs font-medium text-text-secondary mb-1">Start Time</label>
            <input type="time" value={form.startTime} onChange={e => setForm({...form, startTime: e.target.value})} disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
          <div><label className="block text-xs font-medium text-text-secondary mb-1">End Time</label>
            <input type="time" value={form.endTime} onChange={e => setForm({...form, endTime: e.target.value})} disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
        </div>
        <div className="flex flex-col gap-3">
          <button onClick={() => handleSubmit(true)} disabled={loading} className="w-full py-2.5 px-4 bg-accent hover:bg-accent-hover text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50">
            Apply to Whole Series
          </button>
          <button onClick={() => handleSubmit(false)} disabled={loading} className="w-full py-2.5 px-4 border border-accent/50 text-accent hover:bg-accent/10 rounded-lg text-sm font-medium transition-colors disabled:opacity-50">
            Apply to Only This Day
          </button>
        </div>
      </div>
    </div>
  );
}

function BookingFormModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { user } = useAuth();
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: getRooms });
  const { data: subjects = [] } = useQuery({ queryKey: ['subjects'], queryFn: getSubjects });
  const { data: programs = [] } = useQuery({ queryKey: ['programs'], queryFn: getPrograms });
  
  const [form, setForm] = useState({ 
    roomId: 0, 
    subject: '', 
    description: '', 
    fromDate: '', 
    toDate: '', 
    startTime: '09:00', 
    endTime: '10:00',
    programId: 0
  });
  
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState<{ total: number, current: number, conflicts: number } | null>(null);
  // Selected days: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri
  const [selectedDays, setSelectedDays] = useState<Set<number>>(new Set([1, 2, 3, 4, 5]));

  const toggleDay = (day: number) => {
    setSelectedDays(prev => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day); else next.add(day);
      return next;
    });
  };

  const subjectOptions = subjects.map(s => ({ value: s.code, label: s.name, sublabel: s.code }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true); setProgress(null);
    
    if (new Date(form.toDate) < new Date(form.fromDate)) {
      setError('To-date must be the same or after from-date.');
      setLoading(false);
      return;
    }
    
    if (form.endTime <= form.startTime) {
      setError('End time must be after start time.');
      setLoading(false);
      return;
    }

    try {
      let curDate = new Date(form.fromDate);
      const endDate = new Date(form.toDate);
      const daysToCreate: Date[] = [];
      
      while (curDate <= endDate) {
        const dayOfWeek = curDate.getDay(); // 0=Sun, 1=Mon, ..., 6=Sat
        // Only include if it's a selected weekday (convert JS day to our 1-5 format)
        if (dayOfWeek >= 1 && dayOfWeek <= 5 && selectedDays.has(dayOfWeek)) {
          daysToCreate.push(new Date(curDate));
        }
        curDate.setDate(curDate.getDate() + 1);
      }

      if (daysToCreate.length === 0) {
        setError('Ingen dager funnet i valgt periode med valgte ukedager.');
        setLoading(false);
        return;
      }

      let conflicts = 0;
      setProgress({ total: daysToCreate.length, current: 0, conflicts: 0 });

      for (let i = 0; i < daysToCreate.length; i++) {
        const d = daysToCreate[i];
        const dateStr = d.toISOString().split('T')[0];
        const startDateTime = `${dateStr}T${form.startTime}:00`;
        const endDateTime = `${dateStr}T${form.endTime}:00`;

        try {
          await createBooking({
            roomId: form.roomId,
            subject: form.subject,
            description: form.description,
            startDateTime, endDateTime,
            programId: form.programId || undefined
          });
        } catch (err: any) {
          if (err.response?.status === 409) conflicts++;
          else throw err;
        }
        setProgress(p => p ? { ...p, current: i + 1, conflicts } : null);
      }

      if (conflicts === daysToCreate.length) {
        toast.error('Could not create any bookings. The room was busy at all requested times.');
      } else if (conflicts > 0) {
        toast.warning(`Created ${daysToCreate.length - conflicts} bookings. Skipped ${conflicts} busy days.`);
      } else {
        toast.success(`Created bookings across ${daysToCreate.length} weekday(s).`);
        onClose();
      }
      onSaved();
    } catch (err: any) { setError(err.response?.data?.error || 'Failed to create booking series'); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl">
        <button onClick={onClose} disabled={loading} className="absolute top-3 right-3 text-text-muted hover:text-text-primary disabled:opacity-50"><X size={18} /></button>
        <h2 className="text-lg font-semibold mb-4">Book Room Period</h2>
        {error && <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">{error}</div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Room</label>
            <select value={form.roomId} onChange={e => setForm({...form, roomId: parseInt(e.target.value)})} required disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50">
              <option value={0}>Select a room...</option>
              {rooms.map(r => <option key={r.id} value={r.id}>{r.roomNumber} ({r.roomType}, {r.capacity} seats)</option>)}
            </select></div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Subject Code</label>
            <AutocompleteInput
              value={form.subject}
              onChange={v => setForm({...form, subject: v})}
              options={subjectOptions}
              placeholder="e.g. INF100"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Klasse (valgfritt)</label>
            <select value={form.programId} onChange={e => setForm({...form, programId: parseInt(e.target.value)})}
              disabled={loading}
              className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50">
              <option value={0}>Alle klasser</option>
              {programs.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </div>
          {/* Day-of-week selector */}
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1.5">Dager</label>
            <div className="flex gap-1.5">
              {([
                { day: 1, label: 'Man' },
                { day: 2, label: 'Tir' },
                { day: 3, label: 'Ons' },
                { day: 4, label: 'Tor' },
                { day: 5, label: 'Fre' },
              ] as const).map(({ day, label }) => (
                <button key={day} type="button" onClick={() => toggleDay(day)} disabled={loading}
                  className={`flex-1 px-2 py-2 text-xs font-medium rounded-lg border transition-colors disabled:opacity-50 ${
                    selectedDays.has(day)
                      ? 'bg-accent/15 border-accent/40 text-accent'
                      : 'bg-bg-input border-border text-text-muted hover:border-border-focus'
                  }`}>
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="block text-sm font-medium text-text-secondary mb-1.5">From Date</label>
              <input type="date" value={form.fromDate} onChange={e => setForm({...form, fromDate: e.target.value})} required disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
            <div><label className="block text-sm font-medium text-text-secondary mb-1.5">To Date</label>
              <input type="date" value={form.toDate} onChange={e => setForm({...form, toDate: e.target.value})} required disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Start Time</label>
              <input type="time" value={form.startTime} onChange={e => setForm({...form, startTime: e.target.value})} required disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
            <div><label className="block text-sm font-medium text-text-secondary mb-1.5">End Time</label>
              <input type="time" value={form.endTime} onChange={e => setForm({...form, endTime: e.target.value})} required disabled={loading} className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>
          </div>
          <div><label className="block text-sm font-medium text-text-secondary mb-1.5">Description / Tags</label>
            <input value={form.description || ''} onChange={e => setForm({...form, description: e.target.value})} disabled={loading} placeholder="E.g. Exam, Group work" className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus disabled:opacity-50" /></div>

          {loading && progress && (
            <div className="text-xs text-text-secondary bg-bg-hover p-2 rounded-lg">
              Processing: {progress.current} / {progress.total} days ...
              {progress.conflicts > 0 && <span className="text-danger ml-2">({progress.conflicts} conflicts)</span>}
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            {!loading && <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors">Cancel</button>}
            <button type="submit" disabled={loading} className="px-4 py-2 text-sm rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50">{loading ? 'Creating...' : 'Create'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
