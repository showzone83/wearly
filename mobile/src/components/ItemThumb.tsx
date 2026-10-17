import { useState } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { Image } from 'expo-image';

import type { WardrobeItem } from '@/data/types';
import { makeStyles, useColors } from '@/theme';
import { GarmentArt } from './GarmentArt';
import { Icon } from './Icon';

interface IItemThumbProps {
  item?: WardrobeItem;
  size: number;
  tag?: string;
  style?: StyleProp<ViewStyle>;
  /** 정사각형이 아닌 높이 */
  height?: number;
  dim?: boolean;
  /** 오른쪽 위 하트 표시 (on = 채움) */
  heart?: 'on' | 'off';
  /** 모서리 반지름. 지정하지 않으면 크기에 맞춰 정한다 */
  radius?: number;
}

export const ItemThumb = ({ item, size, tag, style, height, dim, heart, radius }: IItemThumbProps) => {
  const [broken, setBroken] = useState(false);
  const s = useStyles();
  const c = useColors();

  const h = height ?? size;
  const art = Math.min(size, h) * 0.72;
  const r = radius ?? (size < 70 ? 12 : 16);

  return (
    <View style={[s.tile, { width: size, height: h, borderRadius: r }, style]}>
      {item?.imageUri && !broken ? (
        <Image
          source={{ uri: item.imageUri }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          onError={() => setBroken(true)}
        />
      ) : item ? (
        <GarmentArt
          subType={item.subType}
          category={item.category}
          color={item.color}
          pattern={item.pattern}
          size={art}
        />
      ) : (
        <Text style={s.empty}>없음</Text>
      )}
      {dim && <View style={[StyleSheet.absoluteFill, s.dim]} />}
      {tag && (
        <View style={s.tag}>
          <Text style={s.tagText}>{tag}</Text>
        </View>
      )}
      {heart && (
        <View style={s.heart}>
          <Icon name="heart" size={13} color={c.primary} filled={heart === 'on'} strokeWidth={1.8} />
        </View>
      )}
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  tile: {
    backgroundColor: t.colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  tag: {
    position: 'absolute',
    top: 8,
    left: 8,
    backgroundColor: t.colors.surface,
    borderRadius: t.radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  tagText: { color: t.colors.primary, fontSize: 10, fontWeight: '800', letterSpacing: 0.4 },
  empty: { color: t.colors.textMuted, fontSize: 12 },
  dim: { backgroundColor: t.colors.bg, opacity: 0.55 },
  heart: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: t.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
}));
