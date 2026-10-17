// 마스코트 그림 미리보기. 앱 Mascot(react-native-svg)과 같은 데이터·같은 규칙(fill 기본값 none, 위에서부터 차례로)으로
// DOM SVG를 그린다 (doc/12 4장).
import { mascotArt } from '@/lib/appTheme';
import type { IMascotShape, TMascotId, TMascotPose } from '@/lib/appTheme';

const renderShape = (s: IMascotShape, i: number) => {
  const common = {
    fill: s.fill ?? 'none',
    stroke: s.stroke,
    strokeWidth: s.strokeWidth,
    strokeLinecap: s.strokeLinecap,
    strokeLinejoin: s.strokeLinejoin,
    opacity: s.opacity,
    transform: s.transform,
  };
  switch (s.type) {
    case 'path':
      return <path key={i} d={s.d ?? ''} {...common} />;
    case 'circle':
      return <circle key={i} cx={s.cx} cy={s.cy} r={s.r} {...common} />;
    case 'ellipse':
      return <ellipse key={i} cx={s.cx} cy={s.cy} rx={s.rx} ry={s.ry} {...common} />;
    case 'rect':
      return <rect key={i} x={s.x} y={s.y} width={s.width} height={s.height} rx={s.rx} ry={s.ry} {...common} />;
    default:
      return null;
  }
};

export const MascotSvg = ({
  mascot,
  pose,
  size = 96,
  title,
}: {
  mascot: TMascotId;
  pose: TMascotPose;
  size?: number;
  title?: string;
}) => {
  const art = mascotArt(mascot, pose);
  return (
    <svg
      width={size}
      height={size}
      viewBox={art.viewBox}
      role="img"
      aria-label={title ?? `${mascot} ${pose}`}
      data-mascot={mascot}
      data-pose={pose}
    >
      {title ? <title>{title}</title> : null}
      {art.shapes.map(renderShape)}
    </svg>
  );
};
