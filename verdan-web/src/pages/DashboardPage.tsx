import { useAuth } from '../auth/AuthProvider';
import { PageHeader } from '../components/PageHeader';
import { Users, BookOpen, GraduationCap, ClipboardList, DoorOpen, Calendar, Building2, BarChart3, MessageSquare, FileSearch } from 'lucide-react';
import { Link } from 'react-router-dom';

export function DashboardPage() {
  const { user } = useAuth();

  if (!user) return null;

  const isSuperAdmin = user.role === 'SUPER_ADMIN';
  const isInstAdmin = user.role === 'INSTITUTION_ADMIN';
  const isAdmin = isSuperAdmin || isInstAdmin;

  return (
    <div>
      <PageHeader
        title={`Velkommen, ${user.firstName || user.username}`}
        description={`Du er logget inn som ${user.role.replace(/_/g, ' ').toLowerCase()}`}
      />

      {/* Institution info card for INSTITUTION_ADMIN */}
      {isInstAdmin && user.institutionName && (
        <div className="mb-6 bg-bg-card border border-accent/20 rounded-xl p-5 flex items-center gap-4">
          <div className="p-3 bg-accent/10 rounded-xl">
            <Building2 size={24} className="text-accent" />
          </div>
          <div>
            <p className="text-xs text-text-muted font-medium uppercase tracking-wider">Din institusjon</p>
            <h3 className="text-lg font-semibold text-text-primary">{user.institutionName}</h3>
            {user.institutionLevel && (
              <span className="inline-block mt-1 text-xs font-medium px-2 py-0.5 rounded-full bg-accent/10 text-accent">
                {user.institutionLevel}
              </span>
            )}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {isSuperAdmin && (
          <DashCard to="/institutions" icon={<Building2 />} title="Institusjoner" description="Administrer skoler og campuser" color="text-rose-400" />
        )}
        {isSuperAdmin && (
          <>
            <DashCard to="/users" icon={<Users />} title="Brukere" description="Administrer administratorer for hver institusjon" color="text-purple-400" />
            <DashCard to="/chat" icon={<MessageSquare />} title="Chat" description="Send og motta meldinger" color="text-teal-400" />
          </>
        )}
        {isInstAdmin && (
          <>
            <DashCard to="/users" icon={<Users />} title="Brukere" description="Administrer elever, lærere og administratorer" color="text-purple-400" />
            <DashCard to="/subjects" icon={<BookOpen />} title="Fag" description="Administrer kurs og fag" color="text-blue-400" />
            <DashCard to="/grades" icon={<GraduationCap />} title="Karakterer" description="Se og administrer alle karakterer" color="text-green-400" />
            <DashCard to="/attendance" icon={<ClipboardList />} title="Fravær" description="Følg opp elevfravær" color="text-amber-400" />
            <DashCard to="/rooms" icon={<DoorOpen />} title="Rom" description="Administrer rom og kapasitet" color="text-cyan-400" />
            <DashCard to="/bookings" icon={<Calendar />} title="Timeplan" description="Planlegg rombookinger" color="text-pink-400" />
            <DashCard to="/chat" icon={<MessageSquare />} title="Chat" description="Send og motta meldinger" color="text-teal-400" />
            <DashCard to="/reports" icon={<GraduationCap />} title="Uteksaminerte" description="Se uteksaminerte elever" color="text-indigo-400" />
            <DashCard to="/portal" icon={<FileSearch />} title="Søknadsportal" description="Administrer opptak og søknader" color="text-orange-400" />
          </>
        )}
        {user.role === 'TEACHER' && (
          <>
            <DashCard to="/subjects" icon={<BookOpen />} title="Fag" description="Se dine kurs" color="text-blue-400" />
            <DashCard to="/grades" icon={<GraduationCap />} title="Karakterer" description="Administrer elevkarakterer" color="text-green-400" />
            <DashCard to="/attendance" icon={<ClipboardList />} title="Fravær" description="Registrer fravær" color="text-amber-400" />
            <DashCard to="/bookings" icon={<Calendar />} title="Timeplan" description="Book rom for undervisning" color="text-pink-400" />
            <DashCard to="/chat" icon={<MessageSquare />} title="Chat" description="Send og motta meldinger" color="text-teal-400" />
          </>
        )}
        {user.role === 'STUDENT' && (
          <>
            <DashCard to="/subjects" icon={<BookOpen />} title="Fag" description="Se fagene du er meldt opp i" color="text-blue-400" />
            <DashCard to="/grades" icon={<GraduationCap />} title="Mine karakterer" description="Sjekk dine resultater" color="text-green-400" />
            <DashCard to="/attendance" icon={<ClipboardList />} title="Mitt fravær" description="Se din fraværsoversikt" color="text-amber-400" />
            <DashCard to="/chat" icon={<MessageSquare />} title="Chat" description="Send og motta meldinger" color="text-teal-400" />
            <DashCard to="/portal" icon={<FileSearch />} title="Søknadsportal" description="Søk på studier og programmer" color="text-orange-400" />
          </>
        )}
      </div>
    </div>
  );
}

function DashCard({ to, icon, title, description, color }: {
  to: string; icon: React.ReactNode; title: string; description: string; color: string;
}) {
  return (
    <Link
      to={to}
      className="group bg-bg-card border border-border rounded-xl p-5 hover:border-accent/50 hover:shadow-lg hover:shadow-accent/5 transition-all duration-300"
    >
      <div className={`${color} mb-3 group-hover:scale-110 transition-transform duration-300`}>
        {icon}
      </div>
      <h3 className="font-semibold text-text-primary group-hover:text-accent transition-colors">{title}</h3>
      <p className="text-sm text-text-secondary mt-1">{description}</p>
    </Link>
  );
}
