import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { useColors } from '@/theme';

export type IconName =
  | 'home'
  | 'hanger'
  | 'sparkle'
  | 'calendar'
  | 'user'
  | 'plus'
  | 'back'
  | 'chevron'
  | 'search'
  | 'sun'
  | 'cloud'
  | 'rain'
  | 'bell'
  | 'pin'
  | 'close'
  | 'swap'
  | 'star'
  | 'camera'
  | 'image'
  | 'trash'
  | 'check'
  | 'heart'
  | 'share'
  | 'grid'
  | 'filter'
  | 'sort'
  | 'shirt'
  | 'pants'
  | 'jacket'
  | 'dress'
  | 'blazer'
  | 'tee'
  | 'gear'
  | 'help'
  | 'doc'
  | 'clipboard'
  | 'chart'
  | 'refresh'
  | 'arrowRight'
  | 'logout'
  | 'palette'
  | 'magic';

export const condIcon = (cond: 'SUNNY' | 'CLOUDY' | 'RAIN'): IconName =>
  cond === 'SUNNY' ? 'sun' : cond === 'RAIN' ? 'rain' : 'cloud';

interface IIconProps {
  name: IconName;
  size?: number;
  /** 지정하지 않으면 테마의 본문 색 */
  color?: string;
  filled?: boolean;
  strokeWidth?: number;
}

