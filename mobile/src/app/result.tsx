import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { condIcon, Icon, type IconName } from '@/components/Icon';
import { ItemThumb } from '@/components/ItemThumb';
import { OutfitBoard, OutfitItemRows, ReasonBox } from '@/components/OutfitView';
import { Badge, Button, Header, notify, Row, Screen, SectionHead, Title } from '@/components/ui';
import { router } from 'expo-router';

import { FORMALITY_LABEL, MOOD_LABEL, OCCASION_LABEL, SLOTS } from '@/data/constants';
import type { Outfit, ScoreBreakdown, WardrobeItem } from '@/data/types';
import { providerLabel, type RecommendSource } from '@/lib/aiOutfits';
import { errorAlertMessage } from '@/lib/api';
import { styleLabel } from '@/lib/codes';
import { newId } from '@/lib/context';
import { longLabel, todayKey } from '@/lib/date';
import { savePlan } from '@/lib/planSync';
import { WEIGHTS } from '@/lib/recommend';
import { engineInput, useStore } from '@/store/useStore';
import { cardBase, makeStyles, useColors } from '@/theme';

const BREAKDOWN_LABEL: Record<keyof ScoreBreakdown, string> = {
  color: '색상 조화',
  occasion: '상황 적합',
  weather: '날씨 적합',
  taste: '개인 취향',
  diversity: '다양성',
};

