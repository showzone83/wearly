// 옷 일러스트 미리보기와 색 칩. 모양·색 규칙은 앱 데이터(mobile/src/data/garmentShapes.ts)를 그대로 쓰고,
// 그리는 순서는 앱 GarmentArt와 같다 (패턴은 그리지 않는다).
import { useId } from 'react';

import { GARMENT_FOLD_OPACITY, GARMENT_FOLD_WIDTH, garmentLayers, garmentTones } from '@/lib/garment';

export const GarmentPreview = ({
  shape,
  category,
  hex = '#22305A',
  light = 0.2,
  size = 120,
  title,
}: {
  shape: string | null | undefined;
  category: string | null | undefined;
  hex?: string;
  light?: number;
  size?: number;
  title?: string;
}) => {
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, '');

  const fill = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : '#A3A7AE';
  const tone = garmentTones(fill, light);
  const g = garmentLayers(shape, category);
  const fillId = `gp-fill-${uid}`;
  const shadowId = `gp-shadow-${uid}`;
  const clipId = `gp-clip-${uid}`;
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label={title ?? g.key}>
      {title ? <title>{title}</title> : null}
      <defs>
        <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={tone.top} />
          <stop offset="0.45" stopColor={tone.fill} />
          <stop offset="1" stopColor={tone.bottom} />
        </linearGradient>
        <radialGradient id={shadowId} cx="50%" cy="50%" r="50%">
          <stop offset="0" stopColor="#5A4632" stopOpacity={0.22} />
          <stop offset="0.6" stopColor="#5A4632" stopOpacity={0.1} />
          <stop offset="1" stopColor="#5A4632" stopOpacity={0} />
        </radialGradient>
        <clipPath id={clipId}>
          <path d={g.body} />
        </clipPath>
      </defs>
      <ellipse cx={g.shadow.cx} cy={g.shadow.cy} rx={g.shadow.rx} ry={g.shadow.ry} fill={`url(#${shadowId})`} />
      <path d={g.body} fill={`url(#${fillId})`} stroke={tone.edge} strokeWidth={0.9} strokeLinejoin="round" />
      {g.folds ? (
        <path
          d={g.folds}
          fill="none"
          stroke={tone.line}
          strokeWidth={GARMENT_FOLD_WIDTH}
          strokeOpacity={GARMENT_FOLD_OPACITY}
          strokeLinecap="round"
          clipPath={`url(#${clipId})`}
        />
      ) : null}
      {g.light ? <path d={g.light} fill="#FFFFFF" stroke={tone.edge} strokeWidth={2.2} strokeLinejoin="round" /> : null}
      {g.details ? (
        <path
          d={g.details}
          fill="none"
          stroke={tone.line}
          strokeOpacity={0.85}
          strokeWidth={g.detailWidth}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      ) : null}
      {g.buttons.map(([x, y]) => (
        <circle key={`${x}-${y}`} cx={x} cy={y} r={1.5} fill={tone.line} />
      ))}
    </svg>
  );
};

export const ColorChip = ({ hex, size = 16, title }: { hex: string | undefined; size?: number; title?: string }) => {
  const valid = !!hex && /^#[0-9a-fA-F]{6}$/.test(hex);
  return (
    <span
      className={`chip-dot ${valid ? '' : 'chip-dot-empty'}`}
      style={{ width: size, height: size, background: valid ? hex : undefined }}
      title={title ?? hex}
      aria-hidden
    />
  );
};
