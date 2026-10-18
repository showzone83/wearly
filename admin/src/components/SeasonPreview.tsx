// 계절 테마 미리보기 (이슈 #6): 4계절 × 2테마. 칸마다 작은 앱 화면, 계절이 바꾸는 색 견본, 가장 낮은 글자 대비.
// 색은 앱 코드(mobile/src/data/seasons.ts)의 덮어쓰기를 테마에 합친 값 그대로다.
import { MiniAppPreview } from '@/components/ThemePreview';
import { Badge } from '@/components/ui';

import {
  minTextContrast,
  SEASON_INFO,
  SEASON_SWATCH_TOKENS,
  seasonalTheme,
  SEASONS,
  THEME_IDS,
  THEMES,
} from '@/lib/appTheme';
import { contrastGrade, fmtContrast, SEASON_PERIOD } from '@/lib/seasonMode';
import type { TSeason, TThemeId } from '@/lib/appTheme';

const SeasonCell = ({ themeId, season, active }: { themeId: TThemeId; season: TSeason; active: boolean }) => {
  const theme = seasonalTheme(THEMES[themeId], season);
  const info = SEASON_INFO[season];
  const worst = minTextContrast(theme.colors);
  const grade = contrastGrade(worst.ratio);
  return (
    <figure className={active ? 'season-cell season-cell-active' : 'season-cell'}>
      <figcaption className="season-cell-head">
        <strong>{theme.name}</strong>
        {active ? (
          <Badge tone="green" title="지금 사용자 앱에 보이는 조합">
            ● 지금 앱
          </Badge>
        ) : null}
      </figcaption>
      <MiniAppPreview
        theme={theme}
        compact
        seasonGreeting={`${info.icon} ${info.greeting}`}
        label={`${theme.name} ${info.label} 앱 화면 미리보기`}
      />
      <ul className="season-swatches">
        {SEASON_SWATCH_TOKENS.map((t) => (
          <li key={t.key} title={`${t.label} · ${t.key} · ${theme.colors[t.key]}`}>
            <span className="swatch-color" style={{ background: theme.colors[t.key] }} aria-hidden />
            <span className="mono">{theme.colors[t.key]}</span>
          </li>
        ))}
      </ul>
      <div className="small">
        글자 대비 최저 <span className="mono">{fmtContrast(worst.ratio)}</span>{' '}
        <Badge tone={grade === '부족' ? 'red' : 'green'} title={worst.pair}>
          {grade}
        </Badge>
      </div>
    </figure>
  );
};

/** 계절 4줄 × 테마 2칸. active는 지금 사용자 앱에 보이는 조합 (테마·계절) */
export const SeasonPreviewGrid = ({ active }: { active: { themeId: string | null; season: TSeason | null } }) => {
  return (
    <div className="season-grid">
      {SEASONS.map((season) => (
        <section key={season} className="season-row" aria-label={`${SEASON_INFO[season].label} 미리보기`}>
          <div className="season-row-head">
            <span className="season-row-icon" aria-hidden>
              {SEASON_INFO[season].icon}
            </span>
            <div>
              <div className="season-row-name">{SEASON_INFO[season].label}</div>
              <div className="muted small">{SEASON_PERIOD[season]}</div>
            </div>
          </div>
          {THEME_IDS.map((id) => (
            <SeasonCell
              key={id}
              themeId={id}
              season={season}
              active={active.themeId === id && active.season === season}
            />
          ))}
        </section>
      ))}
    </div>
  );
};