// 목업 ④ 추천 결과 3안 (p.42). 모양은 디자인 보드의 "AI 추천 코디" 화면
export default function Result() {
  const draft = useStore((s) => s.draft);
  const allItems = useStore((s) => s.items);
  const saveDayOutfit = useStore((s) => s.saveDayOutfit);
  const [outfits, setOutfits] = useState<Outfit[]>(draft?.outfits ?? []);
  const [index, setIndex] = useState(0);
  const s = useStyles();
  const c = useColors();

  const current = outfits[index];
  const ctx = current?.context;
  const input = useMemo(() => (ctx ? engineInput(ctx) : null), [ctx]);

  if (!draft || !current || !ctx || !input) {
    return (
      <Screen header={<Header title="추천 결과" />}>
        <Text style={s.emptyText}>추천 결과가 없어요. AI 코디에서 다시 시도해 주세요.</Text>
      </Screen>
    );
  }

  const isPlan = draft.mode === 'PLAN' && !!draft.plan;
  const chips = isPlan
    ? [OCCASION_LABEL[ctx.occasion], ...draft.plan!.concepts.map(styleLabel)]
    : [OCCASION_LABEL[ctx.occasion], MOOD_LABEL[ctx.mood], FORMALITY_LABEL[ctx.formality]];
  const byId = new Map(allItems.map((i) => [i.id, i]));
  const pieces = SLOTS.map((k) => (current.items[k] ? byId.get(current.items[k]!) : undefined)).filter(
    (i): i is WardrobeItem => !!i,
  );
  const go = (d: number) => setIndex((i) => (i + d + outfits.length) % outfits.length);

  const save = () => {
    if (isPlan) {
      // 기기에 먼저 저장하고 서버로 보낸다 (계정 모드). 오프라인 등은 다음 동기화 때 다시 보낸다
      void savePlan({ ...draft.plan!, id: draft.planId ?? newId('plan'), outfit: current }).then((out) => {
        if (out.kind === 'limit' || out.kind === 'invalid') {
          notify('예정을 서버에 저장하지 못했어요', `${errorAlertMessage(out.error)}\n이 예정은 이 기기에만 있어요.`);
        }
      });
      router.dismissTo('/calendar');
    } else {
      saveDayOutfit(todayKey(), current);
      router.dismissTo('/home');
    }
  };

  return (
    <Screen header={<Header />}>
      <Title style={s.title}>AI 추천 코디</Title>
      <Text style={s.sub}>
        {isPlan ? `${longLabel(draft.plan!.date)} · ` : ''}내 옷장 속 아이템으로 완성한 {outfits.length}가지
        스타일이에요.
      </Text>

      <Row style={s.chipRow}>
        <View style={s.ctxChip}>
          <Text style={s.ctxChipText}>{chips[0]}</Text>
        </View>
        {ctx.weather && (
          <View style={s.weatherChip}>
            <Icon name={condIcon(ctx.weather.cond)} size={14} color={c.accent} filled />
            <Text style={s.weatherChipText}>{ctx.weather.temp}°</Text>
          </View>
        )}
        <Text style={s.chipMore} numberOfLines={1}>
          {chips.slice(1).join(' · ')}
        </Text>
        {outfits.length > 1 && (
          <Pressable onPress={() => go(1)} hitSlop={8} style={s.otherLink}>
            <Text style={s.otherText}>다른 코디 보기</Text>
            <Icon name="chevron" size={13} color={c.textSub} />
          </Pressable>
        )}
      </Row>

      {/* 큰 코디 카드: 좌우로 넘기며 3안을 본다 */}
      <View style={s.bigCard}>
        <OutfitBoard outfit={current} inset={14} />
        <View style={s.scoreBadge}>
          <Text style={s.scoreText}>{current.score}점</Text>
        </View>
        {outfits.length > 1 && (
          <>
            <Pressable onPress={() => go(-1)} style={[s.arrow, { left: 6 }]} accessibilityLabel="이전 코디">
              <Icon name="back" size={16} color={c.text} />
            </Pressable>
            <Pressable onPress={() => go(1)} style={[s.arrow, { right: 6 }]} accessibilityLabel="다음 코디">
              <Icon name="chevron" size={16} color={c.text} />
            </Pressable>
          </>
        )}
      </View>
      <View style={s.pager}>
        {outfits.map((o, i) => (
          <Pressable key={o.id} onPress={() => setIndex(i)} hitSlop={6} accessibilityLabel={`코디 ${i + 1}`}>
            <View style={[s.pageDot, i === index && s.pageDotOn]} />
          </Pressable>
        ))}
      </View>

      <Text style={s.outfitTitle}>
        {isPlan ? '예정일 추천 코디' : '오늘의 추천 코디'} {index + 1}
      </Text>
      <Text style={s.outfitDesc}>{current.ai ? current.ai.reason : current.reasons.slice(0, 2).join('. ')}</Text>
      <SourceLine source={draft.source} />

      <View style={s.thumbs}>
        {pieces.map((it) => (
          <ItemThumb key={it.id} item={it} size={58} radius={14} />
        ))}
      </View>

      <Button
        title={isPlan ? '이 코디로 캘린더에 저장 ✨' : '이 코디로 입어볼래요 ✨'}
        onPress={save}
        style={{ marginTop: 18 }}
      />
      <View style={s.actions}>
        <Action
          icon="share"
          label="공유하기"
          onPress={() => notify('공유하기', '코디 공유는 출시 버전에서 지원해요.')}
        />
        <Action icon="heart" label="저장하기" onPress={save} />
        <Action icon="refresh" label="다른 코디" onPress={() => go(1)} disabled={outfits.length < 2} />
      </View>

      <ReasonBox reasons={current.reasons} title={current.ai ? '점수 근거' : '추천 이유'} />

      {/* 점수 근거: 기획서 p.24 초기 점수 모델 */}
      <SectionHead title="점수 근거" />
      <View style={s.breakdown}>
        {(Object.keys(BREAKDOWN_LABEL) as (keyof ScoreBreakdown)[]).map((k) => {
          const v = current.breakdown[k];
          return (
            <Row key={k} style={{ gap: 10 }}>
              <Text style={s.bLabel}>{BREAKDOWN_LABEL[k]}</Text>
              <Text style={s.bWeight}>{Math.round(WEIGHTS[k] * 100)}%</Text>
              <View style={s.bTrack}>
                {v !== null && <View style={[s.bFill, { width: `${Math.round(v * 100)}%` }]} />}
              </View>
              <Text style={s.bValue}>{v === null ? '제외' : Math.round(v * 100)}</Text>
            </Row>
          );
        })}
      </View>

      <SectionHead title="아이템 바꾸기" />
      <OutfitItemRows
        outfit={current}
        input={input}
        onChange={(next) => setOutfits((list) => list.map((o, i) => (i === index ? next : o)))}
      />
    </Screen>
  );
}

const Action = ({
  icon,
  label,
  onPress,
  disabled,
}: {
  icon: IconName;
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) => {
  const s = useStyles();
  const c = useColors();

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={[s.action, disabled && { opacity: 0.4 }]}
      accessibilityRole="button"
    >
      <Icon name={icon} size={22} color={c.textSub} />
      <Text style={s.actionText}>{label}</Text>
    </Pressable>
  );
};

