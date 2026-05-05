import { useState, useMemo, useEffect } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
  importStudents,
  batchDeleteUsers,
  transferStudentsBatch,
} from '../api/users';
import type { ImportResult, TransferResult } from '../api/users';
import { getInstitutions } from '../api/institutions';
import { PageHeader } from '../components/PageHeader';
import { LoadingState, EmptyState } from '../components/LoadingState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  Plus,
  Pencil,
  Trash2,
  Search,
  X,
  ChevronDown,
  ChevronRight,
  Building2,
  School,
  BookOpen,
  Building,
  GraduationCap,
  MapPin,
  Users,
  Upload,
  Download,
  FileSpreadsheet,
  CheckCircle2,
  AlertTriangle,
  Undo2,
  ArrowRightLeft,
} from 'lucide-react';
import { toast } from 'sonner';
import type { User, CreateUserRequest, Institution, InstitutionLevel } from '../types';

const LEVEL_CONFIG: Record<
  InstitutionLevel,
  { label: string; icon: React.ReactNode; color: string }
> = {
  GENERAL: {
    label: 'Generell',
    icon: <Building2 size={20} />,
    color: 'text-slate-400 bg-slate-400/10',
  },
  UNGDOMSSKOLE: {
    label: 'Ungdomsskole',
    icon: <School size={20} />,
    color: 'text-blue-400 bg-blue-400/10',
  },
  VGS: {
    label: 'Videregående skole',
    icon: <BookOpen size={20} />,
    color: 'text-emerald-400 bg-emerald-400/10',
  },
  FAGSKOLE: {
    label: 'Fagskole',
    icon: <Building size={20} />,
    color: 'text-amber-400 bg-amber-400/10',
  },
  UNIVERSITET: {
    label: 'Universitet og høyskole',
    icon: <GraduationCap size={20} />,
    color: 'text-purple-400 bg-purple-400/10',
  },
};

