import { useState, type ReactNode } from 'react';
import { Platform, Pressable, Switch, Text, View } from 'react-native';

import { Icon, type IconName } from '@/components/Icon';
import { Mascot } from '@/components/Mascot';
import { Badge, confirm, notify, PageTitle, Row, Screen } from '@/components/ui';
import { router } from 'expo-router';

import { SEASON_INFO, SEASON_MODE_LABELS, SEASON_MODES } from '@/data/seasons';
import { THEME_IDS, THEMES } from '@/data/themes';
import { deleteAccount, logout, saveProfile } from '@/lib/account';
import { API_BASE_URL, errorAlertMessage } from '@/lib/api';
import { styleLabel } from '@/lib/codes';
import { diffDays, todayKey } from '@/lib/date';
import { MOCK_MODE } from '@/lib/mode';
import { ensureNotifyPermission, notificationsSupported, openNotifySettings, serverPushActive } from '@/lib/planNotify';
import { notifyTimeLabel, stepNotifyTime } from '@/lib/planNotifyRules';
import { personalOf, personalSummary } from '@/lib/personalInfo';
import { activeItems } from '@/lib/retire';
import { regionLabel } from '@/lib/weatherSync';
import { useNotifyStore } from '@/store/useNotifyStore';
import { useStore } from '@/store/useStore';
import { useThemeStore } from '@/store/useThemeStore';
import { useWeatherStore } from '@/store/useWeatherStore';
import { cardBase, makeStyles, useColors } from '@/theme';

