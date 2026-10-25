import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';

import { isApiError } from '@/api/errors';
import type { PairingPermission, RetireReason } from '@/api/types';
import { defaultName, draftOf, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { ItemThumb } from '@/components/ItemThumb';
import { RetireSheet } from '@/components/RetireSheet';
import { Button, Header, notify, RetiredTag, Screen, TraceLabel } from '@/components/ui';
import type { IItemImage, WardrobeItem } from '@/data/types';
import { api, errorAlertMessage, errorMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { canEditCloset, OWNER_ACCESS_TEXT, ownerAccessProblem, PERMISSION_LABEL } from '@/lib/pairingRules';
import { uploadPhoto } from '@/lib/photoUpload';
import { retireReasonLabel } from '@/lib/retire';
import { imageFromDto, inputFromDraft, itemFromDto } from '@/lib/serverItems';
import { makeStyles, useColors } from '@/theme';

// 옷장 도우미가 보는 주인 옷 (doc/26 5단계): 보기, 수정·사진 바꾸기·사용 중지·다시 사용(등록·수정 권한).
// 완전 삭제는 주인만 할 수 있어 이 화면에 없다. 바꾼 일은 작업 기록에 남고 주인에게 묶음 알림이 간다.
export default function OwnerItem() {
  const { ownerId, id } = useLocalSearchParams<{ ownerId?: string; id?: string }>();
  const [item, setItem] = useState<WardrobeItem | null>(null);
  const [permission, setPermission] = useState<PairingPermission | null>(null);
  const [draft, setDraft] = useState<ItemDraft | null>(null);
  /** 새로 올린 사진 (저장할 때 imageId를 보낸다) */
  const [newPhoto, setNewPhoto] = useState<{ uri: string; image: IItemImage } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [trace, setTrace] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const [retireOpen, setRetireOpen] = useState(false);
  const s = useStyles();
  const c = useColors();

  const goBack = (message: string) => {
    notify('옷을 열 수 없어요', message);
    router.replace('/helper');
  };

  /** 연결·권한 문제면 안내하고 true. 그 밖의 오류면 false */
  const access = (e: unknown): boolean => {
    const kind = isApiError(e) ? ownerAccessProblem(e.status, e.code) : null;
    if (!kind) return false;
    goBack(OWNER_ACCESS_TEXT[kind]);
    return true;
  };

  const apply = (dto: Parameters<typeof itemFromDto>[0]) => {
    const next = itemFromDto(dto);
    setItem(next);
    setDraft(draftOf(next));
    setNewPhoto(null);
  };

  const load = async () => {
    if (!ownerId || !id) {
      goBack(OWNER_ACCESS_TEXT.gone);
      return;
    }
    setError(null);
    setTrace(undefined);
    try {
      const pairings = await api.pairing.list();
      const mine = pairings.asHelper.find((p) => p.partnerId === ownerId && p.status === 'ACTIVE');
      if (!mine) {
        goBack(OWNER_ACCESS_TEXT.gone);
        return;
      }
      setPermission(mine.permission);
      apply(await api.owners.get(ownerId, id));
    } catch (e) {
      if (access(e)) return;
      setError(errorMessage(e, '옷을 불러오지 못했어요.'));
      setTrace(systemTraceId(e));
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 화면을 열 때 한 번
  }, [ownerId, id]);

  /** 서버에 한 가지를 요청한다. 연결·권한 문제는 목록으로, 그 밖의 오류는 안내만 */
  const run = async (task: () => Promise<void>, failTitle: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await task();
    } catch (e) {
      if (access(e)) return;
      notify(failTitle, errorAlertMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const save = () =>
    run(async () => {
      if (!draft || !ownerId || !id) return;
      const input = inputFromDraft({ ...draft, name: draft.name.trim() || defaultName(draft) });
      const dto = await api.owners.update(ownerId, id, newPhoto ? { ...input, imageId: newPhoto.image.imageId } : input);
      apply(dto);
      notify('저장했어요', '주인 옷장에 반영됐어요.');
    }, '저장하지 못했어요');

  const changePhoto = async () => {
    if (!ownerId) return;
    try {
      const res = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 1,
        exif: false,
      });
      if (res.canceled || !res.assets[0]) return;
      const uri = res.assets[0].uri;
      await run(async () => {
        const uploaded = await uploadPhoto(uri, ownerId);
        setNewPhoto({ uri, image: imageFromDto(uploaded.image) ?? uploaded.image });
      }, '사진을 올리지 못했어요');
    } catch (e) {
      console.warn('[helper/item] 사진 선택 실패', e);
      notify('사진을 불러오지 못했어요', '다시 시도해 주세요.');
    }
  };

  const retire = (reason: string, memo: string) =>
    run(async () => {
      if (!ownerId || !id) return;
      apply(await api.owners.retire(ownerId, id, { reason: reason as RetireReason, memo: memo.trim() || undefined }));
      setRetireOpen(false);
    }, '사용 중지하지 못했어요');

  const restore = () =>
    run(async () => {
      if (!ownerId || !id) return;
      apply(await api.owners.restore(ownerId, id));
    }, '다시 사용하지 못했어요');

  const canEdit = canEditCloset(permission);

  return (
    <Screen
      header={<Header title="옷 보기" />}
      footer={canEdit && item ? <Button title={busy ? '처리하는 중…' : '저장'} onPress={() => void save()} disabled={busy} /> : undefined}
    >
      {error ? (
        <View style={s.notice}>
          <Text style={s.noticeText}>{error}</Text>
          <TraceLabel traceId={trace} />
          <Button title="다시 시도" variant="outline" small onPress={() => void load()} style={{ marginTop: 8 }} />
        </View>
      ) : !item || !draft ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={c.primary} />
      ) : (
        <>
          <View style={s.photoWrap}>
            {newPhoto ? (
              <Image source={{ uri: newPhoto.uri }} style={s.photo} contentFit="cover" />
            ) : (
              <ItemThumb item={item} size={220} variant="full" dim={!!item.retiredAt} />
            )}
          </View>
          <Text style={s.title}>
            {item.name} {item.retiredAt ? <RetiredTag /> : null}
          </Text>
          <Text style={s.perm}>옷장 도우미 · {permission ? PERMISSION_LABEL[permission] : ''}</Text>

          {item.retiredAt && (
            <View style={s.retired}>
              <Text style={s.retiredTitle}>사용 안 함 · {retireReasonLabel(item.retireReason)}</Text>
              {!!item.retireMemo && <Text style={s.retiredMemo}>{item.retireMemo}</Text>}
              {canEdit && <Button title="다시 사용하기" variant="soft" small onPress={() => void restore()} disabled={busy} />}
            </View>
          )}

          {canEdit && (
            <View style={s.row}>
              <Button title="사진 바꾸기" variant="outline" small onPress={() => void changePhoto()} disabled={busy} style={s.flex} />
              {!item.retiredAt && (
                <Button title="사용 중지" variant="danger" small onPress={() => setRetireOpen(true)} disabled={busy} style={s.flex} />
              )}
            </View>
          )}
          {newPhoto && <Text style={s.hint}>새 사진을 올렸어요. 저장하면 바뀌어요.</Text>}

          <View pointerEvents={canEdit ? 'auto' : 'none'} style={[s.form, !canEdit && { opacity: 0.85 }]}>
            <ItemForm value={draft} onChange={setDraft} purchaseBaseDate={item.createdAt.slice(0, 10)} />
          </View>
          <Text style={s.hint}>
            {canEdit
              ? '완전 삭제는 옷장 주인만 할 수 있어요.'
              : '보기만 할 수 있어요. 옷을 고치려면 옷장 주인이 "등록·수정" 권한을 줘야 해요.'}
          </Text>
        </>
      )}
      <RetireSheet item={retireOpen ? item : null} busy={busy} onClose={() => setRetireOpen(false)} onSubmit={(r, m) => void retire(r, m)} />
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  photoWrap: { alignItems: 'center', marginBottom: 14 },
  photo: { width: 220, height: 220, borderRadius: 20 },
  title: { fontSize: 20, fontWeight: '800', color: t.colors.text, textAlign: 'center' },
  perm: { fontSize: 12, color: t.colors.textMuted, textAlign: 'center', marginTop: 4, marginBottom: 14 },
  retired: { backgroundColor: t.colors.surfaceAlt, borderRadius: t.radius.md, padding: 14, gap: 8, marginBottom: 14 },
  retiredTitle: { fontSize: 14, fontWeight: '800', color: t.colors.text },
  retiredMemo: { fontSize: 13, color: t.colors.textSub, lineHeight: 19 },
  row: { flexDirection: 'row', gap: 8, marginBottom: 6 },
  flex: { flex: 1 },
  form: { marginTop: 14 },
  hint: { color: t.colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 10 },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
