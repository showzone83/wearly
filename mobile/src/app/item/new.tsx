import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { GarmentArt } from '@/components/GarmentArt';
import { Icon } from '@/components/Icon';
import { defaultName, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { Badge, Button, Header, Kicker, notify, Row, Screen, Serif } from '@/components/ui';
import { COLOR_KEYS, SUBTYPE, SUBTYPES_BY_CATEGORY } from '@/data/constants';
import type { Category, ColorKey, SubType } from '@/data/types';
import { isApiError } from '@/api/errors';
import { createItem, isAccountMode } from '@/lib/account';
import { api, errorMessage, isNetworkError, photoFile } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { canRecommend } from '@/lib/recommend';
import { draftFromSuggestion } from '@/lib/serverItems';
import { useStore } from '@/store/useStore';
import { colors } from '@/theme';

type Step = 1 | 2 | 3 | 4;

const ANALYSIS_STEPS = ['종류 인식', '색상 추출', '패턴 · 계절 판단'];
/** 분석 진행 표시를 끝까지 보여 주는 최소 시간 */
const MIN_ANALYZE_MS = 550 * ANALYSIS_STEPS.length + 300;

/** AI 제안의 출처: 게스트 목업 · 서버 목업 공급자 · Claude · 서버 연결 실패로 기기 목업 대체 */
type AiSource = 'guest' | 'server-mock' | 'claude' | 'fallback';

const AI_BADGE: Record<AiSource, string> = {
  guest: 'AI 제안 · 목업',
  'server-mock': 'AI 제안 · 서버 목업',
  claude: 'AI 제안',
  fallback: 'AI 제안 · 기기 목업',
};

type PickedAsset = { uri: string; fileName?: string | null; mimeType?: string | null };

type Outcome =
  | { kind: 'ai'; source: AiSource; draft: ItemDraft; remaining?: number }
  | { kind: 'limit' }
  | { kind: 'error'; message: string };

function hash(str: string): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h;
}

/**
 * 목업 AI 분석: 실제 모델 대신 사진마다 고정된 제안값을 만든다.
 * 목업 실행(npm run start:mock)의 게스트 모드와, 그때 서버에 닿지 못한 경우에만 쓴다.
 */
function mockAnalyze(uri: string): ItemDraft {
  const h = hash(uri);
  const cats: Category[] = ['TOP', 'TOP', 'BOTTOM', 'OUTER', 'SHOES', 'BAG'];
  const category = cats[h % cats.length];
  const subs = SUBTYPES_BY_CATEGORY[category];
  // h는 32비트 부호 없는 값이라 >>(부호 있는 시프트)를 쓰면 음수 인덱스가 나온다
  const subType: SubType = subs[(h >>> 3) % subs.length];
  const palette: ColorKey[] = COLOR_KEYS.slice(0, 8);
  const color = palette[(h >>> 7) % palette.length];
  return {
    name: defaultName({ color, subType }),
    category,
    subType,
    color,
    pattern: 'SOLID',
    seasons: SUBTYPE[subType].warmth >= 2 ? ['FALL', 'WINTER'] : ['SPRING', 'FALL'],
    styles: ['MINIMAL'],
    formality: 2,
    status: 'OWNED',
    imageUri: uri,
  };
}

/**
 * 서버 AI 분석(POST /wardrobe/items/analyze).
 * 기기 목업(mockAnalyze)은 목업 실행(npm run start:mock)에서만 쓴다. 기본 실행에서 실패하면 목업으로 가리지 않고
 * 원인을 보여 준 뒤 직접 입력으로 넘긴다.
 */
async function runAnalysis(asset: PickedAsset): Promise<Outcome> {
  if (!isAccountMode()) {
    if (MOCK_MODE) return { kind: 'ai', source: 'guest', draft: mockAnalyze(asset.uri) };
    return { kind: 'error', message: '로그인해야 AI 분석을 쓸 수 있어요.' };
  }
  try {
    const res = await api.wardrobe.analyze(await photoFile(asset));
    return {
      kind: 'ai',
      source: res.provider === 'claude' ? 'claude' : 'server-mock',
      draft: draftFromSuggestion(res.suggestion, asset.uri),
      remaining: res.remainingToday,
    };
  } catch (e) {
    const cause = (e as { cause?: unknown }).cause;
    console.warn('[item/new] AI 분석 실패', e, cause, asset);
    if (isApiError(e) && e.code === 'AI_LIMIT_EXCEEDED') return { kind: 'limit' };
    if (isNetworkError(e) && MOCK_MODE) return { kind: 'ai', source: 'fallback', draft: mockAnalyze(asset.uri) };
    const detail = cause instanceof Error ? ` (${cause.message})` : '';
    return { kind: 'error', message: errorMessage(e, 'AI 분석에 실패했어요.') + detail };
  }
}

const MANUAL: ItemDraft = {
  name: '네이비 니트',
  category: 'TOP',
  subType: 'KNIT',
  color: 'navy',
  pattern: 'SOLID',
  seasons: ['FALL', 'WINTER'],
  styles: ['NEAT'],
  formality: 2,
  status: 'OWNED',
};

