import { useState } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { Image } from 'expo-image';

import type { WardrobeItem } from '@/data/types';
import { serverImageSource, type TImageVariant } from '@/lib/photoRules';
import { requestImageRefresh } from '@/lib/photoSync';
import { photoSource } from '@/lib/samplePhotos';
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
  /** 서버 사진 크기: 목록은 썸네일(기본), 상세는 원본 */
  variant?: TImageVariant;
}

/**
 * 옷 사진 칸. 서버 사진(item.image) → 기기 사진(item.imageUri: 게스트·샘플·아직 못 올린 사진) → 일러스트 순으로 그린다.
 * 서버 사진은 imageId 기반 cacheKey로 디스크에 캐시해 서명 주소가 바뀌어도 다시 내려받지 않는다.
 * 서버 사진을 못 그리면(주소 만료 등) 옷장을 다시 받아 새 주소로 그리고, 그동안은 기기 사진·일러스트를 보여 준다.
 */
export const ItemThumb = ({ item, size, tag, style, height, dim, heart, radius, variant = 'thumb' }: IItemThumbProps) => {
  /** 그리지 못한 서버 주소. 주소가 바뀌면(다시 받음) 다시 시도한다 */
  const [failedServer, setFailedServer] = useState<string | null>(null);
  /** 그리지 못한 기기 사진 주소 */
  const [failedLocal, setFailedLocal] = useState<string | null>(null);
  const s = useStyles();
  const c = useColors();

  const h = height ?? size;
  const art = Math.min(size, h) * 0.72;
  const r = radius ?? (size < 70 ? 12 : 16);
  const server = serverImageSource(item?.image, variant);
  const local = photoSource(item?.imageUri);
  const useServer = !!server && server.uri !== failedServer;
  const photo = useServer ? server : local && item?.imageUri !== failedLocal ? local : undefined;

  const onError = () => {
    if (useServer && server) {
      setFailedServer(server.uri);
      if (item) requestImageRefresh(item.id);
    } else {
      setFailedLocal(item?.imageUri ?? null);
    }
  };

  return (
    <View style={[s.tile, { width: size, height: h, borderRadius: r }, style]}>
      {photo ? (
        <Image
          source={photo}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          cachePolicy="memory-disk"
          recyclingKey={item?.id}
          onError={onError}
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
