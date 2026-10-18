// 앱 테마 미리보기: 색 견본과 작은 앱 화면(홈 화면 모양을 그 테마 색으로 줄여 그림, doc/12 5장).
// 색은 컨테이너에 CSS 변수(--t-primary 등)로 넣고 styles.css의 .tp-* 클래스가 읽는다.
import { MascotImage } from '@/components/Mascot';

import { COLOR_TOKENS, themeVars } from '@/lib/appTheme';
import type { ITheme } from '@/lib/appTheme';

export const ColorSwatches = ({ theme }: { theme: ITheme }) => {
  return (
    <ul className="swatches">
      {COLOR_TOKENS.map((t) => (
        <li key={t.key} className="swatch" title={`${t.label} · ${theme.colors[t.key]}`}>
          <span className="swatch-color" style={{ background: theme.colors[t.key] }} aria-hidden />
          <span className="swatch-text">
            <span className="swatch-key mono">{t.key}</span>
            <span className="swatch-hex mono">{theme.colors[t.key]}</span>
          </span>
        </li>
      ))}
    </ul>
  );
};

const TABS = ['홈', '내 옷장', '+', '코디추천', '더보기'];

/**
 * 작은 앱 화면: 인사 + 마스코트, 날씨 카드, 통계 카드, 칩, 코디 카드, 주 버튼, 하단 탭.
 * seasonGreeting을 주면 앱 홈처럼 인사 위에 계절 인사 알약을 그린다(이슈 #6). compact는 계절 미리보기 격자용 작은 크기
 */
export const MiniAppPreview = ({
  theme,
  seasonGreeting,
  compact,
  label,
}: {
  theme: ITheme;
  seasonGreeting?: string;
  compact?: boolean;
  label?: string;
}) => {
  return (
    <div
      className={compact ? 'tp-phone tp-compact' : 'tp-phone'}
      style={themeVars(theme)}
      aria-label={label ?? `${theme.name} 앱 화면 미리보기`}
      role="img"
    >
      <div className="tp-screen">
        <div className="tp-greet">
          <div>
            {seasonGreeting ? <div className="tp-season">{seasonGreeting}</div> : null}
            <div className="tp-title">안녕하세요!</div>
            <div className="tp-sub">오늘 뭐 입지? 이제 고민 끝.</div>
          </div>
          <MascotImage mascot={theme.mascot} pose="hello" size={46} />
        </div>
        <div className="tp-card tp-weather">
          <span className="tp-sun" aria-hidden>
            ☀
          </span>
          <span className="tp-temp">23°</span>
          <span className="tp-sub">맑음 ›</span>
        </div>
        <div className="tp-stats">
          {[
            ['24', '등록한 옷'],
            ['3', '오늘 추천'],
            ['68%', '활용도'],
          ].map(([v, l]) => (
            <div key={l} className="tp-card tp-stat">
              <div className="tp-stat-v">{v}</div>
              <div className="tp-muted">{l}</div>
            </div>
          ))}
        </div>
        <div className="tp-chips">
          <span className="tp-chip on">전체</span>
          <span className="tp-chip">상의</span>
          <span className="tp-chip">하의</span>
          <span className="tp-chip">아우터</span>
        </div>
        <div className="tp-card tp-outfit">
          <div className="tp-outfit-art">
            <span className="tp-badge">NEW</span>
            <span className="tp-heart" aria-hidden>
              ♥
            </span>
          </div>
          <div className="tp-outfit-text">
            <div className="tp-strong">이런 코디는 어때요?</div>
            <div className="tp-muted">네이비 셔츠 · 베이지 슬랙스</div>
          </div>
        </div>
        <div className="tp-button">이 코디로 입어볼래요 ✨</div>
      </div>
      <div className="tp-tabbar">
        {TABS.map((t, i) =>
          t === '+' ? (
            <span key={t} className="tp-plus">
              +
            </span>
          ) : (
            <span key={t} className={i === 0 ? 'tp-tab on' : 'tp-tab'}>
              {t}
            </span>
          ),
        )}
      </div>
    </div>
  );
};