// 목업 ⑦ 마이 · 개인 설정 (p.45). 탭 이름은 보드처럼 "더보기"
export default function My() {
  const profile = useStore((s) => s.profile);
  const items = useStore((s) => s.items);
  const looks = useStore((s) => s.looks);
  const updateProfile = useStore((s) => s.updateProfile);
  const loadSample = useStore((s) => s.loadSample);
  const resetAll = useStore((s) => s.resetAll);
  const isAccount = useStore((s) => s.authMode === 'account');
  const user = useStore((s) => s.user);
  const themeId = useThemeStore((s) => s.themeId);
  const seasonMode = useThemeStore((s) => s.seasonMode);
  const seasonPref = useThemeStore((s) => s.seasonPref);
  const season = useThemeStore((s) => s.season);
  const regionCode = useWeatherStore((s) => s.regionCode);
  const notifyTime = useNotifyStore((s) => s.time);
  const notifyPermission = useNotifyStore((s) => s.permission);
  const serverPush = useNotifyStore((s) => s.serverPush);
  const [busy, setBusy] = useState(false);
  const s = useStyles();

  const today = todayKey();
  // 사용 중인 옷 기준 (doc/13)
  const inUse = activeItems(items);
  const active = inUse.filter((i) => i.lastWornAt && diffDays(today, i.lastWornAt) <= 30).length;
  const usage = inUse.length ? Math.round((active / inUse.length) * 100) : 0;
  const seasonSub =
    seasonPref === 'off'
      ? '꺼 둠 · 기본 색으로 보여요'
      : season
        ? `${SEASON_INFO[season].icon} 지금 ${SEASON_INFO[season].label} 색으로 보여요`
        : '계절 이벤트가 열리면 화면 색이 바뀌어요';

  // 예정 전날 알림 (lib/planNotify.ts): 서버가 보내면 서버 시각, 아니면 기기 로컬 알림 시각
  const byServer = !!serverPush && serverPushActive();
  const dMinus1Sub = !notificationsSupported()
    ? Platform.OS === 'web'
      ? '전날 최신 예보로 재확인 · 웹 미리보기에서는 알림이 오지 않아요'
      : '전날 최신 예보로 재확인 · 이 앱(Android Expo Go)에서는 알림이 오지 않아요'
    : byServer
      ? `전날 ${serverPush!.dayBeforeTime}쯤 최신 예보로 알려 드려요`
      : profile.notify.dMinus1 && notifyPermission === 'denied'
        ? '기기에서 알림 권한이 꺼져 있어요'
        : `전날 ${notifyTimeLabel(notifyTime)} · 최신 예보로 재확인`;

  // 바로 바꿔 보여 주고, 계정 모드면 서버에 저장한다. 실패하면 되돌린다
  const toggle = async (key: keyof typeof profile.notify) => {
    const before = profile;
    const next = { ...profile, notify: { ...profile.notify, [key]: !profile.notify[key] } };
    updateProfile(next);
    // 예정 전날 알림을 켜면 알림 권한을 묻는다 (이미 거부했으면 다시 묻지 않고 설정 화면 안내)
    if (key === 'dMinus1' && next.notify.dMinus1 && notificationsSupported()) {
      void ensureNotifyPermission(true).then((p) => {
        if (p === 'denied') {
          confirm(
            '알림 권한이 꺼져 있어요',
            '예정 전날 알림을 받으려면 기기 설정에서 Wearly 알림을 허용해 주세요.',
            openNotifySettings,
            '설정 열기',
          );
        }
      });
    }
    if (!isAccount) return;
    try {
      await saveProfile(next);
    } catch (e) {
      updateProfile(before);
      notify('알림 설정을 저장하지 못했어요', errorAlertMessage(e));
    }
  };

  const run = async (task: () => Promise<void>, failTitle: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await task();
      router.replace('/onboarding');
    } catch (e) {
      notify(failTitle, errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen header={<PageTitle title="더보기" />}>
      {/* 프로필: 마스코트 아바타 + 이름 + 인사 */}
      <View style={s.profile}>
        <View style={s.avatar}>
          <Mascot pose="hello" size={64} />
        </View>
        <View style={{ flex: 1 }}>
          <Row style={{ gap: 8 }}>
            <Text style={s.name}>{profile.name}님</Text>
            <Badge tone="green">{isAccount ? 'FREE' : 'GUEST'}</Badge>
          </Row>
          <Text style={s.greet}>오늘도 스타일리시한 하루 되세요! 🧡</Text>
          <Text style={s.email} numberOfLines={1}>
            {isAccount ? (user?.email ?? '이메일 계정') : '게스트 · 데이터는 이 기기에만 저장돼요'}
          </Text>
        </View>
      </View>

      <View style={s.stats}>
        <Stat value={`${inUse.length}`} unit="벌" label="내 옷" />
        <View style={s.statLine} />
        <Stat value={`${looks.length}`} unit="건" label="코디 기록" />
        <View style={s.statLine} />
        <Stat value={`${usage}`} unit="%" label="활용률 (30일)" />
      </View>

      <MenuCard>
        <MenuRow icon="hanger" label="내 옷장" value={`${inUse.length}벌`} onPress={() => router.navigate('/closet')} />
        <MenuRow icon="clipboard" label="코디 기록" value="캘린더" onPress={() => router.navigate('/calendar')} />
        <MenuRow
          icon="gear"
          label="스타일 추천 설정"
          value={profile.moods.map(styleLabel).join(' · ') || '미설정'}
          onPress={() => router.push('/settings')}
        />
        <MenuRow
          icon="user"
          label="나의 정보"
          value={personalSummary(personalOf(profile)) ?? '미입력'}
          onPress={() => router.push('/personal')}
        />
        <MenuRow icon="chart" label="옷장 스타일 분석" onPress={() => router.push('/analysis')} />
        <MenuRow
          icon="sparkle"
          label="AI 진단 리포트"
          value={looks.length ? `${looks.length}건` : undefined}
          onPress={() => router.push('/analysis')}
        />
        <MenuRow
          icon="camera"
          label="등록 없이 바로 진단"
          right={<Badge tone="green">기기 내 처리</Badge>}
          last
          onPress={() =>
            notify(
              '바로 진단',
              '온디바이스 모델(Core ML · TFLite)로 기기 안에서 진단하는 기능은 출시 버전에서 제공해요.',
            )
          }
        />
      </MenuCard>

      {/* 날씨 지역 (doc/17 5장). 목업 실행은 서버 날씨를 쓰지 않으므로 숨긴다 */}
      {!MOCK_MODE && (
        <>
          <Text style={s.section}>날씨</Text>
          <MenuCard>
            <MenuRow
              icon="pin"
              label="날씨 지역"
              value={regionLabel(regionCode)}
              last
              onPress={() => router.push('/region')}
            />
          </MenuCard>
        </>
      )}

      <Text style={s.section}>알림 설정</Text>
      <MenuCard>
        <ToggleRow
          icon="bell"
          label="아침 코디 푸시"
          sub="매일 아침 7:30"
          value={profile.notify.morning}
          onChange={() => void toggle('morning')}
        />
        <ToggleRow
          icon="clipboard"
          label="저녁 일별 진단"
          sub="기록을 남기면 저녁에 진단 요약"
          value={profile.notify.evening}
          onChange={() => void toggle('evening')}
        />
        <ToggleRow
          icon="calendar"
          label="D-1 예정일 알림"
          sub={dMinus1Sub}
          value={profile.notify.dMinus1}
          onChange={() => void toggle('dMinus1')}
          last={!profile.notify.dMinus1 || !notificationsSupported() || byServer}
        />
        {profile.notify.dMinus1 && notificationsSupported() && !byServer && (
          notifyPermission === 'denied' ? (
            <MenuRow icon="bell" label="알림 권한 허용하기" value="기기 설정" last onPress={openNotifySettings} />
          ) : (
            <TimeStepRow
              label="전날 알림 시각"
              value={notifyTimeLabel(notifyTime)}
              onStep={(dir) => useNotifyStore.getState().setTime(stepNotifyTime(notifyTime, dir))}
            />
          )
        )}
      </MenuCard>

      {/* 계절 테마 (이슈 #6): 서버 설정을 따를지 이 기기에서 끌지만 고른다 */}
      <Text style={s.section}>화면</Text>
      <MenuCard>
        <ToggleRow
          icon="palette"
          label="계절 테마"
          sub={seasonSub}
          value={seasonPref === 'follow'}
          onChange={() => useThemeStore.getState().setSeasonPref(seasonPref === 'follow' ? 'off' : 'follow')}
          last
        />
      </MenuCard>

      {MOCK_MODE && (
        <>
          <Text style={s.section}>테마 바꾸기 (개발용)</Text>
          <MenuCard>
            {THEME_IDS.map((id, i) => (
              <MenuRow
                key={id}
                icon="palette"
                label={THEMES[id].name}
                value={themeId === id ? '사용 중' : undefined}
                right={themeId === id ? <Icon name="check" size={18} /> : undefined}
                last={i === THEME_IDS.length - 1}
                onPress={() => useThemeStore.getState().setThemeId(id)}
              />
            ))}
          </MenuCard>
          <Text style={s.section}>계절 모드 (개발용 · 서버 설정 대신)</Text>
          <MenuCard>
            {SEASON_MODES.map((mode, i) => (
              <MenuRow
                key={mode}
                icon="sparkle"
                label={SEASON_MODE_LABELS[mode]}
                value={seasonMode === mode ? '사용 중' : undefined}
                right={seasonMode === mode ? <Icon name="check" size={18} /> : undefined}
                last={i === SEASON_MODES.length - 1}
                onPress={() => useThemeStore.getState().setSeasonMode(mode)}
              />
            ))}
          </MenuCard>
        </>
      )}

      {/* 친구 초대 (doc/25)·옷장 도우미 (doc/26). 코드·연결은 서버 계정에 있으므로 계정 사용자만 */}
      {isAccount && (
        <>
          <Text style={s.section}>친구 초대</Text>
          <MenuCard>
            <MenuRow icon="share" label="친구 초대" value="초대 코드 보내기" onPress={() => router.push('/invite')} />
            <MenuRow
              icon="user"
              label="옷장 도우미"
              value="옷을 대신 등록·관리"
              last
              onPress={() => router.push('/helper')}
            />
          </MenuCard>
        </>
      )}

      <Text style={s.section}>계정</Text>
      <MenuCard>
        {isAccount ? (
          <>
            <MenuRow
              icon="logout"
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
              icon="user"
              label="회원 탈퇴"
              danger
              last
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
            icon="user"
            label="계정 만들기 · 로그인"
            value="이메일"
            last
            onPress={() => router.push({ pathname: '/auth', params: { mode: 'signup' } })}
          />
        )}
      </MenuCard>

      {!isAccount && (
        <>
          <Text style={s.section}>데이터</Text>
          <MenuCard>
            <MenuRow
              icon="refresh"
              label="샘플 옷장 다시 불러오기"
              onPress={() =>
                confirm('샘플 옷장을 불러올까요?', '지금 옷장과 기록이 샘플 데이터로 바뀌어요.', loadSample, '불러오기')
              }
            />
            <MenuRow
              icon="trash"
              label="처음부터 다시 시작"
              danger
              last
              onPress={() =>
                confirm(
                  '모든 데이터를 지울까요?',
                  '옷장·코디·기록이 모두 삭제되고 첫 화면으로 돌아가요.',
                  () => {
                    resetAll();
                    router.replace('/onboarding');
                  },
                  '삭제',
                )
              }
            />
          </MenuCard>
        </>
      )}

      <View style={s.bottomArt}>
        <Mascot pose="cheer" size={150} />
      </View>
      <Text style={s.foot}>
        {isAccount
          ? 'Wearly v0.1 · 옷장·취향은 계정에, 코디·기록·사진은 이 기기에 저장돼요'
          : 'Wearly v0.1 · 데이터는 이 기기에만 저장돼요'}
        {'\n'}
        {MOCK_MODE ? '목업 실행' : '서버 실행'} · API {API_BASE_URL}
      </Text>
    </Screen>
  );
}

const Stat = ({ value, unit, label }: { value: string; unit: string; label: string }) => {
  const s = useStyles();

  return (
    <View style={s.stat}>
      <Text style={s.statValue}>
        {value}
        <Text style={s.statUnit}>{unit}</Text>
      </Text>
      <Text style={s.statLabel}>{label}</Text>
    </View>
  );
};

const MenuCard = ({ children }: { children: ReactNode }) => {
  const s = useStyles();

  return <View style={s.menu}>{children}</View>;
};

interface IMenuRowProps {
  icon: IconName;
  label: string;
  value?: string;
  right?: ReactNode;
  onPress: () => void;
  danger?: boolean;
  /** 카드의 마지막 줄 (아래 구분선 없음) */
  last?: boolean;
}

const MenuRow = ({ icon, label, value, right, onPress, danger, last }: IMenuRowProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <Pressable style={[s.row, last && s.rowLast]} onPress={onPress} accessibilityRole="button">
      <Icon name={icon} size={21} color={danger ? c.danger : c.text} />
      <Text style={[s.rowLabel, danger && { color: c.danger }]}>{label}</Text>
      <Row style={{ gap: 6, flexShrink: 1 }}>
        {value && (
          <Text style={s.rowValue} numberOfLines={1}>
            {value}
          </Text>
        )}
        {right ?? <Icon name="chevron" size={16} color={c.textMuted} />}
      </Row>
    </Pressable>
  );
};

