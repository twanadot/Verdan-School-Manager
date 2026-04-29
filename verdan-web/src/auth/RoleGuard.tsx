import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import type { Role } from '../types';

interface RoleGuardProps {
  allowed: Role[];
}

/** Only renders children if the user's role is in `allowed` */
export function RoleGuard({ allowed }: RoleGuardProps) {
  const { user } = useAuth();
  if (!user || !allowed.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