// 보드의 얇은 선 아이콘을 SVG로 직접 그린다 (외부 아이콘 폰트 미사용)
export const Icon = ({ name, size = 22, color, filled, strokeWidth = 1.6 }: IIconProps) => {
  const text = useColors().text;

  const c = color ?? text;
  const stroke = {
    stroke: c,
    strokeWidth,
    fill: 'none',
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  };
  const fillable = { ...stroke, fill: filled ? c : 'none' };

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {name === 'home' && (
        <Path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z" {...fillable} />
      )}
      {name === 'hanger' && (
        <Path
          d="M12 7.5a2 2 0 1 1 2-2c0 1-.8 1.4-2 2.2V9l8.5 6.2c.8.6.4 1.8-.6 1.8H4.1c-1 0-1.4-1.2-.6-1.8L12 9"
          {...stroke}
        />
      )}
      {name === 'sparkle' && (
        <>
          <Path
            d="M11 3.5c.5 4 2.5 6 6.5 6.5-4 .5-6 2.5-6.5 6.5-.5-4-2.5-6-6.5-6.5 4-.5 6-2.5 6.5-6.5z"
            {...fillable}
          />
          <Path
            d="M18 15c.2 1.6 1 2.4 2.5 2.5-1.5.2-2.3 1-2.5 2.5-.2-1.5-1-2.3-2.5-2.5 1.5-.1 2.3-.9 2.5-2.5z"
            {...fillable}
          />
        </>
      )}
      {name === 'calendar' && (
        <>
          <Rect x="4" y="5.5" width="16" height="14.5" rx="2.5" {...stroke} />
          <Path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" {...stroke} />
        </>
      )}
      {name === 'user' && (
        <>
          <Circle cx="12" cy="8.5" r="3.8" {...fillable} />
          <Path d="M4.5 20c.8-3.8 3.7-6 7.5-6s6.7 2.2 7.5 6" {...stroke} />
        </>
      )}
      {name === 'plus' && <Path d="M12 5v14M5 12h14" {...stroke} />}
      {name === 'back' && <Path d="M15 5l-7 7 7 7" {...stroke} />}
      {name === 'chevron' && <Path d="M9 5l7 7-7 7" {...stroke} />}
      {name === 'arrowRight' && <Path d="M5 12h14M13 6l6 6-6 6" {...stroke} />}
      {name === 'search' && (
        <>
          <Circle cx="11" cy="11" r="6" {...stroke} />
          <Path d="M20 20l-4.5-4.5" {...stroke} />
        </>
      )}
      {name === 'sun' && (
        <>
          <Circle cx="12" cy="12" r="4.2" fill={c} />
          <Path
            d="M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8"
            {...stroke}
          />
        </>
      )}
      {name === 'cloud' && (
        <Path d="M7 18h10.5a3.5 3.5 0 0 0 .3-7A5.5 5.5 0 0 0 7.2 9.5 4.3 4.3 0 0 0 7 18z" {...fillable} />
      )}
      {name === 'rain' && (
        <>
          <Path d="M7 14.5h10.5a3.5 3.5 0 0 0 .3-7A5.5 5.5 0 0 0 7.2 6 4.3 4.3 0 0 0 7 14.5z" {...fillable} />
          <Path d="M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5" {...stroke} />
        </>
      )}
      {name === 'bell' && (
        <>
          <Path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z" {...stroke} />
          <Path d="M10 20.5a2 2 0 0 0 4 0" {...stroke} />
        </>
      )}
      {name === 'pin' && <Path d="M9 4h6l-1 5 3 3H7l3-3zM12 12v8" {...fillable} />}
      {name === 'close' && <Path d="M6 6l12 12M18 6L6 18" {...stroke} />}
      {name === 'swap' && <Path d="M7 7h11l-3-3M17 17H6l3 3" {...stroke} />}
      {name === 'refresh' && (
        <>
          <Path d="M19 12a7 7 0 1 1-2.1-5" {...stroke} />
          <Path d="M17.5 3.5v3.8h-3.8" {...stroke} />
        </>
      )}
      {name === 'star' && (
        <Path d="M12 3.8l2.5 5.2 5.6.7-4.1 3.9 1 5.6L12 16.5l-5 2.7 1-5.6-4.1-3.9 5.6-.7z" {...fillable} />
      )}
      {name === 'heart' && (
        <Path
          d="M12 19.5s-7.5-4.4-7.5-9.6A4.1 4.1 0 0 1 12 7.6a4.1 4.1 0 0 1 7.5 2.3c0 5.2-7.5 9.6-7.5 9.6z"
          {...fillable}
        />
      )}
      {name === 'share' && (
        <>
          <Circle cx="17.5" cy="6" r="2.3" {...stroke} />
          <Circle cx="6.5" cy="12" r="2.3" {...stroke} />
          <Circle cx="17.5" cy="18" r="2.3" {...stroke} />
          <Path d="M8.6 10.9l6.8-3.8M8.6 13.1l6.8 3.8" {...stroke} />
        </>
      )}
      {name === 'grid' && (
        <>
          {[6, 12, 18].map((x) => [6, 12, 18].map((y) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={1.35} fill={c} />))}
        </>
      )}
      {name === 'filter' && (
        <>
          <Path d="M4 7h9M17 7h3M4 17h3M11 17h9" {...stroke} />
          <Circle cx="15" cy="7" r="2" {...stroke} />
          <Circle cx="9" cy="17" r="2" {...stroke} />
        </>
      )}
      {name === 'sort' && <Path d="M8 5v14M8 19l-3-3M8 19l3-3M16 19V5M16 5l-3 3M16 5l3 3" {...stroke} />}
      {name === 'shirt' && (
        <Path
          d="M9 4.5 4.5 6.8 3 11l3 1.2V19.5h12V12.2L21 11l-1.5-4.2L15 4.5c-.6 1.3-1.7 2-3 2s-2.4-.7-3-2z"
          {...fillable}
        />
      )}
      {name === 'tee' && (
        <>
          <Path
            d="M8.5 4.5 3.5 7.5l1.8 3.2 2.2-1.1V20h9V9.6l2.2 1.1 1.8-3.2-5-3c-.5 1.2-1.8 2-3.5 2s-3-.8-3.5-2z"
            {...fillable}
          />
        </>
      )}
      {name === 'pants' && <Path d="M6.5 4h11l1.2 16h-4.4L12 10l-2.3 10H5.3z M6.5 7.5h11" {...fillable} />}
      {name === 'jacket' && (
        <>
          <Path d="M9 4 5 6l-1.5 13.5H8V12M15 4l4 2 1.5 13.5H16V12" {...stroke} />
          <Path d="M9 4l3 5 3-5M12 9v11M8 19.5h8" {...stroke} />
        </>
      )}
      {name === 'blazer' && (
        <>
          <Path d="M8.5 4 4.5 6.5 4 20h16l-.5-13.5L15.5 4" {...stroke} />
          <Path d="M8.5 4 12 12l3.5-8M12 12v8M9.5 15.5h1" {...stroke} />
        </>
      )}
      {name === 'dress' && (
        <Path d="M9.5 3.5v3L8 9.5l1.2 1.5L5.5 20h13l-3.7-9 1.2-1.5L14.5 6.5v-3M9.5 6.5h5" {...fillable} />
      )}
      {name === 'camera' && (
        <>
          <Path
            d="M4 8.5a1.5 1.5 0 0 1 1.5-1.5h2.3L9.3 5h5.4l1.5 2h2.3A1.5 1.5 0 0 1 20 8.5V18a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18z"
            {...stroke}
          />
          <Circle cx="12" cy="13" r="3.3" {...stroke} />
        </>
      )}
      {name === 'image' && (
        <>
          <Rect x="4" y="5" width="16" height="14" rx="2.5" {...stroke} />
          <Circle cx="9" cy="10" r="1.6" {...stroke} />
          <Path d="M5 17l4.5-4 3 2.5L16 12l3 3" {...stroke} />
        </>
      )}
      {name === 'trash' && <Path d="M5 7h14M10 4h4M7 7l1 13h8l1-13M10.5 11v5M13.5 11v5" {...stroke} />}
      {name === 'check' && <Path d="M5 12.5l4.5 4.5L19 7.5" {...stroke} />}
      {name === 'gear' && (
        <>
          <Circle cx="12" cy="12" r="3" {...stroke} />
          <Path
            d="M12 3.5l1.6 1.9 2.4-.6.7 2.4 2.4.7-.6 2.4 1.9 1.7-1.9 1.6.6 2.4-2.4.7-.7 2.4-2.4-.6L12 20.5l-1.6-1.9-2.4.6-.7-2.4-2.4-.7.6-2.4L3.5 12l1.9-1.6-.6-2.4 2.4-.7.7-2.4 2.4.6z"
            {...stroke}
          />
        </>
      )}
      {name === 'help' && (
        <>
          <Circle cx="12" cy="12" r="8.5" {...stroke} />
          <Path d="M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.6" {...stroke} />
          <Circle cx="12" cy="16.8" r="0.9" fill={c} />
        </>
      )}
      {name === 'doc' && (
        <>
          <Path d="M6.5 3.5h7.5l4 4v13h-11.5z" {...stroke} />
          <Path d="M13.5 3.5v4.5h4.5M9.5 12.5h5M9.5 16h5" {...stroke} />
        </>
      )}
      {name === 'clipboard' && (
        <>
          <Rect x="5" y="5" width="14" height="15.5" rx="2.5" {...stroke} />
          <Path d="M9 5V3.8h6V5M8.5 13l2.3 2.3 4.7-4.6" {...stroke} />
        </>
      )}
      {name === 'chart' && (
        <>
          <Path d="M4 20h16" {...stroke} />
          <Rect x="6" y="11" width="3" height="6.5" rx="1" {...stroke} />
          <Rect x="10.5" y="6.5" width="3" height="11" rx="1" {...stroke} />
          <Rect x="15" y="13.5" width="3" height="4" rx="1" {...stroke} />
        </>
      )}
      {name === 'logout' && (
        <Path d="M10 4.5H6a1.5 1.5 0 0 0-1.5 1.5v12A1.5 1.5 0 0 0 6 19.5h4M14 8l4 4-4 4M18 12H9" {...stroke} />
      )}
      {name === 'palette' && (
        <>
          <Path
            d="M12 3.5a8.5 8.5 0 0 0 0 17c1.3 0 1.8-1 1.3-2-.6-1.2.2-2.5 1.5-2.5h2.2a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9-8.5-9z"
            {...stroke}
          />
          <Circle cx="8" cy="11" r="1.2" fill={c} />
          <Circle cx="11" cy="7.5" r="1.2" fill={c} />
          <Circle cx="15.5" cy="8.5" r="1.2" fill={c} />
        </>
      )}
      {name === 'magic' && (
        <>
          <Path d="M9 4.5 4.5 6.8 3 11l3 1.2V19.5h9" {...stroke} />
          <Path d="M15 4.5 19.5 6.8 21 11" {...stroke} />
          <Path d="M9 4.5c.6 1.3 1.7 2 3 2s2.4-.7 3-2" {...stroke} />
          <Path
            d="M18.5 13.5c.3 1.6 1 2.3 2.5 2.5-1.5.2-2.2.9-2.5 2.5-.3-1.6-1-2.3-2.5-2.5 1.5-.2 2.2-.9 2.5-2.5z"
            {...fillable}
          />
        </>
      )}
    </Svg>
  );
};