interface IToggleRowProps {
  icon: IconName;
  label: string;
  sub: string;
  value: boolean;
  onChange: () => void;
  last?: boolean;
}

const ToggleRow = ({ icon, label, sub, value, onChange, last }: IToggleRowProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View style={[s.row, last && s.rowLast]}>
      <Icon name={icon} size={21} />
      <View style={{ flex: 1 }}>
        <Text style={[s.rowLabel, { flex: 0 }]}>{label}</Text>
        <Text style={s.rowSub}>{sub}</Text>
      </View>
      <Switch value={value} onValueChange={onChange} trackColor={{ true: c.primary }} />
    </View>
  );
};

interface ITimeStepRowProps {
  label: string;
  value: string;
  onStep: (dir: 1 | -1) => void;
}

/** 30분 단위 시각 고르기 (− 값 +) */
const TimeStepRow = ({ label, value, onStep }: ITimeStepRowProps) => {
  const s = useStyles();
  const c = useColors();

  return (
    <View style={[s.row, s.rowLast]}>
      <Icon name="bell" size={21} />
      <Text style={s.rowLabel}>{label}</Text>
      <Row style={{ gap: 4 }}>
        <Pressable style={s.stepBtn} onPress={() => onStep(-1)} hitSlop={6} accessibilityLabel="30분 앞으로">
          <Text style={[s.stepText, { color: c.text }]}>−</Text>
        </Pressable>
        <Text style={s.stepValue}>{value}</Text>
        <Pressable style={s.stepBtn} onPress={() => onStep(1)} hitSlop={6} accessibilityLabel="30분 뒤로">
          <Text style={[s.stepText, { color: c.text }]}>+</Text>
        </Pressable>
      </Row>
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  profile: { ...cardBase(t), flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16 },
  avatar: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: t.colors.accentSoft,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  name: { fontSize: 18, fontWeight: '800', color: t.colors.text },
  greet: { fontSize: 13, color: t.colors.textSub, marginTop: 5 },
  email: { fontSize: 11, color: t.colors.textMuted, marginTop: 3 },
  stats: { ...cardBase(t), flexDirection: 'row', alignItems: 'center', marginTop: 12, paddingVertical: 14 },
  stat: { flex: 1, alignItems: 'center' },
  statLine: { width: 1, height: 30, backgroundColor: t.colors.border },
  statValue: { fontSize: 22, fontWeight: '800', color: t.colors.primary },
  statUnit: { fontSize: 13, fontWeight: '700' },
  statLabel: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  section: { fontSize: 14, color: t.colors.textSub, fontWeight: '700', marginTop: 24, marginLeft: 4 },
  menu: { ...cardBase(t), paddingHorizontal: 16, marginTop: 12 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 15,
    borderBottomWidth: 1,
    borderBottomColor: t.colors.border,
  },
  rowLast: { borderBottomWidth: 0 },
  rowLabel: { flex: 1, fontSize: 15, color: t.colors.text, fontWeight: '600' },
  rowSub: { fontSize: 12, color: t.colors.textSub, marginTop: 2 },
  rowValue: { fontSize: 13, color: t.colors.textMuted, maxWidth: 150 },
  stepBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: t.colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { fontSize: 17, fontWeight: '700', lineHeight: 20 },
  stepValue: { fontSize: 14, fontWeight: '700', color: t.colors.text, minWidth: 74, textAlign: 'center' },
  bottomArt: { alignItems: 'flex-end', marginTop: 18, marginRight: 6 },
  foot: { color: t.colors.textMuted, fontSize: 12, textAlign: 'center', marginTop: 8, lineHeight: 18 },
}));
