import Svg, { Circle, Path, Rect } from 'react-native-svg';

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
  | 'check';

export function condIcon(cond: 'SUNNY' | 'CLOUDY' | 'RAIN'): IconName {
  return cond === 'SUNNY' ? 'sun' : cond === 'RAIN' ? 'rain' : 'cloud';
}

interface Props {
  name: IconName;
  size?: number;
  color?: string;
  filled?: boolean;
}

// 목업의 얇은 선 아이콘을 SVG로 직접 그린다 (외부 아이콘 폰트 미사용)
export function Icon({ name, size = 22, color = '#111', filled }: Props) {
  const stroke = { stroke: color, strokeWidth: 1.7, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {name === 'home' && <Path d="M4 10.5 12 4l8 6.5V20h-5v-6H9v6H4z" {...stroke} />}
      {name === 'hanger' && (
        <Path d="M12 7.5a2 2 0 1 1 2-2c0 1-.8 1.4-2 2.2V9l8.5 6.2c.8.6.4 1.8-.6 1.8H4.1c-1 0-1.4-1.2-.6-1.8L12 9" {...stroke} />
      )}
      {name === 'sparkle' && (
        <>
          <Path d="M11 3.5c.5 4 2.5 6 6.5 6.5-4 .5-6 2.5-6.5 6.5-.5-4-2.5-6-6.5-6.5 4-.5 6-2.5 6.5-6.5z" {...stroke} />
          <Path d="M18 15c.2 1.6 1 2.4 2.5 2.5-1.5.2-2.3 1-2.5 2.5-.2-1.5-1-2.3-2.5-2.5 1.5-.1 2.3-.9 2.5-2.5z" {...stroke} />
        </>
      )}
      {name === 'calendar' && (
        <>
          <Rect x="4" y="5.5" width="16" height="14.5" rx="2" {...stroke} />
          <Path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" {...stroke} />
        </>
      )}
      {name === 'user' && (
        <>
          <Circle cx="12" cy="8.5" r="3.8" {...stroke} />
          <Path d="M4.5 20c.8-3.8 3.7-6 7.5-6s6.7 2.2 7.5 6" {...stroke} />
        </>
      )}
      {name === 'plus' && <Path d="M12 5v14M5 12h14" {...stroke} />}
      {name === 'back' && <Path d="M15 5l-7 7 7 7" {...stroke} />}
      {name === 'chevron' && <Path d="M9 5l7 7-7 7" {...stroke} />}
      {name === 'search' && (
        <>
          <Circle cx="11" cy="11" r="6" {...stroke} />
          <Path d="M20 20l-4.5-4.5" {...stroke} />
        </>
      )}
      {name === 'sun' && (
        <>
          <Circle cx="12" cy="12" r="4" fill={color} />
          <Path d="M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8" {...stroke} />
        </>
      )}
      {name === 'cloud' && (
        <Path d="M7 18h10.5a3.5 3.5 0 0 0 .3-7A5.5 5.5 0 0 0 7.2 9.5 4.3 4.3 0 0 0 7 18z" fill={filled ? color : 'none'} stroke={color} strokeWidth={1.7} />
      )}
      {name === 'rain' && (
        <>
          <Path d="M7 14.5h10.5a3.5 3.5 0 0 0 .3-7A5.5 5.5 0 0 0 7.2 6 4.3 4.3 0 0 0 7 14.5z" {...stroke} />
          <Path d="M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5" {...stroke} />
        </>
      )}
      {name === 'bell' && (
        <>
          <Path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z" {...stroke} />
          <Path d="M10 20.5a2 2 0 0 0 4 0" {...stroke} />
        </>
      )}
      {name === 'pin' && (
        <Path d="M9 4h6l-1 5 3 3H7l3-3zM12 12v8" fill={filled ? color : 'none'} stroke={color} strokeWidth={1.7} strokeLinejoin="round" />
      )}
      {name === 'close' && <Path d="M6 6l12 12M18 6L6 18" {...stroke} />}
      {name === 'swap' && <Path d="M7 7h11l-3-3M17 17H6l3 3" {...stroke} />}
      {name === 'star' && (
        <Path
          d="M12 3.8l2.5 5.2 5.6.7-4.1 3.9 1 5.6L12 16.5l-5 2.7 1-5.6-4.1-3.9 5.6-.7z"
          fill={filled ? color : 'none'}
          stroke={color}
          strokeWidth={1.5}
          strokeLinejoin="round"
        />
      )}
      {name === 'camera' && (
        <>
          <Path d="M4 8h3.5L9 5.5h6L16.5 8H20v11H4z" {...stroke} />
          <Circle cx="12" cy="13" r="3.3" {...stroke} />
        </>
      )}
      {name === 'image' && (
        <>
          <Rect x="4" y="5" width="16" height="14" rx="2" {...stroke} />
          <Circle cx="9" cy="10" r="1.6" {...stroke} />
          <Path d="M5 17l4.5-4 3 2.5L16 12l3 3" {...stroke} />
        </>
      )}
      {name === 'trash' && <Path d="M5 7h14M10 4h4M7 7l1 13h8l1-13" {...stroke} />}
      {name === 'check' && <Path d="M5 12.5l4.5 4.5L19 7.5" {...stroke} />}
    </Svg>
  );
}
