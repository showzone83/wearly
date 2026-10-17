// 마스코트 그림 (doc/12 4장). 그림 데이터는 data/mascots, 어떤 마스코트를 쓸지는 현재 테마가 정한다.
import Svg, { Circle, Ellipse, Path, Rect } from 'react-native-svg';

import { mascotArt, type IMascotShape, type TMascotId, type TMascotPose } from '@/data/mascots';
import { useThemeStore } from '@/store/useThemeStore';

interface IMascotProps {
  pose: TMascotPose;
  size?: number;
  /** 지정하지 않으면 현재 테마의 마스코트 */
  mascot?: TMascotId;
}

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
      return <Path key={i} d={s.d ?? ''} {...common} />;
    case 'circle':
      return <Circle key={i} cx={s.cx} cy={s.cy} r={s.r} {...common} />;
    case 'ellipse':
      return <Ellipse key={i} cx={s.cx} cy={s.cy} rx={s.rx} ry={s.ry} {...common} />;
    case 'rect':
      return <Rect key={i} x={s.x} y={s.y} width={s.width} height={s.height} rx={s.rx} ry={s.ry} {...common} />;
  }
};

export const Mascot = ({ pose, size = 120, mascot }: IMascotProps) => {
  const themeMascot = useThemeStore((s) => s.theme.mascot);

  const art = mascotArt(mascot ?? themeMascot, pose);

  return (
    <Svg width={size} height={size} viewBox={art.viewBox}>
      {art.shapes.map(renderShape)}
    </Svg>
  );
};
