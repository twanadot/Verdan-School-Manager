import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AuthProvider } from './auth/AuthProvider';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RoleGuard } from './auth/RoleGuard';
import { Sidebar } from './components/Sidebar';
import { ThemeProvider, useTheme } from './contexts/ThemeProvider';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { UsersPage } from './pages/UsersPage';
import { SubjectsPage } from './pages/SubjectsPage';
import { GradesPage } from './pages/GradesPage';
import { AttendancePage } from './pages/AttendancePage';
import { RoomsPage } from './pages/RoomsPage';
import { BookingsPage } from './pages/BookingsPage';
import { ReportsPage } from './pages/ReportsPage';
import { InstitutionsPage } from './pages/InstitutionsPage';
import { ChatPage } from './pages/ChatPage';
import { AdmissionsPortalPage } from './pages/AdmissionsPortalPage';
import { PortalPage } from './pages/PortalPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
  },
});

function ThemeAwareToaster() {
  const { activeTheme } = useTheme();
  return (
    <Toaster
      position="top-right"
      theme={activeTheme}
      toastOptions={{
        className: 'bg-bg-card border-border text-text-primary',
        style: {
          background: 'var(--bg-card)',
          border: '1px solid var(--border)',
          color: 'var(--text-primary)',
        },
      }}
    />
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider defaultTheme="system" storageKeyTheme="verdan-theme">
        <AuthProvider>
          <BrowserRouter>
            <ThemeAwareToaster />
            <Routes>
              <Route path="/login" element={<LoginPage />} />

            {/* Protected routes with sidebar layout */}
            <Route element={<ProtectedRoute />}>
              <Route element={<Sidebar />}>
                <Route path="/dashboard" element={<DashboardPage />} />

                {/* Super Admin only */}
                <Route element={<RoleGuard allowed={['SUPER_ADMIN']} />}>
                  <Route path="/institutions" element={<InstitutionsPage />} />
                </Route>

                {/* User management */}
                <Route element={<RoleGuard allowed={['SUPER_ADMIN', 'INSTITUTION_ADMIN']} />}>
                  <Route path="/users" element={<UsersPage />} />
                </Route>

                {/* Institution-level management */}
                <Route element={<RoleGuard allowed={['INSTITUTION_ADMIN']} />}>
                  <Route path="/rooms" element={<RoomsPage />} />
                </Route>

                {/* Admin + Teacher */}
                <Route element={<RoleGuard allowed={['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT']} />}>
                  <Route path="/bookings" element={<BookingsPage />} />
                </Route>

                {/* Institution roles only */}
                <Route element={<RoleGuard allowed={['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT']} />}>
                  <Route path="/subjects" element={<SubjectsPage />} />
                  <Route path="/grades" element={<GradesPage />} />
                  <Route path="/attendance" element={<AttendancePage />} />
                </Route>
                {/* Søknadsportal — students and institution admins */}
                <Route element={<RoleGuard allowed={['INSTITUTION_ADMIN', 'STUDENT']} />}>
                  <Route path="/portal" element={<AdmissionsPortalPage />} />
                </Route>
                {/* Elevportalen — teachers + students */}
                <Route element={<RoleGuard allowed={['INSTITUTION_ADMIN', 'TEACHER', 'STUDENT']} />}>
                  <Route path="/student-portal" element={<PortalPage />} />
                </Route>
                <Route path="/chat" element={<ChatPage />} />
                <Route path="/reports" element={<ReportsPage />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
