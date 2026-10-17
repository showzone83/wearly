// 옷 일러스트 미리보기와 색 칩. 모양은 앱 데이터(mobile/src/data/garmentShapes.ts)를 그대로 쓰고,
// 그리는 순서·색 규칙은 앱 GarmentArt와 같다 (패턴은 그리지 않는다).
import { garmentLayers, shade } from '@/lib/garment';

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
  const fill = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : '#A3A7AE';
  const edge = shade(fill, light > 0.6 ? -0.28 : -0.12);
  const g = garmentLayers(shape, category);
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label={title ?? g.key}>
      {title ? <title>{title}</title> : null}
      <path d={g.body} fill={fill} stroke={edge} strokeWidth={1.4} strokeLinejoin="round" />
      {g.light ? <path d={g.light} fill="#FFFFFF" stroke={edge} strokeWidth={2.5} strokeLinejoin="round" /> : null}
      {g.details ? (
        <path
          d={g.details}
          fill="none"
          stroke={edge}
          strokeWidth={g.detailWidth}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      ) : null}
      {g.buttons.map(([x, y]) => (
        <circle key={`${x}-${y}`} cx={x} cy={y} r={1.6} fill={edge} />
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
