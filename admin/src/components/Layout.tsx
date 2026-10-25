import { API_URL } from '@/api';

import { Button, Notice } from '@/components/ui';

import { useAuthStore } from '@/stores/useAuthStore';

import { NavLink, Outlet, useNavigate } from 'react-router';

import { lowRecoveryCodesText } from '@/lib/mfa';

const MENU = [
  { to: '/', label: '대시보드', icon: '◧', end: true },
  { to: '/codes', label: '코드 관리', icon: '☰' },
  { to: '/ai/providers', label: 'AI 공급자', icon: '✦' },
  { to: '/ai/logs', label: 'AI 분석 기록', icon: '≡' },
  { to: '/users', label: '회원', icon: '◔' },
  // 초대 코드로 가입한 이력·초대를 보낸 이력 (doc/25)
  { to: '/invites', label: '친구 초대', icon: '✉' },
  // 옷장 도우미 연결·대리 작업 기록·강제 해제 (doc/26 6단계)
  { to: '/pairings', label: '옷장 도우미', icon: '♡' },
  // 탈퇴 기록: 사유별 집계·탈퇴 예정 회원 취소·바로 삭제 (doc/27 4단계)
  { to: '/withdrawals', label: '탈퇴 기록', icon: '⌫' },
  // 지금 로그인돼 있는 세션과 강제 로그아웃 (doc/16 5장)
  { to: '/sessions', label: '로그인 사용자', icon: '◉' },
  // 관리자 계정의 로그인·2단계 인증 기록 (doc/22)
  { to: '/auth-events', label: '관리자 로그인 기록', icon: '⚿' },
  // 서버가 주기적으로 받아 두는 날씨: 스케줄·공급자·호출 이력·저장된 날씨 (doc/17 6장)
  { to: '/weather', label: '날씨', icon: '☁' },
  // 앱 설정: 지금은 테마 하나 (doc/12 6장)
  { to: '/app/theme', label: '앱 설정', icon: '◐' },
  { to: '/audit', label: '변경 이력', icon: '↺' },
];

export const Layout = () => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const recoveryCodesLeft = useAuthStore((s) => s.recoveryCodesLeft);
  const setRecoveryCodesLeft = useAuthStore((s) => s.setRecoveryCodesLeft);
  const navigate = useNavigate();

  /** 로그인 때 받은 남은 복구 코드가 적으면 화면 위에 띄운다 (doc/22) */
  const lowCodes = lowRecoveryCodesText(recoveryCodesLeft);

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
              <NavLink
                to={m.to}
                end={m.end}
                className={({ isActive }) => (isActive ? 'menu-link active' : 'menu-link')}
              >
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
          <div className="sidebar-actions">
            <NavLink to="/me" className={({ isActive }) => `btn btn-sm ${isActive ? 'btn-primary' : 'btn-secondary'}`}>
              내 계정
            </NavLink>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => void logout()}>
              로그아웃
            </button>
          </div>
          <div className="api-url" title="VITE_API_URL">
            {API_URL}
          </div>
        </div>
      </nav>
      <main className="content">
        {lowCodes ? (
          <Notice tone="warn">
            <div className="top-banner">
              <span>{lowCodes}</span>
              <Button size="sm" onClick={() => navigate('/me')}>
                내 계정으로
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setRecoveryCodesLeft(null)}>
                닫기
              </Button>
            </div>
          </Notice>
        ) : null}
        <Outlet />
      </main>
    </div>
  );
};