const LEVEL_ORDER: InstitutionLevel[] = [
  'GENERAL',
  'UNGDOMSSKOLE',
  'VGS',
  'FAGSKOLE',
  'UNIVERSITET',
];

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const isSuperAdmin = currentUser?.role === 'SUPER_ADMIN';
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [showForm, setShowForm] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);
  const [showImport, setShowImport] = useState(false);
  const [showTransfer, setShowTransfer] = useState(false);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users', roleFilter],
    queryFn: () => getUsers(roleFilter || undefined),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['subjects'] });
      queryClient.invalidateQueries({ queryKey: ['subjectMembers'] });
      queryClient.invalidateQueries({ queryKey: ['programs'] });
      queryClient.invalidateQueries({ queryKey: ['programMembers'] });
      queryClient.invalidateQueries({ queryKey: ['grades'] });
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      toast.success('Bruker slettet');
      setDeleteTarget(null);
    },
    onError: () => toast.error('Kunne ikke slette bruker'),
  });

  const filtered = users.filter(
    (u) =>
      u.username.toLowerCase().includes(search.toLowerCase()) ||
      u.firstName?.toLowerCase().includes(search.toLowerCase()) ||
      u.lastName?.toLowerCase().includes(search.toLowerCase()) ||
      u.email?.toLowerCase().includes(search.toLowerCase()) ||
      u.institutionName?.toLowerCase().includes(search.toLowerCase()),
  );

  const roleBadge = (role: string) => {
    const colors: Record<string, string> = {
      SUPER_ADMIN: 'bg-badge-admin',
      INSTITUTION_ADMIN: 'bg-badge-admin',
      TEACHER: 'bg-badge-teacher',
      STUDENT: 'bg-badge-student',
    };
    return (
      <span
        className={`px-2 py-0.5 rounded-full text-[11px] font-semibold text-white ${colors[role] || 'bg-bg-hover'}`}
      >
        {role.replace('_', ' ')}
      </span>
    );
  };

  if (isLoading) return <LoadingState message="Laster brukere..." />;

  // SUPER_ADMIN view: institutions grouped by level with expandable admin lists
  if (isSuperAdmin) {
    return (
      <SuperAdminUsersView
        users={filtered}
        search={search}
        setSearch={setSearch}
        onAddUser={() => {
          setEditingUser(null);
          setShowForm(true);
        }}
        onEditUser={(u) => {
          setEditingUser(u);
          setShowForm(true);
        }}
        onDeleteUser={(u) => setDeleteTarget(u)}
        roleBadge={roleBadge}
        showForm={showForm}
        editingUser={editingUser}
        deleteTarget={deleteTarget}
        deleteMutation={deleteMutation}
        onCloseForm={() => {
          setShowForm(false);
          setEditingUser(null);
        }}
        onSaved={() => {
          setShowForm(false);
          setEditingUser(null);
          queryClient.invalidateQueries({ queryKey: ['users'] });
        }}
        onCancelDelete={() => setDeleteTarget(null)}
      />
    );
  }

  // INSTITUTION_ADMIN view: table of users
  return (
    <div>
      <PageHeader
        title="Brukere"
        description="Administrer brukere i din institusjon"
        action={
          <div className="flex gap-2">
            {currentUser?.institutionLevel === 'UNGDOMSSKOLE' && (
            <button
              onClick={() => setShowImport(true)}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors"
            >
              <Upload size={16} /> Importer elever
            </button>
            )}
            {currentUser?.institutionLevel === 'VGS' && (
            <button
              onClick={() => setShowTransfer(true)}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
            >
              <ArrowRightLeft size={16} /> Overfør elever
            </button>
            )}
            <button
              onClick={() => {
                setEditingUser(null);
                setShowForm(true);
              }}
              className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"
            >
              <Plus size={16} /> Legg til bruker
            </button>
          </div>
        }
      />

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Søk brukere..."
            className="w-full pl-9 pr-4 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus transition-colors"
        >
          <option value="">Alle roller</option>
          <option value="INSTITUTION_ADMIN">Institusjonsadmin</option>
          <option value="TEACHER">Lærer</option>
          <option value="STUDENT">Elev</option>
        </select>
      </div>

      {/* Table */}
      {filtered.length === 0 ? (
        <EmptyState title="Ingen brukere funnet" message="Prøv å justere søket eller filteret" />
      ) : (
        <div className="bg-bg-card border border-border rounded-xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-bg-primary/50 text-text-secondary text-left">
                <th className="px-4 py-3 font-medium">Brukernavn</th>
                <th className="px-4 py-3 font-medium">Navn</th>
                <th className="px-4 py-3 font-medium">Kjønn</th>
                <th className="px-4 py-3 font-medium">Fødselsdato</th>
                <th className="px-4 py-3 font-medium">E-post</th>
                <th className="px-4 py-3 font-medium">Telefon</th>
                <th className="px-4 py-3 font-medium">Rolle</th>
                <th className="px-4 py-3 font-medium w-24">Handlinger</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((user) => (
                <tr
                  key={user.id}
                  className="border-b border-border/50 hover:bg-bg-hover/50 transition-colors"
                >
                  <td className="px-4 py-3 font-medium text-text-primary">{user.username}</td>
                  <td className="px-4 py-3 text-text-secondary">
                    {user.firstName} {user.lastName}
                    {user.transferredFromInstitutionName && (
                      <span className="ml-2 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-info/10 text-info" title={`Overført fra ${user.transferredFromInstitutionName}`}>
                        ↗ Overført
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    {user.gender === 'MALE' ? 'Mann' : user.gender === 'FEMALE' ? 'Kvinne' : '—'}
                  </td>
                  <td className="px-4 py-3 text-text-secondary">{user.birthDate || '—'}</td>
                  <td className="px-4 py-3 text-text-secondary">{user.email || '—'}</td>
                  <td className="px-4 py-3 text-text-secondary">{user.phone || '—'}</td>
                  <td className="px-4 py-3">{roleBadge(user.role)}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      <button
                        onClick={() => {
                          setEditingUser(user);
                          setShowForm(true);
                        }}
                        className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"
                        title="Rediger"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => setDeleteTarget(user)}
                        className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"
                        title="Slett"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Form Modal */}
      {showForm && (
        <UserFormModal
          user={editingUser}
          onClose={() => {
            setShowForm(false);
            setEditingUser(null);
          }}
          onSaved={() => {
            setShowForm(false);
            setEditingUser(null);
            queryClient.invalidateQueries({ queryKey: ['users'] });
          }}
        />
      )}

      {/* Import Modal */}
      {showImport && (
        <ImportStudentsModal
          onClose={() => setShowImport(false)}
          onDone={() => {
            setShowImport(false);
            queryClient.invalidateQueries({ queryKey: ['users'] });
          }}
        />
      )}

      {/* Transfer Modal (VGS) */}
      {showTransfer && (
        <TransferStudentsModal
          onClose={() => setShowTransfer(false)}
          onDone={() => {
            setShowTransfer(false);
            queryClient.invalidateQueries({ queryKey: ['users'] });
          }}
        />
      )}

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Slett bruker"
        message={`Er du sikker på at du vil slette "${deleteTarget?.username}"? Denne handlingen kan ikke angres.`}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
        loading={deleteMutation.isPending}
      />
    </div>
  );
}

// ─── SUPER ADMIN: Institutions grouped view with expandable admin lists ───
function SuperAdminUsersView({
  users,
  search,
  setSearch,
  onAddUser,
  onEditUser,
  onDeleteUser,
  roleBadge,
  showForm,
  editingUser,
  deleteTarget,
  deleteMutation,
  onCloseForm,
  onSaved,
  onCancelDelete,
}: {
  users: User[];
  search: string;
  setSearch: (s: string) => void;
  onAddUser: () => void;
  onEditUser: (u: User) => void;
  onDeleteUser: (u: User) => void;
  roleBadge: (role: string) => React.ReactNode;
  showForm: boolean;
  editingUser: User | null;
  deleteTarget: User | null;
  deleteMutation: any;
  onCloseForm: () => void;
  onSaved: () => void;
  onCancelDelete: () => void;
}) {
  const { data: institutions = [], isLoading: instLoading } = useQuery({
    queryKey: ['institutions'],
    queryFn: getInstitutions,
  });
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  const toggle = (id: number) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  // Group institutions by level
  const grouped = useMemo(() => {
    const filtered = search.trim()
      ? institutions.filter(
          (i) =>
            i.name.toLowerCase().includes(search.toLowerCase()) ||
            (i.location && i.location.toLowerCase().includes(search.toLowerCase())),
        )
      : institutions;

    const groups: Record<string, Institution[]> = {};
    for (const level of LEVEL_ORDER) {
      const items = filtered.filter((i) => i.level === level);
      if (items.length > 0) groups[level] = items;
    }
    const uncategorized = filtered.filter((i) => !i.level || !LEVEL_ORDER.includes(i.level));
    if (uncategorized.length > 0) groups['UNCATEGORIZED'] = uncategorized;
    return groups;
  }, [institutions, search]);

  // Get admins for a specific institution
  const getAdminsForInstitution = (instId: number) =>
    users.filter((u) => u.institutionId === instId);

  if (instLoading) return <LoadingState message="Laster institusjoner..." />;

  return (
    <div>
      <PageHeader
        title="Brukere"
        description="Administrer institusjonsadministratorer på tvers av alle institusjoner"
        action={
          <button
            onClick={onAddUser}
            className="flex items-center gap-2 px-4 py-2 bg-accent hover:bg-accent-hover text-white text-sm font-medium rounded-lg transition-colors"
          >
            <Plus size={16} /> Legg til bruker
          </button>
        }
      />

      {/* Search */}
      <div className="mb-6">
        <div className="relative max-w-md">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Søk i institusjoner..."
            className="w-full pl-9 pr-4 py-2.5 bg-bg-secondary border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors"
          />
        </div>
      </div>

      {Object.keys(grouped).length === 0 ? (
        <EmptyState title="Ingen institusjoner funnet" message="Legg til en institusjon først" />
      ) : (
        <div className="space-y-8">
          {Object.entries(grouped).map(([level, items]) => {
            const config = LEVEL_CONFIG[level as InstitutionLevel];
            const isUncategorized = level === 'UNCATEGORIZED';

            return (
              <div key={level}>
                {/* Level header */}
                <div className="flex items-center gap-3 mb-4">
                  <div
                    className={`p-2 rounded-lg ${isUncategorized ? 'text-text-muted bg-bg-hover' : config.color}`}
                  >
                    {isUncategorized ? <Building2 size={20} /> : config.icon}
                  </div>
                  <div>
                    <h2 className="text-base font-semibold text-text-primary">
                      {isUncategorized ? 'Ikke kategorisert' : config.label}
                    </h2>
                    <p className="text-xs text-text-muted">
                      {items.length} {items.length === 1 ? 'institusjon' : 'institusjoner'}
                    </p>
                  </div>
                </div>

                {/* Institution cards with expandable admin lists */}
                <div className="space-y-3 pl-2">
                  {items.map((inst) => {
                    const isOpen = expanded.has(inst.id);
                    const admins = getAdminsForInstitution(inst.id);

                    return (
                      <div
                        key={inst.id}
                        className="bg-bg-secondary border border-border rounded-xl overflow-hidden hover:border-accent/30 transition-colors"
                      >
                        {/* Institution row — clickable */}
                        <button
                          onClick={() => toggle(inst.id)}
                          className="w-full flex items-center gap-3 p-4 text-left hover:bg-bg-hover/30 transition-colors"
                        >
                          <div
                            className={`p-2 rounded-lg shrink-0 ${isUncategorized ? 'text-text-muted bg-bg-hover' : config.color}`}
                          >
                            <Building2 size={20} />
                          </div>
                          <div className="flex-1 min-w-0">
                            <h3 className="font-semibold text-text-primary text-sm">{inst.name}</h3>
                            <p className="text-xs text-text-muted flex items-center gap-1 mt-0.5">
                              <MapPin size={11} /> {inst.location || 'Ingen lokasjon'}
                            </p>
                          </div>
                          <div className="flex items-center gap-3 shrink-0">
                            <span className="flex items-center gap-1.5 text-xs text-text-muted">
                              <Users size={14} />
                              {admins.length} {admins.length === 1 ? 'admin' : 'administratorer'}
                            </span>
                            <div className="text-text-muted transition-transform duration-200">
                              {isOpen ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                            </div>
                          </div>
                        </button>

                        {/* Expanded: admin list */}
                        {isOpen && (
                          <div className="border-t border-border bg-bg-primary/30">
                            {admins.length === 0 ? (
                              <p className="px-4 py-3 text-sm text-text-muted italic">
                                Ingen administratorer registrert for denne institusjonen
                              </p>
                            ) : (
                              <table className="w-full text-sm">
                                <thead>
                                  <tr className="border-b border-border/50 text-text-secondary text-left">
                                    <th className="px-4 py-2 font-medium text-xs">Brukernavn</th>
                                    <th className="px-4 py-2 font-medium text-xs">Navn</th>
                                    <th className="px-4 py-2 font-medium text-xs">Kjønn</th>
                                    <th className="px-4 py-2 font-medium text-xs">E-post</th>
                                    <th className="px-4 py-2 font-medium text-xs">Telefon</th>
                                    <th className="px-4 py-2 font-medium text-xs">Rolle</th>
                                    <th className="px-4 py-2 font-medium text-xs w-20">
                                      Handlinger
                                    </th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {admins.map((admin) => (
                                    <tr
                                      key={admin.id}
                                      className="border-b border-border/30 hover:bg-bg-hover/50 transition-colors"
                                    >
                                      <td className="px-4 py-2.5 font-medium text-text-primary">
                                        {admin.username}
                                      </td>
                                      <td className="px-4 py-2.5 text-text-secondary">
                                        {admin.firstName} {admin.lastName}
                                      </td>
                                      <td className="px-4 py-2.5 text-text-secondary">
                                        {admin.gender === 'MALE'
                                          ? 'Mann'
                                          : admin.gender === 'FEMALE'
                                            ? 'Kvinne'
                                            : '—'}
                                      </td>
                                      <td className="px-4 py-2.5 text-text-secondary">
                                        {admin.email || '—'}
                                      </td>
                                      <td className="px-4 py-2.5 text-text-secondary">
                                        {admin.phone || '—'}
                                      </td>
                                      <td className="px-4 py-2.5">{roleBadge(admin.role)}</td>
                                      <td className="px-4 py-2.5">
                                        <div className="flex gap-1">
                                          <button
                                            onClick={(e) => {
                                              e.stopPropagation();
                                              onEditUser(admin);
                                            }}
                                            className="p-1.5 rounded-md hover:bg-bg-hover text-text-muted hover:text-accent transition-colors"
                                            title="Rediger"
                                          >
                                            <Pencil size={14} />
                                          </button>
                                          <button
                                            onClick={(e) => {
                                              e.stopPropagation();
                                              onDeleteUser(admin);
                                            }}
                                            className="p-1.5 rounded-md hover:bg-danger/10 text-text-muted hover:text-danger transition-colors"
                                            title="Slett"
                                          >
                                            <Trash2 size={14} />
                                          </button>
                                        </div>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Form Modal */}
      {showForm && <UserFormModal user={editingUser} onClose={onCloseForm} onSaved={onSaved} />}

      {/* Delete confirmation */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Slett bruker"
        message={`Er du sikker på at du vil slette "${deleteTarget?.username}"? Denne handlingen kan ikke angres.`}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        onCancel={onCancelDelete}
        loading={deleteMutation.isPending}
      />
    </div>
  );
}

// ─── User Form Modal ───
function UserFormModal({
  user,
  onClose,
  onSaved,
}: {
  user: User | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { user: currentUser } = useAuth();
  const { data: institutions = [] } = useQuery({
    queryKey: ['institutions'],
    queryFn: () => import('../api/institutions').then((m) => m.getInstitutions()),
  });
  const isEditing = !!user;
  const [form, setForm] = useState<CreateUserRequest>({
    username: user?.username || '',
    password: '',
    role: user?.role || (currentUser?.role === 'SUPER_ADMIN' ? 'INSTITUTION_ADMIN' : 'STUDENT'),
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    email: user?.email || '',
    phone: user?.phone || '',
    gender: user?.gender,
    birthDate: user?.birthDate || '',
    institutionId: user?.institutionId || currentUser?.institutionId,
  });

  // When institutions load, default to the first one if no institutionId is set (SUPER_ADMIN)
  useEffect(() => {
    if (!form.institutionId && institutions.length > 0) {
      setForm((prev) => ({ ...prev, institutionId: institutions[0].id }));
    }
  }, [institutions]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (isEditing) {
        const { password, ...rest } = form;
        await updateUser(user!.id, password ? form : rest);
        toast.success('Bruker oppdatert');
      } else {
        // Username is auto-generated on backend from firstName + lastName
        const payload = { ...form, username: undefined };
        await createUser(payload as CreateUserRequest);
        toast.success('Bruker opprettet');
      }
      onSaved();
    } catch (err: any) {
      const msg =
        err.response?.data?.errors?.join('. ') || err.response?.data?.error || 'Kunne ikke lagre';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const update = (field: string, value: string | number | undefined) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-lg shadow-2xl">
        <button
          onClick={onClose}
          className="absolute top-3 right-3 text-text-muted hover:text-text-primary"
        >
          <X size={18} />
        </button>
        <h2 className="text-lg font-semibold text-text-primary mb-4">
          {isEditing ? 'Rediger bruker' : 'Opprett bruker'}
        </h2>

        {error && (
          <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field
              label="Fornavn"
              value={form.firstName || ''}
              onChange={(v) => update('firstName', v)}
              required
            />
            <Field
              label="Etternavn"
              value={form.lastName || ''}
              onChange={(v) => update('lastName', v)}
              required
            />
            {isEditing && (
              <Field
                label="Brukernavn"
                value={form.username || ''}
                onChange={(v) => update('username', v)}
                disabled
              />
            )}
            <Field
              label="Passord"
              value={form.password}
              onChange={(v) => update('password', v)}
              type="password"
              required={!isEditing}
              placeholder={isEditing ? '(uendret)' : ''}
            />
            <Field
              label="E-post"
              value={form.email || ''}
              onChange={(v) => update('email', v)}
              type="email"
            />
            <Field label="Telefon" value={form.phone || ''} onChange={(v) => update('phone', v)} />
            <Field
              label="Fødselsdato"
              value={form.birthDate || ''}
              onChange={(v) => update('birthDate', v)}
              type="date"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            {/* Gender */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">Kjønn</label>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => update('gender', 'MALE')}
                  className={`flex-1 px-3 py-2 text-sm font-medium rounded-lg border transition-colors ${
                    form.gender === 'MALE'
                      ? 'bg-blue-500/10 border-blue-500/30 text-blue-400'
                      : 'bg-bg-input border-border text-text-secondary hover:border-border-focus'
                  }`}
                >
                  Mann
                </button>
                <button
                  type="button"
                  onClick={() => update('gender', 'FEMALE')}
                  className={`flex-1 px-3 py-2 text-sm font-medium rounded-lg border transition-colors ${
                    form.gender === 'FEMALE'
                      ? 'bg-pink-500/10 border-pink-500/30 text-pink-400'
                      : 'bg-bg-input border-border text-text-secondary hover:border-border-focus'
                  }`}
                >
                  Kvinne
                </button>
              </div>
            </div>
            {/* Role */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">Rolle</label>
              <select
                value={form.role}
                onChange={(e) => update('role', e.target.value)}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
              >
                {currentUser?.role === 'SUPER_ADMIN' ? (
                  <option value="INSTITUTION_ADMIN">Institusjonsadmin</option>
                ) : (
                  <>
                    <option value="STUDENT">Elev</option>
                    <option value="TEACHER">Lærer</option>
                    <option value="INSTITUTION_ADMIN">Institusjonsadmin</option>
                  </>
                )}
              </select>
            </div>
          </div>
          {currentUser?.role === 'SUPER_ADMIN' && (
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1.5">
                Institusjon
              </label>
              <select
                value={form.institutionId || ''}
                onChange={(e) => update('institutionId', Number(e.target.value))}
                className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary focus:outline-none focus:border-border-focus"
              >
                {institutions.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name}
                  </option>
                ))}
              </select>
            </div>
          )}
          {!isEditing && (
            <div className="p-3 bg-accent/5 border border-accent/20 rounded-lg text-xs text-text-secondary">
              <span className="font-medium text-accent">ℹ️ Brukernavn:</span> Genereres automatisk
              fra fornavn og etternavn (f.eks.{' '}
              <span className="font-mono text-text-primary">
                {form.firstName && form.lastName
                  ? `${form.firstName.toLowerCase()}.${form.lastName.toLowerCase()}`
                  : 'ola.nordmann'}
              </span>
              )
            </div>
          )}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
            >
              Avbryt
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors disabled:opacity-50"
            >
              {loading ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Opprett'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  required,
  disabled,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-text-secondary mb-1.5">{label}</label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        disabled={disabled}
        placeholder={placeholder}
        className="w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus transition-colors disabled:opacity-50"
      />
    </div>
  );
}

// ─── Import Students Modal ───
function ImportStudentsModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState('');

  const handleFile = (f: File) => {
    const ext = f.name.toLowerCase();
    if (!ext.endsWith('.csv') && !ext.endsWith('.xlsx') && !ext.endsWith('.xls')) {
      setError('Ugyldig filformat. Bruk .csv eller .xlsx');
      return;
    }
    setError('');
    setFile(f);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) handleFile(e.dataTransfer.files[0]);
  };

  const handleUpload = async () => {
    if (!file) return;
    setLoading(true);
    setError('');
    try {
      const res = await importStudents(file);
      setResult(res);
      if (res.created > 0) toast.success(`${res.created} elever importert!`);
      if (res.errors.length > 0 && res.created === 0) {
        toast.error(`Import avvist: ${res.errors.length} feil funnet. Se detaljer i dialogen.`);
      } else if (res.errors.length > 0) {
        toast.warning(`${res.created} importert, men ${res.errors.length} rader hadde feil.`);
      }
    } catch (err: any) {
      const msg = err.response?.data?.error || 'Import feilet';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const downloadPasswords = () => {
    if (!result?.createdStudents?.length) return;
    const header = 'Brukernavn,Fornavn,Etternavn,E-post,F\u00f8dselsdato,Passord,Klasse\n';
    const rows = result.createdStudents
      .map(
        (s) =>
          `${s.username},${s.firstName},${s.lastName},${s.email},${s.birthDate},${s.password},${s.className}`,
      )
      .join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `passliste_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <button
          onClick={onClose}
          className="absolute top-3 right-3 text-text-muted hover:text-text-primary"
        >
          <X size={18} />
        </button>
        <div className="flex items-center gap-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-green-500/10 flex items-center justify-center">
            <FileSpreadsheet size={20} className="text-green-400" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Importer elever</h2>
            <p className="text-xs text-text-muted">Last opp CSV- eller Excel-fil med elevdata</p>
          </div>
        </div>

        {!result ? (
          <>
            {/* Format info */}
            <div className="mb-5 p-4 bg-bg-primary/50 border border-border rounded-lg">
              <h3 className="text-sm font-semibold text-text-primary mb-2">
                Påkrevde kolonner i filen:
              </h3>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  fornavn
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  etternavn
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  epost
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  telefon
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  kjønn
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  fødselsdato
                </span>
                <span className="px-2 py-1 bg-accent/10 text-accent rounded font-medium">
                  klasse
                </span>
              </div>
              <p className="text-xs text-text-muted mt-2">
                Kjønn: M/F, Mann/Kvinne, Gutt/Jente. Klasse må matche et eksisterende program
                (f.eks. 8A, 8B).
              </p>
              <p className="text-xs text-text-muted mt-1">
                Brukernavn og passord genereres automatisk.
              </p>
            </div>

            {/* Drop zone */}
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer ${
                dragOver
                  ? 'border-accent bg-accent/5'
                  : file
                    ? 'border-green-500/30 bg-green-500/5'
                    : 'border-border hover:border-accent/50'
              }`}
              onClick={() => {
                const input = document.createElement('input');
                input.type = 'file';
                input.accept = '.csv,.xlsx,.xls';
                input.onchange = (e) => {
                  const f = (e.target as HTMLInputElement).files?.[0];
                  if (f) handleFile(f);
                };
                input.click();
              }}
            >
              {file ? (
                <div className="flex flex-col items-center gap-2">
                  <FileSpreadsheet size={32} className="text-green-400" />
                  <p className="text-sm font-medium text-text-primary">{file.name}</p>
                  <p className="text-xs text-text-muted">{(file.size / 1024).toFixed(1)} KB</p>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setFile(null);
                    }}
                    className="text-xs text-danger hover:underline"
                  >
                    Fjern fil
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2">
                  <Upload size={32} className="text-text-muted" />
                  <p className="text-sm text-text-primary font-medium">Dra og slipp fil her</p>
                  <p className="text-xs text-text-muted">
                    eller klikk for å velge fil (.csv, .xlsx)
                  </p>
                </div>
              )}
            </div>

            {error && (
              <div className="mt-3 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
                {error}
              </div>
            )}

            <div className="flex justify-end gap-3 mt-5">
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
              >
                Avbryt
              </button>
              <button
                onClick={handleUpload}
                disabled={!file || loading}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-green-600 hover:bg-green-700 text-white transition-colors disabled:opacity-50"
              >
                {loading ? (
                  <>
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />{' '}
                    Importerer...
                  </>
                ) : (
                  <>
                    <Upload size={16} /> Importer elever
                  </>
                )}
              </button>
            </div>
          </>
        ) : (
          <>
            {/* Results */}
            <div className="space-y-4">
              <div className="flex gap-4">
                <div className="flex-1 p-4 bg-green-400/5 border border-green-400/20 rounded-lg text-center">
                  <CheckCircle2 size={24} className="text-green-400 mx-auto mb-1" />
                  <p className="text-2xl font-bold text-green-400">{result.created}</p>
                  <p className="text-xs text-text-muted">Opprettet</p>
                </div>
                <div className="flex-1 p-4 bg-orange-400/5 border border-orange-400/20 rounded-lg text-center">
                  <AlertTriangle size={24} className="text-orange-400 mx-auto mb-1" />
                  <p className="text-2xl font-bold text-orange-400">{result.skipped}</p>
                  <p className="text-xs text-text-muted">Hoppet over</p>
                </div>
                <div className="flex-1 p-4 bg-bg-primary/50 border border-border rounded-lg text-center">
                  <Users size={24} className="text-text-muted mx-auto mb-1" />
                  <p className="text-2xl font-bold text-text-primary">{result.total}</p>
                  <p className="text-xs text-text-muted">Totalt i fil</p>
                </div>
              </div>

              {/* Errors */}
              {result.errors.length > 0 && (
                <div className="p-3 bg-danger/5 border border-danger/20 rounded-lg">
                  <h4 className="text-sm font-medium text-danger mb-2">
                    Feil ({result.errors.length})
                  </h4>
                  <ul className="space-y-1 max-h-32 overflow-y-auto">
                    {result.errors.map((err, i) => (
                      <li key={i} className="text-xs text-text-muted">
                        • {err}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Created students preview */}
              {result.createdStudents.length > 0 && (
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="text-sm font-semibold text-text-primary">Opprettede elever</h4>
                    <button
                      onClick={downloadPasswords}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent hover:bg-accent-hover text-white rounded-lg transition-colors"
                    >
                      <Download size={13} /> Last ned passliste (CSV)
                    </button>
                  </div>
                  <div className="bg-bg-primary/50 border border-border rounded-lg overflow-hidden max-h-48 overflow-y-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-border text-text-secondary text-left">
                          <th className="px-3 py-2 font-medium">Brukernavn</th>
                          <th className="px-3 py-2 font-medium">Navn</th>
                          <th className="px-3 py-2 font-medium">E-post</th>
                          <th className="px-3 py-2 font-medium">Passord</th>
                          <th className="px-3 py-2 font-medium">Klasse</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.createdStudents.map((s) => (
                          <tr key={s.id} className="border-b border-border/30">
                            <td className="px-3 py-1.5 font-mono text-accent">{s.username}</td>
                            <td className="px-3 py-1.5 text-text-primary">
                              {s.firstName} {s.lastName}
                            </td>
                            <td className="px-3 py-1.5 text-text-secondary">{s.email}</td>
                            <td className="px-3 py-1.5 font-mono text-green-400">{s.password}</td>
                            <td className="px-3 py-1.5 text-text-secondary">
                              {s.className || '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 mt-5">
              {result.createdStudents.length > 0 && (
                <button
                  onClick={async () => {
                    if (
                      !confirm(
                        `Er du sikker p\u00e5 at du vil angre importen og slette ${result.createdStudents.length} elever?`,
                      )
                    )
                      return;
                    try {
                      const ids = result.createdStudents.map((s) => s.id);
                      const res = await batchDeleteUsers(ids);
                      toast.success(`${res.deleted} elever slettet`);
                      onDone();
                    } catch (err: any) {
                      toast.error(
                        'Kunne ikke angre: ' + (err.response?.data?.error || err.message),
                      );
                    }
                  }}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg border border-danger/30 text-danger hover:bg-danger/10 transition-colors"
                >
                  <Undo2 size={16} /> Angre import
                </button>
              )}
              <button
                onClick={onDone}
                className="px-4 py-2 text-sm font-medium rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors"
              >
                Ferdig
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ─── Transfer Students Modal (VGS) ───
function TransferStudentsModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<TransferResult | null>(null);
  const [error, setError] = useState('');

  const handleFile = (f: File) => {
    const ext = f.name.toLowerCase();
    if (!ext.endsWith('.csv') && !ext.endsWith('.xlsx') && !ext.endsWith('.xls')) {
      setError('Ugyldig filformat. Bruk .csv eller .xlsx');
      return;
    }
    setError('');
    setFile(f);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files.length > 0) handleFile(e.dataTransfer.files[0]);
  };

  const handleUpload = async () => {
    if (!file) return;
    setLoading(true);
    setError('');
    try {
      const res = await transferStudentsBatch(file);
      setResult(res);
      if (res.transferred > 0 && res.errors.length === 0) {
        toast.success(`${res.transferred} elever overført!`);
      } else if (res.errors.length > 0 && res.transferred === 0) {
        toast.error(`Overføring avvist: ${res.errors.length} feil funnet.`);
      }
    } catch (err: any) {
      const msg = err.response?.data?.error || 'Overføring feilet';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-2xl shadow-2xl max-h-[90vh] overflow-y-auto">
        <button
          onClick={onClose}
          className="absolute top-3 right-3 text-text-muted hover:text-text-primary"
        >
          <X size={18} />
        </button>
        <div className="flex items-center gap-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-blue-500/10 flex items-center justify-center">
            <ArrowRightLeft size={20} className="text-blue-400" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Overfør elever</h2>
            <p className="text-xs text-text-muted">Flytt eksisterende elever fra en annen VGS til din institusjon</p>
          </div>
        </div>

        {!result ? (
          <>
            {/* Info box */}
            <div className="mb-4 p-3 bg-blue-400/5 border border-blue-400/20 rounded-lg">
              <p className="text-xs text-blue-300">
                💡 Elevene beholder brukernavn, passord, karakterer og all historikk. De flyttes til din institusjon og plasseres i riktig linje og trinn.
              </p>
            </div>

            {/* Format info */}
            <div className="mb-5 p-4 bg-bg-primary/50 border border-border rounded-lg">
              <h3 className="text-sm font-semibold text-text-primary mb-2">
                Påkrevde kolonner i filen:
              </h3>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <span className="px-2 py-1 bg-blue-500/10 text-blue-400 rounded font-medium">
                  epost
                </span>
                <span className="px-2 py-1 bg-blue-500/10 text-blue-400 rounded font-medium">
                  linje
                </span>
                <span className="px-2 py-1 bg-blue-500/10 text-blue-400 rounded font-medium">
                  trinn
                </span>
              </div>
              <p className="text-xs text-text-muted mt-2">
                Epost brukes til å finne eleven i systemet. Linje må matche et eksisterende program (f.eks. Studiespesialisering). Trinn = VG1, VG2 eller VG3.
              </p>
              <p className="text-xs text-text-muted mt-1">
                Alternativt kan du bruke <strong>brukernavn</strong> i stedet for epost.
              </p>
            </div>

            {/* Drop zone */}
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer ${
                dragOver
                  ? 'border-blue-400 bg-blue-400/5'
                  : file
                    ? 'border-blue-500/30 bg-blue-500/5'
                    : 'border-border hover:border-blue-400/50'
              }`}
              onClick={() => {
                const input = document.createElement('input');
                input.type = 'file';
                input.accept = '.csv,.xlsx,.xls';
                input.onchange = (e) => {
                  const f = (e.target as HTMLInputElement).files?.[0];
                  if (f) handleFile(f);
                };
                input.click();
              }}
            >
              {file ? (
                <div className="flex flex-col items-center gap-2">
                  <FileSpreadsheet size={32} className="text-blue-400" />
                  <p className="text-sm font-medium text-text-primary">{file.name}</p>
                  <p className="text-xs text-text-muted">{(file.size / 1024).toFixed(1)} KB</p>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setFile(null);
                    }}
                    className="text-xs text-danger hover:underline"
                  >
                    Fjern fil
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2">
                  <Upload size={32} className="text-text-muted" />
                  <p className="text-sm text-text-primary font-medium">Dra og slipp fil her</p>
                  <p className="text-xs text-text-muted">
                    eller klikk for å velge fil (.csv, .xlsx)
                  </p>
                </div>
              )}
            </div>

            {error && (
              <div className="mt-3 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
                {error}
              </div>
            )}

            <div className="flex justify-end gap-3 mt-5">
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
              >
                Avbryt
              </button>
              <button
                onClick={handleUpload}
                disabled={!file || loading}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-blue-600 hover:bg-blue-700 text-white transition-colors disabled:opacity-50"
              >
                {loading ? (
                  <>
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />{' '}
                    Overfører...
                  </>
                ) : (
                  <>
                    <ArrowRightLeft size={16} /> Overfør elever
                  </>
                )}
              </button>
            </div>
          </>
        ) : (
          <>
            {/* Results */}
            <div className="space-y-4">
              <div className="flex gap-4">
                <div className="flex-1 p-4 bg-blue-400/5 border border-blue-400/20 rounded-lg text-center">
                  <CheckCircle2 size={24} className="text-blue-400 mx-auto mb-1" />
                  <p className="text-2xl font-bold text-blue-400">{result.transferred}</p>
                  <p className="text-xs text-text-muted">Overført</p>
                </div>
                {result.errors.length > 0 && (
                  <div className="flex-1 p-4 bg-danger/5 border border-danger/20 rounded-lg text-center">
                    <AlertTriangle size={24} className="text-danger mx-auto mb-1" />
                    <p className="text-2xl font-bold text-danger">{result.errors.length}</p>
                    <p className="text-xs text-text-muted">Feil</p>
                  </div>
                )}
              </div>

              {/* Transferred students list */}
              {result.transferredStudents && result.transferredStudents.length > 0 && (
                <div className="border border-border rounded-lg overflow-hidden">
                  <div className="px-4 py-2 bg-bg-primary/50 border-b border-border">
                    <p className="text-xs font-semibold text-text-secondary">Overførte elever</p>
                  </div>
                  <div className="max-h-48 overflow-y-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-border/50 text-text-muted">
                          <th className="px-3 py-1.5 text-left font-medium">Brukernavn</th>
                          <th className="px-3 py-1.5 text-left font-medium">Navn</th>
                          <th className="px-3 py-1.5 text-left font-medium">Linje</th>
                          <th className="px-3 py-1.5 text-left font-medium">Trinn</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.transferredStudents.map((s, i) => (
                          <tr key={i} className="border-b border-border/30">
                            <td className="px-3 py-1.5 font-medium text-text-primary">{s.username}</td>
                            <td className="px-3 py-1.5 text-text-secondary">{s.fullName}</td>
                            <td className="px-3 py-1.5 text-text-secondary">{s.program}</td>
                            <td className="px-3 py-1.5 text-text-secondary">{s.yearLevel}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Errors */}
              {result.errors.length > 0 && (
                <div className="border border-danger/20 rounded-lg overflow-hidden">
                  <div className="px-4 py-2 bg-danger/5 border-b border-danger/20">
                    <p className="text-xs font-semibold text-danger">Feil ({result.errors.length})</p>
                  </div>
                  <div className="max-h-40 overflow-y-auto p-3 space-y-1">
                    {result.errors.map((err, i) => (
                      <p key={i} className="text-xs text-danger/80">• {err}</p>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 mt-5">
              <button
                onClick={onDone}
                className="px-4 py-2 text-sm font-medium rounded-lg bg-accent hover:bg-accent-hover text-white transition-colors"
              >
                Ferdig
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
