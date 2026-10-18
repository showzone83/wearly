import { useState } from 'react';

import { Icon } from '@/components/Icon';
import { defaultName, draftOf, FORMALITY_TEXT, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { ItemThumb } from '@/components/ItemThumb';
import { PhotoSheet } from '@/components/PhotoSheet';
import { RetireSheet } from '@/components/RetireSheet';
import { Button, confirm, Divider, Header, notify, Row, Screen, SectionHead, TraceLabel } from '@/components/ui';

import { useStore } from '@/store/useStore';

import { Image } from 'expo-image';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { removeItem, restoreItem, retireItem, saveItem } from '@/lib/account';
import { errorAlertMessage, errorMessage } from '@/lib/api';
import { categoryLabel, colorLabel, colorMeta, patternLabel, seasonLabel, styleLabel, subTypeLabel } from '@/lib/codes';
import { diffDays, todayKey } from '@/lib/date';
import { systemTraceId } from '@/lib/errorText';
import { itemColors, purchaseInfo, purchaseText } from '@/lib/itemAttrs';
import { pickItemPhoto, reanalysisAvailable, reanalyzeItemPhoto, removeItemPhoto, replaceItemPhoto } from '@/lib/itemPhoto';
import { josa } from '@/lib/josa';
import {
  aiBadgeFields,
  applyReanalysis,
  hasItemPhoto,
  isSmallPhoto,
  reanalysisChanges,
  REMOVE_PHOTO_MESSAGE,
  REMOVE_PHOTO_TITLE,
  SMALL_PHOTO_TEXT,
  type TPhotoAction,
  type TReanalysisField,
} from '@/lib/photoEditRules';
import { isRetired, retiredSummary } from '@/lib/retire';
import { cardBase, makeStyles, useColors } from '@/theme';

import type { WardrobeItem } from '@/data/types';

/** 완전 삭제 확인 문구 (doc/13 4장 그대로) */
const DELETE_MESSAGE =
  "사진과 정보가 지워지고 되돌릴 수 없어요. 이 옷이 들어간 코디·기록에서도 빠져요. 버리거나 기부한 옷이라면 '사용하지 않는 옷'으로 옮기는 걸 권해요.";

/** 고른 사진 (미리보기·다시 시도용) */
interface IPickedPhoto {
  uri: string;
  width?: number;
  height?: number;
}

/** 사진 바꾸기 진행: 올리는 중 · 실패(다시 시도) · 빼는 중 */
type TPhotoTask =
  | ({ status: 'uploading' } & IPickedPhoto)
  | ({ status: 'error'; message: string; traceId?: string } & IPickedPhoto)
  | { status: 'removing' };

/** 새 사진을 올린 뒤 AI 다시 분석: 제안 → 분석 중 → 바뀌는 값 미리 보기 → 적용함 / 오류 */
type TReanalysis =
  | { status: 'offer' }
  | { status: 'loading' }
  | { status: 'result'; suggested: ItemDraft; remaining?: number }
  | { status: 'applied'; count: number }
  | { status: 'error'; message: string; traceId?: string };

/** 다시 분석 미리 보기의 항목 이름 (폼의 이름과 같게) */
const FIELD_LABEL: Record<TReanalysisField, string> = {
  kind: '종류',
  colors: '색상',
  pattern: '패턴',
  seasons: '계절',
  styles: '스타일',
  formality: '격식',
};

/** 다시 분석 미리 보기의 값 글자 */
const fieldText = (f: TReanalysisField, d: ItemDraft): string => {
  switch (f) {
    case 'kind':
      return `${categoryLabel(d.category)} · ${subTypeLabel(d.subType)}`;
    case 'colors':
      return itemColors(d).map(colorLabel).join('·');
    case 'pattern':
      return patternLabel(d.pattern);
    case 'seasons':
      return d.seasons.map(seasonLabel).join('·') || '없음';
    case 'styles':
      return d.styles.map(styleLabel).join('·') || '없음';
    case 'formality':
      return FORMALITY_TEXT[String(d.formality) as keyof typeof FORMALITY_TEXT] ?? String(d.formality);
  }
};

const ItemDetail = () => {
  const { id } = useLocalSearchParams<{ id: string }>();
  const item = useStore((st) => st.items.find((i) => i.id === id));
  const [draft, setDraft] = useState<ItemDraft | null>(item ? draftOf(item) : null);
  const [busy, setBusy] = useState(false);
  /** 사용 중지 시트 */
  const [sheetOpen, setSheetOpen] = useState(false);
  /** 사진 바꾸기 시트 */
  const [photoSheet, setPhotoSheet] = useState(false);
  const [photoTask, setPhotoTask] = useState<TPhotoTask | null>(null);
  /** 마지막으로 고른 사진이 작다 (긴 변 600 미만) → 흐리게 보일 수 있다는 안내 */
  const [smallPhoto, setSmallPhoto] = useState(false);
  const [reanalysis, setReanalysis] = useState<TReanalysis | null>(null);
  /** 다시 분석을 적용한 칸 (폼에 AI 표시) */
  const [aiFields, setAiFields] = useState<('category' | 'color' | 'pattern')[]>([]);
  const s = useStyles();
  const colors = useColors();

  if (!item || !draft) {
    return (
      <Screen header={<Header title="아이템" />}>
        <Text style={{ color: colors.textSub, marginTop: 40, textAlign: 'center' }}>
          삭제되었거나 없는 아이템이에요.
        </Text>
      </Screen>
    );
  }

  const retired = isRetired(item);
  // 저장된 값 기준 (폼에서 고치는 중인 값이 아니다)
  const savedColors = itemColors(item);
  const since = item.lastWornAt ? diffDays(todayKey(), item.lastWornAt) : null;
  const sleeping = since === null || since >= 30;
  // 폼 값끼리만 비교한다 (draftOf가 키 순서를 맞춘다). 사진은 폼 저장과 따로 바로 반영하므로 비교하지 않는다
  // (사진 바꾸기·옮기기·주소 갱신으로 사진 필드가 바뀌어도 '수정됨'이 되지 않게)
  const formKey = (d: ItemDraft | WardrobeItem) => JSON.stringify({ ...draftOf(d), imageUri: undefined });
  const dirty = formKey(draft) !== formKey(item);
  const hasPhoto = hasItemPhoto(item);
  const photoBusy = photoTask?.status === 'uploading' || photoTask?.status === 'removing';
  /** 올리는 동안 고른 사진을 미리 보여 준다 (실패하면 지금 사진 그대로) */
  const preview = photoTask?.status === 'uploading' ? photoTask.uri : undefined;
  const changes = reanalysis?.status === 'result' ? reanalysisChanges(draft, reanalysis.suggested) : [];

  /** 다른 기기에서 지운 옷이라 기기에서도 지웠을 때 */
  const gone = () => {
    notify('이미 삭제된 옷이에요', '다른 기기에서 지운 옷이라 이 기기에서도 지웠어요.');
    router.back();
  };

  // 계정 모드는 서버에 먼저 반영하고, 성공하면 기기 상태를 바꾼다
  const onDelete = async () => {
    setBusy(true);
    try {
      await removeItem(item.id);
      router.back();
    } catch (e) {
      notify('삭제하지 못했어요', errorAlertMessage(e));
      setBusy(false);
    }
  };

  const onAskDelete = () =>
    confirm(`${josa(item.name, '을', '를')} 완전히 삭제할까요?`, DELETE_MESSAGE, () => void onDelete(), '완전 삭제');

  const onRetire = async (reason: string, memo: string) => {
    setBusy(true);
    try {
      const exists = await retireItem(item.id, { reason, memo });
      setSheetOpen(false);
      if (!exists) gone();
    } catch (e) {
      notify('옮기지 못했어요', errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const onRestore = async () => {
    setBusy(true);
    try {
      const exists = await restoreItem(item.id);
      if (!exists) gone();
    } catch (e) {
      notify('다시 사용하지 못했어요', errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  // ── 사진 바꾸기 (이슈 #8) ──

  /** 고른 사진을 올려 바꾼다 (게스트는 기기 사진만). 실패하면 사진 아래 오류와 '다시 시도' */
  const runReplace = async ({ uri, width, height }: IPickedPhoto) => {
    const picked: IPickedPhoto = { uri, width, height };
    setPhotoTask({ status: 'uploading', ...picked });
    setSmallPhoto(isSmallPhoto(picked.width, picked.height));
    setReanalysis(null);
    try {
      const res = await replaceItemPhoto(item.id, picked.uri);
      setPhotoTask(null);
      if (res.kind === 'gone') {
        gone();
        return;
      }
      if (reanalysisAvailable(res.kind === 'server' ? res.image : undefined)) setReanalysis({ status: 'offer' });
    } catch (e) {
      console.warn('[item] 사진 바꾸기 실패', e, (e as { cause?: unknown }).cause);
      setPhotoTask({
        status: 'error',
        ...picked,
        message: errorMessage(e, '사진을 올리지 못했어요.'),
        traceId: systemTraceId(e),
      });
    }
  };

  const onRemovePhoto = async () => {
    setPhotoTask({ status: 'removing' });
    try {
      const exists = await removeItemPhoto(item.id);
      setPhotoTask(null);
      setSmallPhoto(false);
      setReanalysis(null);
      if (!exists) gone();
    } catch (e) {
      setPhotoTask(null);
      notify('사진을 빼지 못했어요', errorAlertMessage(e));
    }
  };

  /** 사진 시트에서 고른 것 (시트가 다 닫힌 뒤 불린다) */
  const onPhotoAction = async (action: TPhotoAction) => {
    if (action === 'remove') {
      confirm(REMOVE_PHOTO_TITLE, REMOVE_PHOTO_MESSAGE, () => void onRemovePhoto(), '사진 빼기');
      return;
    }
    try {
      const picked = await pickItemPhoto(action);
      if (picked.kind === 'denied') {
        notify('카메라 권한이 필요해요', '설정에서 카메라 접근을 허용해 주세요.');
        return;
      }
      if (picked.kind === 'picked') void runReplace(picked);
    } catch (e) {
      console.warn('[item] 사진 선택 실패', e);
      notify('사진을 불러오지 못했어요', '잠시 후 다시 시도해 주세요.');
    }
  };

  const onCancelPhotoTask = () => {
    setPhotoTask(null);
    setSmallPhoto(false);
  };

  /** AI 다시 분석 (누를 때만. 계정 모드는 하루 한도에 들어간다) */
  const onReanalyze = async () => {
    setReanalysis({ status: 'loading' });
    try {
      const current = useStore.getState().items.find((i) => i.id === item.id) ?? item;
      const res = await reanalyzeItemPhoto(current);
      setReanalysis({ status: 'result', suggested: res.suggested, remaining: res.remaining });
    } catch (e) {
      console.warn('[item] AI 다시 분석 실패', e);
      setReanalysis({ status: 'error', message: errorMessage(e, 'AI 분석에 실패했어요.'), traceId: systemTraceId(e) });
    }
  };

  /** 바뀌는 값을 폼에 넣는다. 저장은 '수정 내용 저장'으로 */
  const onApplyReanalysis = (suggested: ItemDraft) => {
    const fields = reanalysisChanges(draft, suggested);
    let next = applyReanalysis(draft, suggested, fields);
    // 이름을 직접 바꾸지 않았다면(자동 이름) 새 종류·색에 맞춘다 (폼과 같은 규칙)
    if (draft.name === defaultName(draft)) next = { ...next, name: defaultName(next) };
    setDraft(next);
    setAiFields(aiBadgeFields(fields));
    setReanalysis({ status: 'applied', count: fields.length });
  };

  const onSave = async () => {
    setBusy(true);
    try {
      // 사진은 지금 아이템 값 그대로 (폼을 연 뒤 바뀌었을 수 있다)
      const exists = await saveItem(item.id, { ...draft, imageUri: item.imageUri, image: item.image });
      if (!exists) notify('이미 삭제된 옷이에요', '다른 기기에서 지운 옷이라 이 기기에서도 지웠어요.');
      router.back();
    } catch (e) {
      notify('저장하지 못했어요', errorAlertMessage(e));
      setBusy(false);
    }
  };

  return (
    <Screen
      header={<Header title="아이템 상세" />}
      footer={
        <Button
          title={busy ? '' : dirty ? '수정 내용 저장' : '저장됨'}
          icon={busy ? <ActivityIndicator color={colors.onPrimary} /> : undefined}
          disabled={!dirty || busy}
          onPress={onSave}
        />
      }
    >
      {retired && (
        <View style={s.banner} accessibilityRole="summary">
          <Row style={{ gap: 8 }}>
            <Icon name="hanger" size={18} color={colors.textSub} />
            <Text style={s.bannerTitle}>{retiredSummary(item)}</Text>
          </Row>
          {item.retireMemo ? <Text style={s.bannerMemo}>“{item.retireMemo}”</Text> : null}
          <Text style={s.bannerNote}>옷장과 추천에서 빠져 있어요. 착용 기록은 그대로 남아요.</Text>
          <Button
            title="다시 사용하기"
            variant="outline"
            small
            disabled={busy}
            onPress={() => void onRestore()}
            style={s.bannerButton}
          />
        </View>
      )}

      <View style={{ alignItems: 'center' }}>
        <Pressable
          onPress={() => setPhotoSheet(true)}
          disabled={photoBusy || busy}
          accessibilityRole="button"
          accessibilityLabel={hasPhoto ? '사진 바꾸기' : '사진 추가'}
          accessibilityHint="카메라로 찍거나 앨범에서 고를 수 있어요"
        >
          <ItemThumb
            item={{ ...item, ...draft, imageUri: item.imageUri, image: item.image }}
            size={200}
            radius={28}
            dim={retired}
            variant="full"
          />
          {preview ? (
            <View style={[StyleSheet.absoluteFill, s.photoLayer]} pointerEvents="none">
              <Image source={{ uri: preview }} style={StyleSheet.absoluteFill} contentFit="cover" />
            </View>
          ) : null}
          {photoBusy && (
            <View style={[StyleSheet.absoluteFill, s.photoLayer, s.photoDim]} pointerEvents="none">
              <ActivityIndicator color="#fff" />
              <Text style={s.photoDimText}>{photoTask?.status === 'removing' ? '사진을 빼고 있어요' : '사진을 올리고 있어요'}</Text>
            </View>
          )}
          <View style={s.photoBadge} pointerEvents="none">
            <Icon name="camera" size={16} color={colors.onPrimary} />
          </View>
        </Pressable>
        {photoTask?.status === 'error' && (
          <View style={s.photoError} accessibilityRole="alert">
            <Text style={s.photoErrorTitle}>사진을 올리지 못했어요</Text>
            <Text style={s.photoErrorText}>{photoTask.message}</Text>
            <TraceLabel traceId={photoTask.traceId} style={{ textAlign: 'center' }} />
            <Row style={{ gap: 8, marginTop: 10 }}>
              <Button title="다시 시도" small onPress={() => void runReplace(photoTask)} style={s.photoErrorButton} />
              <Button title="취소" variant="outline" small onPress={onCancelPhotoTask} style={s.photoErrorButton} />
            </Row>
          </View>
        )}
        {smallPhoto && photoTask?.status !== 'error' && <Text style={s.smallNote}>{SMALL_PHOTO_TEXT}</Text>}
        <View style={s.attrs} accessible accessibilityLabel={`색 ${savedColors.map(colorLabel).join(', ')}`}>
          {savedColors.map((c, i) => (
            <View
              key={c}
              style={[
                i === 0 ? s.mainDot : s.subDot,
                { backgroundColor: colorMeta(c).hex },
                colorMeta(c).light >= 0.9 && s.dotEdge,
              ]}
            />
          ))}
          <Text style={s.attrText}>
            {colorLabel(savedColors[0])}
            {savedColors.length > 1 ? ` + ${savedColors.slice(1).map(colorLabel).join('·')}` : ''}
          </Text>
        </View>
        <Text style={s.purchase}>구입일 {purchaseText(item)}</Text>
      </View>
      {reanalysis && (
        <View style={s.reCard}>
          {reanalysis.status === 'offer' && (
            <>
              <Text style={s.reTitle}>새 사진으로 속성을 다시 분석할까요?</Text>
              <Text style={s.reSub}>바뀌는 값만 먼저 보여 드려요. AI 분석은 하루 사용 횟수에 들어가요.</Text>
              <Row style={s.reButtons}>
                <Button title="AI로 다시 분석" small onPress={() => void onReanalyze()} style={s.reButton} />
                <Button title="괜찮아요" variant="outline" small onPress={() => setReanalysis(null)} style={s.reButton} />
              </Row>
            </>
          )}
          {reanalysis.status === 'loading' && (
            <Row style={{ gap: 10 }}>
              <ActivityIndicator color={colors.primary} />
              <Text style={s.reTitle}>AI가 분석하고 있어요</Text>
            </Row>
          )}
          {reanalysis.status === 'result' && (
            <>
              <Text style={s.reTitle}>{changes.length ? `바뀌는 값 ${changes.length}개` : '바뀌는 값이 없어요'}</Text>
              {changes.map((f) => (
                <View key={f} style={s.reRow}>
                  <Text style={s.reLabel}>{FIELD_LABEL[f]}</Text>
                  <Text style={s.reValue}>
                    <Text style={s.reOld}>{fieldText(f, draft)}</Text>
                    {'  →  '}
                    <Text style={s.reNew}>{fieldText(f, reanalysis.suggested)}</Text>
                  </Text>
                </View>
              ))}
              {reanalysis.remaining !== undefined && <Text style={s.reRemaining}>오늘 남은 AI 분석 {reanalysis.remaining}회</Text>}
              <Row style={s.reButtons}>
                {changes.length > 0 && (
                  <Button title="적용" small onPress={() => onApplyReanalysis(reanalysis.suggested)} style={s.reButton} />
                )}
                <Button
                  title={changes.length ? '취소' : '닫기'}
                  variant="outline"
                  small
                  onPress={() => setReanalysis(null)}
                  style={s.reButton}
                />
              </Row>
            </>
          )}
          {reanalysis.status === 'applied' && (
            <Row style={{ gap: 10 }}>
              <Text style={[s.reSub, { flex: 1, marginTop: 0 }]}>
                {reanalysis.count}개 항목을 폼에 넣었어요. 아래 '수정 내용 저장'을 눌러야 저장돼요.
              </Text>
              <Pressable onPress={() => setReanalysis(null)} hitSlop={10} accessibilityLabel="닫기">
                <Icon name="close" size={18} color={colors.textSub} />
              </Pressable>
            </Row>
          )}
          {reanalysis.status === 'error' && (
            <>
              <Text style={s.reTitle}>AI 분석을 하지 못했어요</Text>
              <Text style={s.reSub}>{reanalysis.message}</Text>
              <TraceLabel traceId={reanalysis.traceId} />
              <Row style={s.reButtons}>
                <Button title="닫기" variant="outline" small onPress={() => setReanalysis(null)} style={s.reButton} />
              </Row>
            </>
          )}
        </View>
      )}
      <Row style={s.stats}>
        <View style={s.stat}>
          <Text style={s.statNum}>{item.wornCount}회</Text>
          <Text style={s.statLabel}>착용</Text>
        </View>
        <View style={s.stat}>
          <Text style={s.statNum}>{since === null ? '-' : since === 0 ? '오늘' : `${since}일 전`}</Text>
          <Text style={s.statLabel}>마지막 착용</Text>
        </View>
        <View style={s.stat}>
          <Text style={[s.statNum, { color: retired ? colors.textMuted : sleeping ? colors.danger : colors.primary }]}>
            {retired ? '사용 안 함' : sleeping ? '잠자는 옷' : '활발'}
          </Text>
          <Text style={s.statLabel}>활용 상태</Text>
        </View>
      </Row>
      <ItemForm value={draft} onChange={setDraft} aiFields={aiFields} purchaseBaseDate={purchaseInfo(item).date} />

      <SectionHead title="관리" style={{ marginTop: 32 }} />
      <View style={s.manage}>
        <Pressable style={s.manageRow} disabled={busy} onPress={() => setSheetOpen(true)} accessibilityRole="button">
          <View style={{ flex: 1 }}>
            <Text style={s.manageTitle}>{retired ? '사용하지 않는 이유 바꾸기' : '사용하지 않는 옷으로'}</Text>
            <Text style={s.manageSub}>
              {retired ? '이유와 메모를 고칠 수 있어요.' : '옷장과 추천에서는 빠지고, 착용 기록은 그대로 남아요.'}
            </Text>
          </View>
          <Icon name="chevron" size={16} color={colors.textMuted} />
        </Pressable>
        <Divider />
        <Pressable style={s.manageRow} disabled={busy} onPress={onAskDelete} accessibilityRole="button">
          <Icon name="trash" size={18} color={colors.danger} />
          <View style={{ flex: 1 }}>
            <Text style={s.deleteTitle}>완전 삭제</Text>
            <Text style={s.manageSub}>사진과 정보를 지워요. 되돌릴 수 없어요.</Text>
          </View>
        </Pressable>
      </View>

      <PhotoSheet
        visible={photoSheet}
        hasPhoto={hasPhoto}
        onClose={() => setPhotoSheet(false)}
        onSelect={(action) => void onPhotoAction(action)}
      />
      <RetireSheet
        item={sheetOpen ? item : null}
        busy={busy}
        onClose={() => setSheetOpen(false)}
        onSubmit={(reason, memo) => void onRetire(reason, memo)}
      />
    </Screen>
  );
};

export default ItemDetail;

const useStyles = makeStyles((t) => ({
  banner: {
    backgroundColor: t.colors.accentSoft,
    borderRadius: t.radius.lg,
    padding: 16,
    marginTop: 4,
    marginBottom: 18,
  },
  bannerTitle: { flex: 1, fontSize: 14, fontWeight: '800', color: t.colors.text },
  bannerMemo: { fontSize: 13, color: t.colors.text, marginTop: 8, lineHeight: 19 },
  bannerNote: { fontSize: 12, color: t.colors.textSub, marginTop: 6, lineHeight: 18 },
  bannerButton: { marginTop: 12, alignSelf: 'flex-start', paddingHorizontal: 18 },
  photoLayer: { borderRadius: 28, overflow: 'hidden' },
  photoDim: { backgroundColor: 'rgba(0,0,0,0.45)', alignItems: 'center', justifyContent: 'center', gap: 8 },
  photoDimText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  photoBadge: {
    position: 'absolute',
    right: 10,
    bottom: 10,
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: t.colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: t.colors.bg,
  },
  photoError: { alignItems: 'center', marginTop: 12, paddingHorizontal: 12 },
  photoErrorTitle: { fontSize: 15, fontWeight: '800', color: t.colors.text },
  photoErrorText: { fontSize: 13, color: t.colors.textSub, marginTop: 4, textAlign: 'center', lineHeight: 19 },
  photoErrorButton: { paddingHorizontal: 18 },
  smallNote: { fontSize: 12, color: t.colors.textSub, marginTop: 10, textAlign: 'center', lineHeight: 17, paddingHorizontal: 16 },
  reCard: { ...cardBase(t), padding: 16, marginTop: 18 },
  reTitle: { fontSize: 15, fontWeight: '800', color: t.colors.text },
  reSub: { fontSize: 13, color: t.colors.textSub, marginTop: 6, lineHeight: 19 },
  reRow: { flexDirection: 'row', gap: 10, marginTop: 10, alignItems: 'flex-start' },
  reLabel: { width: 48, fontSize: 13, fontWeight: '700', color: t.colors.textSub },
  reValue: { flex: 1, fontSize: 13, color: t.colors.text, lineHeight: 19 },
  reOld: { color: t.colors.textMuted },
  reNew: { fontWeight: '800', color: t.colors.primary },
  reRemaining: { fontSize: 12, color: t.colors.primary, fontWeight: '700', marginTop: 10 },
  reButtons: { gap: 8, marginTop: 14 },
  reButton: { flex: 1 },
  attrs: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 14 },
  mainDot: { width: 14, height: 14, borderRadius: 7 },
  subDot: { width: 9, height: 9, borderRadius: 4.5 },
  dotEdge: { borderWidth: 1, borderColor: t.colors.border },
  attrText: { fontSize: 13, fontWeight: '700', color: t.colors.text, marginLeft: 2 },
  purchase: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  stats: { ...cardBase(t), marginVertical: 22 },
  stat: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  statNum: { fontSize: 16, fontWeight: '800', color: t.colors.text },
  statLabel: { fontSize: 12, color: t.colors.textSub, marginTop: 4 },
  manage: { ...cardBase(t), paddingHorizontal: 16 },
  manageRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 15 },
  manageTitle: { fontSize: 15, fontWeight: '700', color: t.colors.text },
  manageSub: { fontSize: 12, color: t.colors.textSub, marginTop: 3, lineHeight: 17 },
  deleteTitle: { fontSize: 15, fontWeight: '700', color: t.colors.danger },
}));
