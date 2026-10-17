import * as Location from 'expo-location';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { Button, Header, notify, Screen } from '@/components/ui';
import { useCodesVersion } from '@/store/useCodes';
import { changeRegion, nearestRegion, regionList } from '@/lib/weatherSync';
import { useWeatherStore } from '@/store/useWeatherStore';
import { cardBase, makeStyles, useColors } from '@/theme';

// 날씨 지역 고르기 (doc/17 5장). 지역 목록은 코드 그룹 REGION(관리자 코드 관리)에서 온다.
// "가까운 지역 찾기"는 위치 권한을 받아 기기 안에서 가장 가까운 지역을 계산한다 (위치는 서버로 보내지 않는다).
export default function Region() {
  const regionCode = useWeatherStore((s) => s.regionCode);
  const [locating, setLocating] = useState(false);
  const s = useStyles();
  const c = useColors();
  // 코드가 갱신되면 지역 목록도 다시 그린다
  useCodesVersion();

  const regions = regionList();

  const pick = (code: string) => {
    void changeRegion(code, true);
    router.back();
  };

  const locate = async () => {
    if (locating) return;
    setLocating(true);
    try {
      const perm = await Location.requestForegroundPermissionsAsync();
      if (!perm.granted) {
        notify('위치 권한이 없어요', '목록에서 지역을 직접 골라 주세요. 설정에서 위치 권한을 켜면 자동으로 찾을 수 있어요.');
        return;
      }
      const pos =
        (await Location.getLastKnownPositionAsync()) ??
        (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Low }));
      const near = nearestRegion(pos.coords.latitude, pos.coords.longitude, regions);
      if (!near) {
        notify('지역을 찾지 못했어요', '목록에서 직접 골라 주세요.');
        return;
      }
      void changeRegion(near.code, true);
      notify('가까운 지역', `${near.label}(으)로 바꿨어요.`);
      router.back();
    } catch (e) {
      notify('위치를 확인하지 못했어요', (e as Error)?.message ?? '목록에서 직접 골라 주세요.');
    } finally {
      setLocating(false);
    }
  };

  return (
    <Screen header={<Header title="날씨 지역" />}>
      <Text style={s.desc}>추천에 쓰는 날씨를 받을 지역이에요. 서버가 지역마다 주기적으로 받아 둔 예보를 써요.</Text>
      <Button
        title={locating ? '' : '가까운 지역 찾기'}
        icon={locating ? <ActivityIndicator color="#fff" /> : <Icon name="pin" size={18} color="#fff" />}
        disabled={locating || regions.length === 0}
        onPress={() => void locate()}
      />
      {regions.length === 0 ? (
        <Text style={s.empty}>지역 목록을 아직 받지 못했어요. 서버에 연결되면 다시 열어 주세요.</Text>
      ) : (
        <View style={s.card}>
          {regions.map((r, i) => {
            const on = r.code === regionCode;
            return (
              <Pressable
                key={r.code}
                onPress={() => pick(r.code)}
                style={[s.row, i === regions.length - 1 && { borderBottomWidth: 0 }]}
                accessibilityRole="button"
                accessibilityState={{ selected: on }}
              >
                <Text style={[s.label, on && { color: c.primary }]}>{r.label}</Text>
                {on ? <Icon name="check" size={18} color={c.primary} /> : null}
              </Pressable>
            );
          })}
        </View>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  desc: { fontSize: 13, color: t.colors.textSub, lineHeight: 19, marginTop: 8, marginBottom: 14 },
  empty: { fontSize: 13, color: t.colors.textMuted, marginTop: 20, textAlign: 'center' },
  card: { ...cardBase(t), marginTop: 16, paddingVertical: 4 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  label: { fontSize: 15, fontWeight: '600', color: t.colors.text },
}));
