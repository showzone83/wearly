import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { GarmentArt } from '@/components/GarmentArt';
import { Icon } from '@/components/Icon';
import { defaultName, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { Mascot } from '@/components/Mascot';
import { Badge, Button, Header, Kicker, notify, Row, Screen, StepDots, Title, TraceLabel } from '@/components/ui';
import type { IItemImage } from '@/data/types';
import { isApiError } from '@/api/errors';
import { createItem, isAccountMode } from '@/lib/account';
import { api, errorAlertMessage, errorMessage, isNetworkError, photoFile } from '@/lib/api';
import { systemTraceId, traceLabelOf } from '@/lib/errorText';
import { activeCodes, defaultStatus, isActiveCode, subTypesOf } from '@/lib/codes';
import { mockAnalyze } from '@/lib/mockAnalyze';
import { MOCK_MODE } from '@/lib/mode';
import { isSmallPhoto, SMALL_PHOTO_TEXT } from '@/lib/photoEditRules';
import { isImagesUnsupported } from '@/lib/photoRules';
import { preparePhoto, uploadPrepared } from '@/lib/photoUpload';
import { canRecommend } from '@/lib/recommend';
import { activeItems } from '@/lib/retire';
import { draftFromSuggestion } from '@/lib/serverItems';
import { photoSource, samplePhotoUri, type TSamplePhoto } from '@/lib/samplePhotos';
import { useStore } from '@/store/useStore';
import { makeStyles, useColors } from '@/theme';

type Step = 1 | 2 | 3 | 4;

/** 1단계 "예시 이미지" 줄: 이렇게 찍으면 된다는 보기. 디자인 보드와 같은 옷 사진(샘플 옷장 사진)을 쓴다 */
const EXAMPLES: TSamplePhoto[] = ['nyang_olive_jacket', 'nyang_stripe_knit', 'nyang_jeans', 'nyang_white_sneakers'];

const ANALYSIS_STEPS = ['종류 인식', '색상 추출', '패턴 · 계절 판단'];
/** 분석 진행 표시를 끝까지 보여 주는 최소 시간 */
const MIN_ANALYZE_MS = 550 * ANALYSIS_STEPS.length + 300;

/**
 * AI 제안의 출처: 게스트 목업 · 서버(공급자 ID) · 서버 연결 실패로 기기 목업 대체.
 * 서버 응답의 provider는 공급자 ID 문자열이다(doc/07 3장). "mock"이면 서버 목업 공급자.
 */
type AiSource = { kind: 'guest' } | { kind: 'fallback' } | { kind: 'server'; provider: string };

/** 알려진 공급자 ID의 표시 이름. 그 밖의 ID는 그대로 보여 준다 */
const PROVIDER_NAME: Record<string, string> = { claude: 'Claude', openai: 'OpenAI', gemini: 'Gemini' };

function aiBadge(src: AiSource): string {
  if (src.kind === 'guest') return 'AI 제안 · 목업';
  if (src.kind === 'fallback') return 'AI 제안 · 기기 목업';
  return src.provider === 'mock' ? 'AI 제안 · 서버 목업' : 'AI 제안';
}

/** 실제 공급자일 때 배지 옆에 작게 붙이는 공급자 이름 */
function providerNote(src: AiSource): string | null {
  if (src.kind !== 'server' || src.provider === 'mock') return null;
  return PROVIDER_NAME[src.provider] ?? src.provider;
}

type PickedAsset = { uri: string; fileName?: string | null; mimeType?: string | null; width?: number; height?: number };

/** 등록할 사진: 기기 주소(미리보기·기기 저장용)와, 서버에 올렸으면 그 참조 */
type TPhoto = { uri: string; image?: IItemImage };

/** 2단계 진행: 사진 올리기(계정 모드) → AI 분석 */
type TStage = 'upload' | 'analyze';

type Outcome =
  | { kind: 'ai'; source: AiSource; draft: ItemDraft; remaining?: number }
  | { kind: 'limit'; photo: TPhoto }
  | { kind: 'error'; message: string; photo: TPhoto; traceId?: string }
  /** 사진을 서버에 올리지 못함 → 2단계에서 오류와 '다시 시도' */
  | { kind: 'uploadError'; message: string; traceId?: string };

/** 원하는 코드가 활성이면 그것, 아니면 첫 활성 코드 */
function pick(options: string[], preferred: string): string {
  return options.includes(preferred) ? preferred : (options[0] ?? preferred);
}

/** 개발 실행에서만: 기기 쪽 원인(연결 실패 등)을 문구 뒤에 붙인다. 배포 화면에는 정해진 문구만 */
const causeDetail = (e: unknown): string => {
  if (!__DEV__) return '';
  const cause = (e as { cause?: unknown }).cause;
  return cause instanceof Error ? ` (${cause.message})` : '';
};

/**
 * 계정 모드: 사진을 서버에 올리고(upload-url → PUT → complete) 그 이미지로 AI 분석(POST /wardrobe/items/analyze { imageId }).
 * 서버에 사진 기능이 없으면(404) 예전 방식(사진 파일 multipart 분석, 사진은 기기에 저장)으로 되돌아간다.
 * 기기 목업(mockAnalyze)은 목업 실행(npm run start:mock)에서만 쓴다. 기본 실행에서 실패하면 목업으로 가리지 않고
 * 원인을 보여 준 뒤 직접 입력으로 넘긴다.
 */
async function runAnalysis(asset: PickedAsset, onStage: (stage: TStage) => void): Promise<Outcome> {
  const original: TPhoto = { uri: asset.uri };
  if (!isAccountMode()) {
    if (MOCK_MODE) return { kind: 'ai', source: { kind: 'guest' }, draft: mockAnalyze(asset.uri) };
    return { kind: 'error', message: '로그인해야 AI 분석을 쓸 수 있어요.', photo: original };
  }

  onStage('upload');
  let photo: TPhoto;
  try {
    // 긴 변 1600 이하 JPEG로 바꾼 파일을 올린다. 예전 서버로 되돌아가도 이 파일로 분석·기기 저장한다
    const prepared = await preparePhoto(asset.uri);
    photo = { uri: prepared.uri };
    try {
      const uploaded = await uploadPrepared(prepared);
      photo = { uri: uploaded.localUri, image: uploaded.image };
    } catch (e) {
      if (!isImagesUnsupported(e)) throw e; // 예전 서버면 사진은 기기에만 둔다
    }
  } catch (e) {
    console.warn('[item/new] 사진 올리기 실패', e, (e as { cause?: unknown }).cause, asset);
    if (isNetworkError(e) && MOCK_MODE) return { kind: 'ai', source: { kind: 'fallback' }, draft: mockAnalyze(asset.uri) };
    return { kind: 'uploadError', message: errorMessage(e, '사진을 올리지 못했어요.') + causeDetail(e), traceId: systemTraceId(e) };
  }

  onStage('analyze');
  try {
    const res = photo.image
      ? await api.wardrobe.analyzeImage({ imageId: photo.image.imageId })
      : await api.wardrobe.analyze(await photoFile({ uri: photo.uri }));
    return {
      kind: 'ai',
      source: { kind: 'server', provider: String(res.provider ?? '') },
      draft: draftFromSuggestion(res.suggestion, photo.uri, photo.image),
      remaining: res.remainingToday,
    };
  } catch (e) {
    console.warn('[item/new] AI 분석 실패', e, (e as { cause?: unknown }).cause, asset);
    if (isApiError(e) && e.code === 'AI_LIMIT_EXCEEDED') return { kind: 'limit', photo };
    if (isNetworkError(e) && MOCK_MODE) {
      return { kind: 'ai', source: { kind: 'fallback' }, draft: { ...mockAnalyze(photo.uri), image: photo.image } };
    }
    return { kind: 'error', message: errorMessage(e, 'AI 분석에 실패했어요.') + causeDetail(e), photo, traceId: systemTraceId(e) };
  }
}

/** 직접 입력의 첫 값: 네이비 니트. 코드가 비활성이면 각 그룹의 첫 활성 값으로 */
function manualDraft(photo?: TPhoto): ItemDraft {
  const category = isActiveCode('CATEGORY', 'TOP') ? 'TOP' : pick(activeCodes('CATEGORY'), 'TOP');
  const subType = pick(subTypesOf(category), 'KNIT');
  const color = pick(activeCodes('COLOR'), 'navy');
  return {
    name: defaultName({ color, subType }),
    category,
    subType,
    color,
    colors: [color],
    pattern: pick(activeCodes('PATTERN'), 'SOLID'),
    seasons: ['FALL', 'WINTER'].filter((x) => isActiveCode('SEASON', x)),
    styles: ['NEAT'].filter((x) => isActiveCode('STYLE', x)),
    formality: 2,
    status: defaultStatus(),
    imageUri: photo?.uri,
    image: photo?.image,
  };
}

export default function NewItem() {
  const items = useStore((s) => s.items);
  const [step, setStep] = useState<Step>(1);
  const [draft, setDraft] = useState<ItemDraft | null>(null);
  const [aiSource, setAiSource] = useState<AiSource | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [photoUri, setPhotoUri] = useState<string | undefined>();
  const [phase, setPhase] = useState(0);
  const [stage, setStage] = useState<TStage>('analyze');
  /** 사진 올리기 실패 문구. 있으면 2단계에 오류와 '다시 시도'를 보여 준다 */
  const [uploadError, setUploadError] = useState<string | null>(null);
  /** 사진 올리기 실패가 시스템 오류면 그 traceId (오류 번호) */
  const [uploadTrace, setUploadTrace] = useState<string | undefined>();
  const [saving, setSaving] = useState(false);
  /** 고른 사진이 작다 (긴 변 600 미만) → 흐리게 보일 수 있다는 안내 (막지는 않는다) */
  const [smallPhoto, setSmallPhoto] = useState(false);
  /** 분석 중 '직접 입력'으로 넘어가면 늦게 온 분석 결과를 버린다 */
  const runRef = useRef(0);
  /** '다시 시도'에 쓸 마지막으로 고른 사진 */
  const assetRef = useRef<PickedAsset | null>(null);
  const s = useStyles();
  const colors = useColors();
  const fromAi = aiSource !== null;

  // 2단계: 분석 진행 표시 (결과가 오면 analyze()가 3단계로 넘긴다). 사진을 올리는 동안은 멈춰 둔다
  useEffect(() => {
    if (step !== 2 || stage !== 'analyze' || uploadError) return;
    setPhase(0);
    const timers = ANALYSIS_STEPS.map((_, i) => setTimeout(() => setPhase(i + 1), 550 * (i + 1)));
    return () => timers.forEach(clearTimeout);
  }, [step, stage, uploadError]);

  const manual = (photo?: TPhoto) => {
    runRef.current++;
    setAiSource(null);
    setUploadError(null);
    setDraft(manualDraft(photo));
    setStep(3);
  };

  const analyze = async (asset: PickedAsset) => {
    const run = ++runRef.current;
    assetRef.current = asset;
    setPhotoUri(asset.uri);
    setSmallPhoto(isSmallPhoto(asset.width, asset.height));
    setDraft(null);
    setUploadError(null);
    setStage('analyze');
    setPhase(0);
    setStep(2);
    const started = Date.now();
    const outcome = await runAnalysis(asset, (next) => {
      if (run === runRef.current) setStage(next);
    });
    if (run !== runRef.current) return;
    if (outcome.kind === 'uploadError') {
      setUploadError(outcome.message);
      setUploadTrace(outcome.traceId);
      return;
    }
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
      const trace = outcome.traceId ? `\n${traceLabelOf(outcome.traceId)}` : '';
      notify('AI 분석을 하지 못했어요', `${outcome.message}\n직접 입력으로 등록해 주세요.${trace}`);
    }
    manual(outcome.photo);
  };

  const retryUpload = () => {
    if (assetRef.current) void analyze(assetRef.current);
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
      // 계정 모드는 올리기 전에 긴 변 1600·JPEG 0.8로 다시 저장하므로 여기서는 압축하지 않는다 (두 번 압축 방지)
      const quality = isAccountMode() ? 1 : 0.6;
      const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality, exif: false };
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
      notify('옷을 저장하지 못했어요', errorAlertMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const header = <Header center={<StepDots total={4} current={step} />} />;

  if (step === 4) {
    const after = canRecommend(useStore.getState().items);
    return (
      <Screen
        header={header}
        footer={
          <View style={{ gap: 10 }}>
            <Button title="계속 등록하기" variant="outline" onPress={() => { setDraft(null); setAiSource(null); setPhotoUri(undefined); setUploadError(null); setSmallPhoto(false); setStep(1); }} />
            <Button title={after.ok ? '오늘 코디 보러 가기' : '옷장으로'} onPress={() => router.replace(after.ok ? '/home' : '/closet')} />
          </View>
        }
      >
                <View style={{ alignItems: 'center', paddingTop: 20 }}>
          <Mascot pose="cheer" size={170} />
          {draft && (
            <View style={s.doneArt}>
              <GarmentArt subType={draft.subType} category={draft.category} color={draft.color} pattern={draft.pattern} size={90} />
            </View>
          )}
          <Kicker>STEP 4 / 4</Kicker>
          <Title style={s.doneTitle}>옷장에 추가했어요</Title>
          <Text style={s.doneSub}>
            {after.ok
              ? `총 ${activeItems(items).length}벌 · 이제 이 옷이 들어간 코디도 추천해 드려요.`
              : `첫 코디까지 ${after.missing.join(', ')} 1벌씩 남았어요. 원피스 한 벌로도 시작할 수 있어요.`}
          </Text>
        </View>
      </Screen>
    );
  }

  if (step === 3 && draft) {
    return (
      <Screen
        header={header}
        footer={
          <Button
            title={saving ? '' : '옷장에 저장'}
            icon={saving ? <ActivityIndicator color={colors.onPrimary} /> : undefined}
            disabled={saving}
            onPress={save}
          />
        }
      >
                <Kicker>STEP 3 / 4</Kicker>
        <Row style={{ gap: 16, marginTop: 12, marginBottom: 26 }}>
          <View style={s.preview}>
            {draft.imageUri ? (
              <Image source={{ uri: draft.imageUri }} style={StyleSheet.absoluteFill} contentFit="cover" />
            ) : (
              <GarmentArt subType={draft.subType} category={draft.category} color={draft.color} pattern={draft.pattern} size={86} />
            )}
          </View>
          <View style={{ flex: 1, gap: 6 }}>
            {aiSource && (
              <Row style={{ gap: 6 }}>
                <Badge tone={aiSource.kind === 'fallback' ? 'beige' : 'green'}>{aiBadge(aiSource)}</Badge>
                {providerNote(aiSource) && <Text style={s.provider}>{providerNote(aiSource)}</Text>}
              </Row>
            )}
            <Text style={s.formTitle}>{fromAi ? '분석이 끝났어요' : '직접 입력해 주세요'}</Text>
            <Text style={s.formSub}>
              {aiSource?.kind === 'fallback'
                ? '서버에 연결하지 못해 기기 안의 목업 분석으로 대신했어요. 틀린 항목은 바로 수정하세요'
                : fromAi
                  ? '틀린 항목은 바로 수정하세요'
                  : draft.imageUri
                    ? '사진은 그대로 저장돼요'
                    : '사진은 나중에 추가할 수 있어요'}
            </Text>
            {remaining !== null && <Text style={s.remaining}>오늘 남은 AI 분석 {remaining}회</Text>}
            {smallPhoto && draft.imageUri && <Text style={s.smallInline}>{SMALL_PHOTO_TEXT}</Text>}
          </View>
        </Row>
        <ItemForm value={draft} onChange={setDraft} aiFields={fromAi ? ['category', 'color', 'pattern'] : []} />
      </Screen>
    );
  }

  if (step === 2) {
    return (
      <Screen header={header} scroll={false}>
                <View style={s.center}>
          {photoUri && <Image source={{ uri: photoUri }} style={s.analyzing} contentFit="cover" />}
          {photoUri && smallPhoto && <Text style={s.small}>{SMALL_PHOTO_TEXT}</Text>}
          {uploadError ? (
            <>
              <Title style={s.doneTitle}>사진을 올리지 못했어요</Title>
              <Text style={s.uploadError}>{uploadError}</Text>
              <TraceLabel traceId={uploadTrace} style={{ textAlign: 'center' }} />
              <Button title="다시 시도" onPress={retryUpload} style={s.retry} />
            </>
          ) : (
            <>
              <ActivityIndicator color={colors.primary} style={{ marginTop: 28 }} />
              <Title style={s.doneTitle}>{stage === 'upload' ? '사진을 올리고 있어요' : 'AI가 분석하고 있어요'}</Title>
              <View style={{ gap: 8, marginTop: 16 }}>
                {ANALYSIS_STEPS.map((t, i) => (
                  <Row key={t} style={{ gap: 8 }}>
                    <Icon name="check" size={16} color={i < phase ? colors.primary : colors.border} />
                    <Text style={{ color: i < phase ? colors.text : colors.textMuted }}>{t}</Text>
                  </Row>
                ))}
              </View>
            </>
          )}
          {/* 올리기 전이거나 실패했으면 사진은 기기에 두고, 서버에 올릴 수 있을 때 옮긴다 */}
          <Pressable onPress={() => manual(photoUri ? { uri: photoUri } : undefined)} style={{ marginTop: 28 }}>
            <Text style={s.manual}>직접 입력할게요</Text>
          </Pressable>
        </View>
      </Screen>
    );
  }

  return (
    <Screen
      header={header}
      footer={
        <View style={{ gap: 4 }}>
          <Button title="사진 선택하기" onPress={() => pick('library')} />
          <Button title="사진 없이 직접 입력" variant="ghost" small onPress={() => manual()} />
        </View>
      }
    >
      <View style={s.stepHead}>
        <View style={{ flex: 1 }}>
          <Kicker>STEP 1 / 4</Kicker>
          <Title style={s.title}>내 옷을 사진으로{'\n'}등록해주세요!</Title>
        </View>
        <Mascot pose="camera" size={112} />
      </View>
      <Text style={s.desc}>사진을 찍으면 AI가 종류와 색상을 알아서 분석해요. 위치정보(EXIF)는 저장하지 않아요.</Text>

      {/* 점선 사진 추가 상자: 카메라(웹은 앨범) */}
      <Pressable
        style={({ pressed }) => [s.drop, pressed && { opacity: 0.8 }]}
        onPress={() => pick('camera')}
        accessibilityRole="button"
        accessibilityLabel="카메라로 촬영"
      >
        <View style={s.dropIcon}>
          <Icon name="camera" size={30} color={colors.onPrimary} strokeWidth={1.8} />
        </View>
        <Text style={s.dropTitle}>사진 추가하기</Text>
        <Text style={s.dropSub}>카메라로 촬영하거나{'\n'}아래 버튼으로 앨범에서 선택해보세요.</Text>
      </Pressable>

      <Text style={s.exampleLabel}>예시 이미지</Text>
      <View style={s.examples}>
        {EXAMPLES.map((key) => (
          <View key={key} style={s.example}>
            <Image source={photoSource(samplePhotoUri(key))} style={StyleSheet.absoluteFill} contentFit="cover" />
          </View>
        ))}
      </View>
      <View style={s.tip}>
        <View style={s.tipBadge}>
          <Text style={s.tipBadgeText}>TIP</Text>
        </View>
        <Text style={s.tipText}>
          밝은 바닥에 옷을 펼쳐 찍으면 인식이 잘 돼요. 쇼핑몰 캡처 사진도 괜찮아요. 옷을 여러 장 등록할수록 더 다양한 코디를
          추천받을 수 있어요!
        </Text>
      </View>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  stepHead: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingTop: 8 },
  title: { fontSize: 27, lineHeight: 37, marginTop: 8 },
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21, marginTop: 10 },
  drop: {
    alignItems: 'center',
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: t.colors.chipBorder,
    borderRadius: t.radius.lg,
    backgroundColor: t.colors.surface,
    paddingVertical: 34,
    marginTop: 22,
  },
  dropIcon: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: t.colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dropTitle: { fontSize: 16, fontWeight: '800', color: t.colors.text, marginTop: 14 },
  dropSub: { fontSize: 13, color: t.colors.textSub, marginTop: 6, textAlign: 'center', lineHeight: 19 },
  exampleLabel: { fontSize: 12, color: t.colors.textMuted, marginTop: 18, marginBottom: 8 },
  examples: { flexDirection: 'row', gap: 8 },
  example: {
    flex: 1,
    aspectRatio: 1,
    borderRadius: t.radius.md,
    backgroundColor: t.colors.surfaceAlt,
    overflow: 'hidden',
  },
  tip: {
    flexDirection: 'row',
    gap: 10,
    backgroundColor: t.colors.primarySoft,
    borderRadius: t.radius.md,
    padding: 14,
    marginTop: 18,
    alignItems: 'flex-start',
  },
  tipBadge: {
    backgroundColor: t.colors.primary,
    borderRadius: 12,
    width: 28,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tipBadgeText: { color: t.colors.onPrimary, fontSize: 10, fontWeight: '800' },
  tipText: { flex: 1, fontSize: 13, color: t.colors.text, lineHeight: 19 },
  center: { flex: 1, alignItems: 'center', paddingTop: 30 },
  analyzing: { width: 180, height: 180, borderRadius: t.radius.lg, backgroundColor: t.colors.surfaceAlt },
  doneArt: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: t.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: -10,
    marginBottom: 14,
  },
  doneTitle: { fontSize: 22, marginTop: 14, textAlign: 'center' },
  doneSub: { color: t.colors.textSub, marginTop: 10, textAlign: 'center', lineHeight: 20 },
  manual: { color: t.colors.primary, fontWeight: '700', textDecorationLine: 'underline' },
  uploadError: { color: t.colors.textSub, marginTop: 10, textAlign: 'center', lineHeight: 20, paddingHorizontal: 12 },
  retry: { alignSelf: 'stretch', marginTop: 22 },
  preview: {
    width: 110,
    height: 110,
    borderRadius: t.radius.lg,
    backgroundColor: t.colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  formTitle: { fontSize: 18, fontWeight: '800', color: t.colors.text },
  formSub: { fontSize: 13, color: t.colors.textSub, lineHeight: 18 },
  remaining: { fontSize: 12, color: t.colors.primary, fontWeight: '700' },
  small: { fontSize: 12, color: t.colors.textSub, lineHeight: 17, marginTop: 8, textAlign: 'center', paddingHorizontal: 12 },
  smallInline: { fontSize: 12, color: t.colors.textSub, lineHeight: 17 },
  provider: { fontSize: 11, color: t.colors.textSub, fontWeight: '600' },
}));
