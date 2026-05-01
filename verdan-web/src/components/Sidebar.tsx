import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useQuery } from '@tanstack/react-query';
import { getUnreadCount } from '../api/chat';
import { ChatSocketProvider } from '../contexts/ChatSocketProvider';
import {
  Users,
  BookOpen,
  GraduationCap,
  ClipboardList,
  DoorOpen,
  Calendar,
  LayoutDashboard,
  LogOut,
  Menu,
  X,
  Building2,
  MessageSquare,
  FileSearch,
  BookOpenCheck,
} from 'lucide-react';
import { useState } from 'react';
import type { Role } from '../types';
import { ThemeToggle } from './ThemeToggle';

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
  roles: Role[];
}

/** Maps institution level to the label for the "Subjects" nav item */
const SUBJECTS_LABEL: Record<string, string> = {
  UNGDOMSSKOLE: 'Klasser',
  VGS: 'Linjer',
  FAGSKOLE: 'Fagskole Grader',
  UNIVERSITET: 'Grader',
};

const getNavItems = (institutionLevel?: string): NavItem[] => [
  {
    to: '/dashboard',
    label: 'Oversikt',
    icon: <LayoutDashboard size={20} />,
    roles: ['SUPER_ADMIN', 'INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  {
    to: '/institutions',
    label: 'Institusjoner',
    icon: <Building2 size={20} />,
    roles: ['SUPER_ADMIN'],
  },
  {
    to: '/users',
    label: 'Brukere',
    icon: <Users size={20} />,
    roles: ['SUPER_ADMIN', 'INSTITUTION_ADMIN'],
  },
  {
    to: '/subjects',
    label: SUBJECTS_LABEL[institutionLevel || ''] || 'Fag',
    icon: <BookOpen size={20} />,
    roles: ['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  {
    to: '/grades',
    label: 'Karakterer',
    icon: <GraduationCap size={20} />,
    roles: ['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  {
    to: '/attendance',
    label: 'Fravær',
    icon: <ClipboardList size={20} />,
    roles: ['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  { to: '/rooms', label: 'Rom', icon: <DoorOpen size={20} />, roles: ['INSTITUTION_ADMIN'] },
  {
    to: '/bookings',
    label: 'Timeplan',
    icon: <Calendar size={20} />,
    roles: ['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  {
    to: '/chat',
    label: 'Chat',
    icon: <MessageSquare size={20} />,
    roles: ['SUPER_ADMIN', 'INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
  {
    to: '/reports',
    label: 'Uteksaminerte',
    icon: <GraduationCap size={20} />,
    roles: ['INSTITUTION_ADMIN'],
  },
  {
    to: '/portal',
    label: 'Søknadsportal',
    icon: <FileSearch size={20} />,
    roles: ['INSTITUTION_ADMIN', 'STUDENT'],
  },
  {
    to: '/student-portal',
    label: 'Elevportalen',
    icon: <BookOpenCheck size={20} />,
    roles: ['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT'],
  },
];

export function Sidebar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  // Unread count — initial load + WebSocket updates the cache
  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['unreadCount'],
    queryFn: getUnreadCount,
    staleTime: 30000,
    refetchOnMount: true,
    enabled: !!user,
  });

  if (!user) return null;

  const filteredItems = getNavItems(user.institutionLevel).filter((item) =>
    item.roles.includes(user.role),
  );

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const roleBadgeColor = {
    SUPER_ADMIN: 'bg-badge-admin',
    INSTITUTION_ADMIN: 'bg-badge-admin',
    TEACHER: 'bg-badge-teacher',
    STUDENT: 'bg-badge-student',
  }[user.role];

  return (
    <ChatSocketProvider>
      <div className="flex h-screen">
        {/* Sidebar */}
        <aside
          className={`bg-sidebar-bg border-r border-border flex flex-col transition-all duration-300 ${collapsed ? 'w-16' : 'w-60'}`}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b border-border">
            {!collapsed && (
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 bg-accent rounded-lg flex items-center justify-center font-bold text-white text-sm">
                  V
                </div>
                <span className="font-semibold text-text-primary text-sm">Verdan</span>
              </div>
            )}
            <button
              onClick={() => setCollapsed(!collapsed)}
              className="p-1 rounded hover:bg-sidebar-hover text-text-secondary transition-colors"
            >
              {collapsed ? <Menu size={18} /> : <X size={18} />}
            </button>
          </div>

          {/* Navigation */}
          <nav className="flex-1 p-2 space-y-1 overflow-y-auto">
            {filteredItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200
                ${
                  isActive
                    ? 'bg-accent text-white shadow-lg shadow-accent/20'
                    : 'text-text-secondary hover:bg-sidebar-hover hover:text-text-primary'
                }
                ${collapsed ? 'justify-center' : ''}`
                }
                title={collapsed ? item.label : undefined}
              >
                <div className="relative">
                  {item.icon}
                  {item.to === '/chat' && unreadCount > 0 && (
                    <span className="absolute -top-1.5 -right-1.5 min-w-[16px] h-4 px-1 flex items-center justify-center text-[9px] font-bold text-white bg-danger rounded-full">
                      {unreadCount > 99 ? '99+' : unreadCount}
                    </span>
                  )}
                </div>
                {!collapsed && <span>{item.label}</span>}
              </NavLink>
            ))}
          </nav>

          {/* User footer */}
          <div className="p-3 border-t border-border flex flex-col gap-1">
            {!collapsed && (
              <div className="flex items-center gap-2 mb-2 px-2">
                <div className="w-8 h-8 bg-bg-hover rounded-full flex items-center justify-center text-xs font-semibold text-text-primary">
                  {user.firstName?.charAt(0) || user.username.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-text-primary truncate">{user.username}</p>
                  <div className="flex flex-col gap-0.5">
                    <span
                      className={`inline-block text-[10px] font-semibold px-1.5 py-0.5 rounded ${roleBadgeColor} text-white w-fit`}
                    >
                      {user.role}
                    </span>
                    {user.institutionName && (
                      <span className="text-[10px] text-text-secondary truncate">
                        {user.institutionName}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            )}
            <ThemeToggle collapsed={collapsed} />
            <button
              onClick={handleLogout}
              className={`flex items-center gap-2 w-full px-3 py-2 rounded-lg text-sm text-danger hover:bg-danger/10 transition-colors ${collapsed ? 'justify-center p-2' : ''}`}
            >
              <LogOut size={18} />
              {!collapsed && <span>Logg ut</span>}
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main className="flex-1 overflow-auto p-6 bg-bg-primary">
          <Outlet />
        </main>
      </div>
    </ChatSocketProvider>
  );
}
