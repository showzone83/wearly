import { router } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { Badge, confirm, notify, Row, Screen, Serif } from '@/components/ui';
import { STYLE_LABEL } from '@/data/constants';
import { deleteAccount, logout, saveProfile } from '@/lib/account';
import { API_BASE_URL, errorMessage } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { diffDays, todayKey } from '@/lib/date';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

// 목업 ⑦ 마이 · 개인 설정 (p.45)
export default function My() {
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const looks = useStore((s) => s.looks);
  const updateProfile = useStore((s) => s.updateProfile);
  const loadSample = useStore((s) => s.loadSample);
  const resetAll = useStore((s) => s.resetAll);
  const isAccount = useStore((s) => s.authMode === 'account');
  const user = useStore((s) => s.user);
  const [busy, setBusy] = useState(false);

  const today = todayKey();
  const active = items.filter((i) => i.lastWornAt && diffDays(today, i.lastWornAt) <= 30).length;
  const usage = items.length ? Math.round((active / items.length) * 100) : 0;

  // 바로 바꿔 보여 주고, 계정 모드면 서버에 저장한다. 실패하면 되돌린다
  const toggle = async (key: keyof typeof profile.notify) => {
    const before = profile;
    const next = { ...profile, notify: { ...profile.notify, [key]: !profile.notify[key] } };
    updateProfile(next);
    if (!isAccount) return;
    try {
      await saveProfile(next);
    } catch (e) {
      updateProfile(before);
      notify('알림 설정을 저장하지 못했어요', errorMessage(e));
    }
  };

  const run = async (task: () => Promise<void>, failTitle: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await task();
      router.replace('/onboarding');
    } catch (e) {
      notify(failTitle, errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen
      header={
        <View style={s.top}>
          <Text style={s.title}>마이</Text>
        </View>
      }
    >
      <View style={s.profile}>
        <View style={s.avatar}>
          <Serif style={{ color: '#fff', fontSize: 22 }}>W</Serif>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={s.name}>{profile.name}</Text>
          <Text style={s.email}>{isAccount ? (user?.email ?? '이메일 계정') : '게스트 · 데이터는 이 기기에만 저장돼요'}</Text>
        </View>
        <Badge>{isAccount ? 'FREE' : 'GUEST'}</Badge>
      </View>

      <View style={s.stats}>
        <Stat value={`${items.length}`} label="내 옷" />
        <Stat value={`${looks.length}`} label="코디 기록" />
        <Stat value={`${usage}%`} label="활용률 (30일)" />
      </View>

      <Text style={s.section}>스타일</Text>
      <MenuRow label="스타일 · 근무 패턴 설정" value={profile.moods.map((m) => STYLE_LABEL[m]).join(' · ') || '미설정'} onPress={() => router.push('/settings')} />
      <MenuRow label="옷장 스타일 분석" onPress={() => router.push('/analysis')} />
      <MenuRow label="AI 진단 리포트" value={looks.length ? `${looks.length}건` : undefined} onPress={() => router.push('/analysis')} />
      <MenuRow
        label="등록 없이 바로 진단"
        right={<Badge tone="green">기기 내 처리</Badge>}
        onPress={() => notify('바로 진단', '온디바이스 모델(Core ML · TFLite)로 기기 안에서 진단하는 기능은 출시 버전에서 제공해요.')}
      />

      <Text style={s.section}>알림</Text>
      <ToggleRow label="아침 코디 푸시" sub="매일 아침 7:30" value={profile.notify.morning} onChange={() => void toggle('morning')} />
      <ToggleRow label="저녁 일별 진단" sub="기록을 남기면 저녁에 진단 요약" value={profile.notify.evening} onChange={() => void toggle('evening')} />
      <ToggleRow label="D-1 예정일 알림" sub="전날 최신 예보로 재확인" value={profile.notify.dMinus1} onChange={() => void toggle('dMinus1')} />

      <Text style={s.section}>계정</Text>
      {isAccount ? (
        <>
          <MenuRow
            label="로그아웃"
            onPress={() =>
              confirm(
                '로그아웃할까요?',
                '옷장과 취향 설정은 계정에 남아 있어요. 이 기기에만 저장된 코디·착용 기록·옷 사진은 지워져요.',
                () => void run(logout, '로그아웃하지 못했어요'),
                '로그아웃',
              )
            }
          />
          <MenuRow
            label="회원 탈퇴"
            danger
            onPress={() =>
              confirm(
                '정말 탈퇴할까요?',
                '계정과 서버에 저장된 옷장·취향 설정이 모두 삭제되고 되돌릴 수 없어요. 이 기기의 데이터도 함께 지워져요.',
                () => void run(deleteAccount, '탈퇴하지 못했어요'),
                '탈퇴',
              )
            }
          />
        </>
      ) : (
        <MenuRow
          label="계정 만들기 · 로그인"
          value="이메일"
          onPress={() => router.push({ pathname: '/auth', params: { mode: 'signup' } })}
        />
      )}

      {!isAccount && (
        <>
          <Text style={s.section}>데이터</Text>
          <MenuRow
            label="샘플 옷장 다시 불러오기"
            onPress={() => confirm('샘플 옷장을 불러올까요?', '지금 옷장과 기록이 샘플 데이터로 바뀌어요.', loadSample, '불러오기')}
          />
          <MenuRow
            label="처음부터 다시 시작"
            danger
            onPress={() =>
              confirm('모든 데이터를 지울까요?', '옷장·코디·기록이 모두 삭제되고 첫 화면으로 돌아가요.', () => {
                resetAll();
                router.replace('/onboarding');
              }, '삭제')
            }
          />
        </>
      )}
      <Text style={s.foot}>
        {isAccount ? 'Wearly v0.1 · 옷장·취향은 계정에, 코디·기록·사진은 이 기기에 저장돼요' : 'Wearly v0.1 · 데이터는 이 기기에만 저장돼요'}
        {'\n'}
        {MOCK_MODE ? '목업 실행' : '서버 실행'} · API {API_BASE_URL}
      </Text>
    </Screen>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <View style={s.stat}>
      <Serif style={s.statValue}>{value}</Serif>
      <Text style={s.statLabel}>{label}</Text>
    </View>
  );
}

function MenuRow({ label, value, right, onPress, danger }: { label: string; value?: string; right?: ReactNode; onPress: () => void; danger?: boolean }) {
  return (
    <Pressable style={s.row} onPress={onPress}>
      <Text style={[s.rowLabel, danger && { color: colors.danger }]}>{label}</Text>
      <Row style={{ gap: 6 }}>
        {value && <Text style={s.rowValue}>{value}</Text>}
        {right ?? <Icon name="chevron" size={16} color={colors.faint} />}
      </Row>
    </Pressable>
  );
}

function ToggleRow({ label, sub, value, onChange }: { label: string; sub: string; value: boolean; onChange: () => void }) {
  return (
    <View style={s.row}>
      <View>
        <Text style={s.rowLabel}>{label}</Text>
        <Text style={s.rowSub}>{sub}</Text>
      </View>
      <Switch value={value} onValueChange={onChange} trackColor={{ true: colors.green }} />
    </View>
  );
}

const s = StyleSheet.create({
  top: { paddingHorizontal: 20, height: 52, justifyContent: 'center' },
  title: { fontSize: 18, fontWeight: '800', color: colors.ink, letterSpacing: 2 },
  profile: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 16 },
  avatar: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.green, alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 17, fontWeight: '800', color: colors.ink },
  email: { fontSize: 12, color: colors.sub, marginTop: 3 },
  stats: { flexDirection: 'row', gap: 8, marginTop: 10 },
  stat: { flex: 1, backgroundColor: colors.tile, alignItems: 'center', paddingVertical: 16 },
  statValue: { fontSize: 22 },
  statLabel: { fontSize: 12, color: colors.sub, marginTop: 4 },
  section: { fontSize: 13, color: colors.sub, fontWeight: '700', marginTop: 26, marginBottom: 4 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: colors.line },
  rowLabel: { fontSize: 15, color: colors.ink, fontWeight: '500' },
  rowSub: { fontSize: 12, color: colors.sub, marginTop: 2 },
  rowValue: { fontSize: 13, color: colors.beige },
  foot: { color: colors.faint, fontSize: 12, textAlign: 'center', marginTop: 30 },
});
