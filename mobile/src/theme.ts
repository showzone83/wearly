// 화면 스타일 도우미 (doc/12_디자인_테마.md).
// 색·모서리 값은 data/themes.ts, 지금 쓰는 테마는 useThemeStore가 정한다.
// 스타일은 모듈 로드 때 고정하지 않고 makeStyles로 테마마다 만든다. 테마가 바뀌면 그 테마의 스타일로 다시 그린다.
import { StyleSheet, type ImageStyle, type TextStyle, type ViewStyle } from 'react-native';

import type { ITheme, IThemeColors } from '@/data/themes';
import { useTheme } from '@/store/useThemeStore';

export const space = {
  gutter: 20,
};

/** 이름별 스타일 묶음 (RN 0.88 엄격 타입에서 없어진 StyleSheet.NamedStyles와 같은 모양) */
type TNamedStyles<T> = { [P in keyof T]: ViewStyle | TextStyle | ImageStyle };

/**
 * 테마별 StyleSheet를 만드는 훅을 돌려준다. 같은 테마의 스타일은 한 번만 만든다(WeakMap 캐시).
 *
 * ```ts
 * const useStyles = makeStyles((t) => ({ title: { color: t.colors.text } }));
 * const Comp = () => { const s = useStyles(); ... };
 * ```
 */
export const makeStyles = <T extends TNamedStyles<T>>(factory: (t: ITheme) => T) => {
  const cache = new WeakMap<ITheme, T>();
  const build = (t: ITheme): T => {
    const hit = cache.get(t);
    if (hit) return hit;
    const made = StyleSheet.create(factory(t));
    cache.set(t, made);
    return made;
  };
  return (): T => build(useTheme());
};

/** 지금 테마의 색 */
export const useColors = (): IThemeColors => useTheme().colors;

/** 보드의 둥근 카드 그림자 (아주 약하게) */
export const softShadow = (t: ITheme, strength = 1): ViewStyle => ({
  shadowColor: t.colors.shadow,
  shadowOpacity: 0.07 * strength,
  shadowRadius: 12,
  shadowOffset: { width: 0, height: 4 },
  elevation: 2 * strength,
});

/** 카드 공통 모양: 표면색 + 둥근 모서리 + 연한 테두리 + 약한 그림자 */
export const cardBase = (t: ITheme): ViewStyle => ({
  backgroundColor: t.colors.surface,
  borderRadius: t.radius.lg,
  borderWidth: 1,
  borderColor: t.colors.border,
  ...softShadow(t),
});

/** 입력칸 공통 모양 */
export const inputBase = (t: ITheme) => ({
  height: 50,
  backgroundColor: t.colors.surface,
  borderWidth: 1,
  borderColor: t.colors.border,
  borderRadius: t.radius.md,
  paddingHorizontal: 16,
  fontSize: 15,
  color: t.colors.text,
});
