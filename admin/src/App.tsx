import type { ReactNode } from 'react';

import { Layout } from '@/components/Layout';
import { AiLogsPage } from '@/pages/AiLogsPage';
import { AppThemePage } from '@/pages/AppThemePage';
import { AuditLogsPage } from '@/pages/AuditLogsPage';
import { CodesPage } from '@/pages/codes/CodesPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { LoginPage } from '@/pages/LoginPage';
import { ProvidersPage } from '@/pages/providers/ProvidersPage';
import { SessionsPage } from '@/pages/SessionsPage';
import { UsersPage } from '@/pages/UsersPage';
import { WeatherCallsPage } from '@/pages/weather/WeatherCallsPage';
import { WeatherProvidersPage } from '@/pages/weather/WeatherProvidersPage';
import { WeatherSchedulePage } from '@/pages/weather/WeatherSchedulePage';
import { WeatherSnapshotsPage } from '@/pages/weather/WeatherSnapshotsPage';

import { useAuthStore } from '@/stores/useAuthStore';

import { Navigate, Route, Routes, useLocation } from 'react-router';

const RequireAdmin = ({ children }: { children: ReactNode }) => {
  const user = useAuthStore((s) => s.user);
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
};

export const App = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAdmin>
            <Layout />
          </RequireAdmin>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="codes" element={<CodesPage />} />
        <Route path="ai/providers" element={<ProvidersPage />} />
        <Route path="ai/logs" element={<AiLogsPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="sessions" element={<SessionsPage />} />
        <Route path="weather" element={<Navigate to="/weather/schedule" replace />} />
        <Route path="weather/schedule" element={<WeatherSchedulePage />} />
        <Route path="weather/providers" element={<WeatherProvidersPage />} />
        <Route path="weather/calls" element={<WeatherCallsPage />} />
        <Route path="weather/snapshots" element={<WeatherSnapshotsPage />} />
        <Route path="app" element={<Navigate to="/app/theme" replace />} />
        <Route path="app/theme" element={<AppThemePage />} />
        <Route path="audit" element={<AuditLogsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
};