/** 추천 방식 배지: "AI 추천 · 공급자" 또는 "규칙 추천" + 남은 횟수 (doc/09 5장) */
const SourceLine = ({ source }: { source?: RecommendSource }) => {
  const s = useStyles();

  if (source?.method === 'AI') {
    return (
      <View style={{ marginTop: 10, gap: 4 }}>
        <Row style={{ gap: 8, flexWrap: 'wrap' }}>
          <Badge tone="green">{`AI 추천 · ${providerLabel(source.provider)}`}</Badge>
          {source.model ? <Text style={s.sourceNote}>{source.model}</Text> : null}
        </Row>
        <Text style={s.remaining}>
          AI가 고른 후보를 웨어리 점수 기준으로 다시 매겼어요 · 오늘 남은 AI 코디 추천 {source.remainingToday}회
        </Text>
      </View>
    );
  }
  return (
    <View style={{ marginTop: 10, gap: 4 }}>
      <Row>
        <Badge>규칙 추천</Badge>
      </Row>
      {source?.note ? <Text style={s.sourceNote}>{source.note}</Text> : null}
    </View>
  );
};

const useStyles = makeStyles((t) => ({
  emptyText: { color: t.colors.textSub, marginTop: 40, textAlign: 'center' },
  title: { fontSize: 26, marginTop: 4 },
  sub: { fontSize: 14, color: t.colors.textSub, marginTop: 6 },
  chipRow: { gap: 8, marginTop: 14 },
  ctxChip: {
    backgroundColor: t.colors.primary,
    borderRadius: t.radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  ctxChipText: { color: t.colors.onPrimary, fontSize: 13, fontWeight: '700' },
  weatherChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: t.colors.surface,
    borderWidth: 1,
    borderColor: t.colors.chipBorder,
    borderRadius: t.radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  weatherChipText: { fontSize: 13, fontWeight: '700', color: t.colors.text },
  chipMore: { flex: 1, fontSize: 12, color: t.colors.textMuted },
  otherLink: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  otherText: { fontSize: 13, color: t.colors.textSub, fontWeight: '600' },
  bigCard: {
    ...cardBase(t),
    borderRadius: t.radius.xl,
    padding: 14,
    marginTop: 14,
  },
  scoreBadge: {
    position: 'absolute',
    top: 22,
    right: 22,
    backgroundColor: t.colors.surface,
    borderRadius: t.radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  scoreText: { color: t.colors.primary, fontWeight: '800', fontSize: 13 },
  arrow: {
    position: 'absolute',
    top: '50%',
    marginTop: -16,
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: t.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: t.colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  pager: { flexDirection: 'row', justifyContent: 'center', gap: 6, marginTop: 12 },
  pageDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: t.colors.border },
  pageDotOn: { width: 18, backgroundColor: t.colors.primary },
  outfitTitle: { fontSize: 18, fontWeight: '800', color: t.colors.text, marginTop: 14 },
  outfitDesc: { fontSize: 14, color: t.colors.textSub, marginTop: 6, lineHeight: 21 },
  sourceNote: { fontSize: 12, color: t.colors.textSub },
  remaining: { fontSize: 12, color: t.colors.primary, fontWeight: '700', lineHeight: 18 },
  thumbs: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 14 },
  actions: { flexDirection: 'row', marginTop: 10, borderTopWidth: 1, borderTopColor: t.colors.border, paddingTop: 10 },
  action: { flex: 1, alignItems: 'center', gap: 4, paddingVertical: 6 },
  actionText: { fontSize: 12, color: t.colors.textSub },
  breakdown: { ...cardBase(t), padding: 14, gap: 9 },
  bLabel: { width: 64, fontSize: 12, color: t.colors.text, fontWeight: '600' },
  bWeight: { width: 30, fontSize: 11, color: t.colors.textMuted },
  bTrack: { flex: 1, height: 6, backgroundColor: t.colors.surfaceAlt, borderRadius: 3, overflow: 'hidden' },
  bFill: { height: 6, backgroundColor: t.colors.primary, borderRadius: 3 },
  bValue: { width: 30, textAlign: 'right', fontSize: 12, fontWeight: '700', color: t.colors.text },
}));
