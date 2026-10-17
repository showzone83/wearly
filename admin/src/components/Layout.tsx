import { NavLink, Outlet } from 'react-router';

import { API_URL } from '@/api';
import { useAuthStore } from '@/stores/useAuthStore';

const MENU = [
  { to: '/', label: '대시보드', icon: '◧', end: true },
  { to: '/codes', label: '코드 관리', icon: '☰' },
  { to: '/ai/providers', label: 'AI 공급자', icon: '✦' },
  { to: '/ai/logs', label: 'AI 분석 기록', icon: '≡' },
  { to: '/users', label: '회원', icon: '◔' },
  { to: '/audit', label: '변경 이력', icon: '↺' },
];

export const Layout = () => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">
          <span className="brand-name">Wearly</span>
          <span className="brand-tag">관리자</span>
        </div>
        <ul className="menu">
          {MENU.map((m) => (
            <li key={m.to}>
              <NavLink to={m.to} end={m.end} className={({ isActive }) => (isActive ? 'menu-link active' : 'menu-link')}>
                <span className="menu-icon" aria-hidden>
                  {m.icon}
                </span>
                {m.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="sidebar-foot">
          <div className="me">
            <div className="me-name">{user?.name ?? '관리자'}</div>
            <div className="me-email">{user?.email ?? ''}</div>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void logout()}>
            로그아웃
          </button>
          <div className="api-url" title="VITE_API_URL">
            {API_URL}
          </div>
        </div>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
};
