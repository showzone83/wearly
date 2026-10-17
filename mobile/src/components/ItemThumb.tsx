import { Image } from 'expo-image';
import { useState } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import type { WardrobeItem } from '@/data/types';
import { colors } from '@/theme';
import { GarmentArt } from './GarmentArt';

interface Props {
  item?: WardrobeItem;
  size: number;
  tag?: string;
  style?: StyleProp<ViewStyle>;
  /** 정사각형이 아닌 높이 */
  height?: number;
  dim?: boolean;
}

export function ItemThumb({ item, size, tag, style, height, dim }: Props) {
  const [broken, setBroken] = useState(false);
  const h = height ?? size;
  const art = Math.min(size, h) * 0.72;
  return (
    <View style={[s.tile, { width: size, height: h }, style]}>
      {item?.imageUri && !broken ? (
        <Image
          source={{ uri: item.imageUri }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          onError={() => setBroken(true)}
        />
      ) : item ? (
        <GarmentArt subType={item.subType} category={item.category} color={item.color} pattern={item.pattern} size={art} />
      ) : (
        <Text style={s.empty}>없음</Text>
      )}
      {dim && <View style={[StyleSheet.absoluteFill, s.dim]} />}
      {tag && (
        <View style={s.tag}>
          <Text style={s.tagText}>{tag}</Text>
        </View>
      )}
    </View>
  );
}

const s = StyleSheet.create({
  tile: { backgroundColor: colors.tile, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  tag: { position: 'absolute', top: 8, left: 8, backgroundColor: colors.ink, paddingHorizontal: 6, paddingVertical: 2 },
  tagText: { color: '#fff', fontSize: 10, fontWeight: '700', letterSpacing: 0.6 },
  empty: { color: colors.faint, fontSize: 12 },
  dim: { backgroundColor: 'rgba(242,243,245,0.55)' },
});