export default function NewItem() {
  const items = useStore((s) => s.items);
  const [step, setStep] = useState<Step>(1);
  const [draft, setDraft] = useState<ItemDraft | null>(null);
  const [aiSource, setAiSource] = useState<AiSource | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [photoUri, setPhotoUri] = useState<string | undefined>();
  const [phase, setPhase] = useState(0);
  const [saving, setSaving] = useState(false);
  /** 분석 중 '직접 입력'으로 넘어가면 늦게 온 분석 결과를 버린다 */
  const runRef = useRef(0);
  const fromAi = aiSource !== null;

  // 2단계: 분석 진행 표시 (결과가 오면 analyze()가 3단계로 넘긴다)
  useEffect(() => {
    if (step !== 2) return;
    setPhase(0);
    const timers = ANALYSIS_STEPS.map((_, i) => setTimeout(() => setPhase(i + 1), 550 * (i + 1)));
    return () => timers.forEach(clearTimeout);
  }, [step]);

  const manual = (uri?: string) => {
    runRef.current++;
    setAiSource(null);
    setDraft({ ...MANUAL, imageUri: uri });
    setStep(3);
  };

  const analyze = async (asset: PickedAsset) => {
    const run = ++runRef.current;
    setPhotoUri(asset.uri);
    setDraft(null);
    setStep(2);
    const started = Date.now();
    const outcome = await runAnalysis(asset);
    const wait = MIN_ANALYZE_MS - (Date.now() - started);
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
    if (run !== runRef.current) return;

    if (outcome.kind === 'ai') {
      setAiSource(outcome.source);
      if (outcome.remaining !== undefined) setRemaining(outcome.remaining);
      setDraft(outcome.draft);
      setStep(3);
      return;
    }
    if (outcome.kind === 'limit') {
      setRemaining(0);
      notify('오늘 AI 분석을 모두 썼어요', '내일 다시 쓸 수 있어요. 이번 옷은 직접 입력으로 등록해 주세요.');
    } else {
      notify('AI 분석을 하지 못했어요', `${outcome.message}\n직접 입력으로 등록해 주세요.`);
    }
    manual(asset.uri);
  };

  const pick = async (source: 'camera' | 'library') => {
    try {
      if (source === 'camera' && Platform.OS !== 'web') {
        const perm = await ImagePicker.requestCameraPermissionsAsync();
        if (!perm.granted) {
          notify('카메라 권한이 필요해요', '설정에서 카메라 접근을 허용해 주세요.');
          return;
        }
      }
      const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.6, exif: false };
      const res = source === 'camera' ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
      if (res.canceled || !res.assets[0]) return;
      void analyze(res.assets[0]);
    } catch (e) {
      console.warn('[item/new] 사진 선택 실패', e);
      notify('사진을 불러오지 못했어요', '직접 입력으로 등록할 수 있어요.');
    }
  };

  const save = async () => {
    if (!draft || saving) return;
    setSaving(true);
    try {
      await createItem({ ...draft, name: draft.name.trim() || defaultName(draft) });
      setStep(4);
    } catch (e) {
      notify('옷을 저장하지 못했어요', errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const progress = (
    <View style={s.progress}>
      {[1, 2, 3, 4].map((n) => (
        <View key={n} style={[s.bar, { backgroundColor: n <= step ? colors.green : colors.line }]} />
      ))}
    </View>
  );

  if (step === 4) {
    const after = canRecommend(useStore.getState().items);
    return (
      <Screen
        header={<Header title="옷 등록" />}
        footer={
          <View style={{ gap: 10 }}>
            <Button title="계속 등록하기" variant="outline" onPress={() => { setDraft(null); setAiSource(null); setPhotoUri(undefined); setStep(1); }} />
            <Button title={after.ok ? '오늘 코디 보러 가기' : '옷장으로'} onPress={() => router.replace(after.ok ? '/home' : '/closet')} />
          </View>
        }
      >
        {progress}
        <View style={{ alignItems: 'center', paddingTop: 40 }}>
          {draft && <GarmentArt subType={draft.subType} color={draft.color} pattern={draft.pattern} size={140} />}
          <Kicker>STEP 4 / 4</Kicker>
          <Serif style={s.doneTitle}>옷장에 추가했어요</Serif>
          <Text style={s.doneSub}>
            {after.ok
              ? `총 ${items.length}벌 · 이제 이 옷이 들어간 코디도 추천해 드려요.`
              : `첫 코디까지 ${after.missing.join(', ')} 1벌씩 남았어요.`}
          </Text>
        </View>
      </Screen>
    );
  }

  if (step === 3 && draft) {
    return (
      <Screen
        header={<Header title="옷 등록" />}
        footer={
          <Button
            title={saving ? '' : '옷장에 저장'}
            icon={saving ? <ActivityIndicator color="#fff" /> : undefined}
            disabled={saving}
            onPress={save}
          />
        }
      >
        {progress}
        <Kicker>STEP 3 / 4</Kicker>
        <Row style={{ gap: 16, marginTop: 12, marginBottom: 26 }}>
          <View style={s.preview}>
            {draft.imageUri ? (
              <Image source={{ uri: draft.imageUri }} style={StyleSheet.absoluteFill} contentFit="cover" />
            ) : (
              <GarmentArt subType={draft.subType} color={draft.color} pattern={draft.pattern} size={86} />
            )}
          </View>
          <View style={{ flex: 1, gap: 6 }}>
            {aiSource && <Badge tone={aiSource === 'fallback' ? 'beige' : 'green'}>{AI_BADGE[aiSource]}</Badge>}
            <Text style={s.formTitle}>{fromAi ? '분석이 끝났어요' : '직접 입력해 주세요'}</Text>
            <Text style={s.formSub}>
              {aiSource === 'fallback'
                ? '서버에 연결하지 못해 기기 안의 목업 분석으로 대신했어요. 틀린 항목은 바로 수정하세요'
                : fromAi
                  ? '틀린 항목은 바로 수정하세요'
                  : draft.imageUri
                    ? '사진은 그대로 저장돼요'
                    : '사진은 나중에 추가할 수 있어요'}
            </Text>
            {remaining !== null && <Text style={s.remaining}>오늘 남은 AI 분석 {remaining}회</Text>}
          </View>
        </Row>
        <ItemForm value={draft} onChange={setDraft} aiFields={fromAi ? ['category', 'color', 'pattern'] : []} />
      </Screen>
    );
  }

  if (step === 2) {
    return (
      <Screen header={<Header title="옷 등록" />} scroll={false}>
        {progress}
        <View style={s.center}>
          {photoUri && <Image source={{ uri: photoUri }} style={s.analyzing} contentFit="cover" />}
          <ActivityIndicator color={colors.green} style={{ marginTop: 28 }} />
          <Serif style={s.doneTitle}>AI가 분석하고 있어요</Serif>
          <View style={{ gap: 8, marginTop: 16 }}>
            {ANALYSIS_STEPS.map((t, i) => (
              <Row key={t} style={{ gap: 8 }}>
                <Icon name="check" size={16} color={i < phase ? colors.green : colors.line} />
                <Text style={{ color: i < phase ? colors.ink : colors.faint }}>{t}</Text>
              </Row>
            ))}
          </View>
          <Pressable onPress={() => manual(photoUri)} style={{ marginTop: 28 }}>
            <Text style={s.manual}>직접 입력할게요</Text>
          </Pressable>
        </View>
      </Screen>
    );
  }

  return (
    <Screen header={<Header title="옷 등록" />}>
      {progress}
      <Kicker>STEP 1 / 4</Kicker>
      <Serif style={s.title}>사진 한 장이면{'\n'}충분해요</Serif>
      <Text style={s.desc}>AI가 종류·색상·패턴을 제안하고, 사용자는 확인만 하면 돼요. 위치정보(EXIF)는 저장하지 않아요.</Text>
      <View style={{ gap: 12, marginTop: 28 }}>
        <Pressable style={s.option} onPress={() => pick('camera')}>
          <Icon name="camera" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>카메라로 촬영</Text>
            <Text style={s.optSub}>밝은 바닥에 옷을 펼쳐 찍으면 인식이 잘 돼요</Text>
          </View>
        </Pressable>
        <Pressable style={s.option} onPress={() => pick('library')}>
          <Icon name="image" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>앨범에서 선택</Text>
            <Text style={s.optSub}>쇼핑몰 캡처 사진도 괜찮아요</Text>
          </View>
        </Pressable>
        <Pressable style={[s.option, { borderStyle: 'dashed' }]} onPress={() => manual()}>
          <Icon name="plus" size={28} />
          <View style={{ flex: 1 }}>
            <Text style={s.optTitle}>사진 없이 직접 입력</Text>
            <Text style={s.optSub}>분석이 어렵거나 사진이 없을 때</Text>
          </View>
        </Pressable>
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  progress: { flexDirection: 'row', gap: 6, marginBottom: 18 },
  bar: { flex: 1, height: 3 },
  title: { fontSize: 27, lineHeight: 38, marginTop: 10 },
  desc: { color: colors.sub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  option: { flexDirection: 'row', alignItems: 'center', gap: 16, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, padding: 18 },
  optTitle: { fontSize: 16, fontWeight: '800', color: colors.ink },
  optSub: { fontSize: 12, color: colors.sub, marginTop: 3 },
  center: { flex: 1, alignItems: 'center', paddingTop: 30 },
  analyzing: { width: 180, height: 180, backgroundColor: colors.tile },
  doneTitle: { fontSize: 22, marginTop: 14, textAlign: 'center' },
  doneSub: { color: colors.sub, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  manual: { color: colors.green, fontWeight: '700', textDecorationLine: 'underline' },
  preview: { width: 110, height: 110, backgroundColor: colors.tile, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  formTitle: { fontSize: 18, fontWeight: '800', color: colors.ink },
  formSub: { fontSize: 13, color: colors.sub, lineHeight: 18 },
  remaining: { fontSize: 12, color: colors.green, fontWeight: '700' },
});
