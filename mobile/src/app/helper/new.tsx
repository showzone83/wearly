import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { router, useLocalSearchParams } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, Platform, StyleSheet, Text, View } from 'react-native';

import { isApiError } from '@/api/errors';
import { GarmentArt } from '@/components/GarmentArt';
import { defaultName, ItemForm, type ItemDraft } from '@/components/ItemForm';
import { Badge, Button, Header, notify, Row, Screen, Title, TraceLabel } from '@/components/ui';
import { api, errorAlertMessage } from '@/lib/api';
import { systemTraceId } from '@/lib/errorText';
import { manualDraft } from '@/lib/itemDraft';
import { OWNER_ACCESS_TEXT, ownerAccessProblem } from '@/lib/pairingRules';
import { uploadPhoto } from '@/lib/photoUpload';
import { draftFromSuggestion, inputFromDraft } from '@/lib/serverItems';
import { makeStyles, useColors } from '@/theme';

type Stage = 'pick' | 'working' | 'form';

// 옷장 도우미의 대리 등록 (doc/26 5단계): 사진을 주인 경로로 올리고(주인의 사진 한도) AI로 분석(돕는 사람의 분석 한도)한 뒤
// 폼을 고쳐 주인 옷장에 등록한다. 분석이 안 되면 직접 입력으로 이어 간다. 등록은 작업 기록에 남고 주인에게 묶음 알림이 간다.
export default function OwnerNewItem() {
  const { ownerId, name } = useLocalSearchParams<{ ownerId?: string; name?: string }>();
  const [stage, setStage] = useState<Stage>('pick');
  const [draft, setDraft] = useState<ItemDraft | null>(null);
  const [aiDone, setAiDone] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);
  const [problem, setProblem] = useState<{ message: string; trace?: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const busy = useRef(false);
  const s = useStyles();
  const c = useColors();

  const ownerName = name || '주인';

  /** 연결·권한 문제면 안내하고 목록으로 돌아간다. 그 밖의 오류면 false */
  const handleAccess = (e: unknown): boolean => {
    const kind = isApiError(e) ? ownerAccessProblem(e.status, e.code) : null;
    if (!kind) return false;
    notify('등록할 수 없어요', OWNER_ACCESS_TEXT[kind]);
    router.replace('/helper');
    return true;
  };

  const manual = (uri?: string) => {
    setDraft(manualDraft(uri ? { uri } : undefined));
    setAiDone(false);
    setStage('form');
  };

  const analyze = async (uri: string) => {
    if (!ownerId || busy.current) return;
    busy.current = true;
    setStage('working');
    setProblem(null);
    try {
      const uploaded = await uploadPhoto(uri, ownerId);
      const photo = { uri, image: uploaded.image };
      try {
        const res = await api.owners.analyzeImage(ownerId, { imageId: uploaded.image.imageId });
        setDraft(draftFromSuggestion(res.suggestion, uri, uploaded.image));
        setRemaining(res.remainingToday);
        setAiDone(true);
      } catch (e) {
        if (handleAccess(e)) return;
        // 분석을 못 해도 올린 사진은 그대로 두고 직접 입력으로 이어 간다 (사진은 이미 주인 것으로 저장됐다)
        notify('AI 분석을 못 했어요', `${errorAlertMessage(e)}\n직접 입력해 주세요.`);
        setDraft(manualDraft(photo));
        setAiDone(false);
      }
      setStage('form');
    } catch (e) {
      if (handleAccess(e)) return;
      setProblem({ message: errorAlertMessage(e), trace: systemTraceId(e) });
      setStage('pick');
    } finally {
      busy.current = false;
    }
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
      // 올리기 전에 긴 변 1600·JPEG로 다시 저장하므로 여기서는 압축하지 않는다
      const opts: ImagePicker.ImagePickerOptions = {
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 1,
        exif: false,
      };
      const res = source === 'camera' ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
      if (res.canceled || !res.assets[0]) return;
      void analyze(res.assets[0].uri);
    } catch (e) {
      console.warn('[helper/new] 사진 선택 실패', e);
      notify('사진을 불러오지 못했어요', '직접 입력으로 등록할 수 있어요.');
    }
  };

  const save = async () => {
    if (!draft || !ownerId || saving) return;
    setSaving(true);
    try {
      const form = { ...draft, name: draft.name.trim() || defaultName(draft) };
      const input = inputFromDraft(form);
      const dto = await api.owners.create(ownerId, draft.image ? { ...input, imageId: draft.image.imageId } : input);
      notify('옷을 등록했어요', `${ownerName}님의 옷장에 "${dto.name}"을(를) 넣었어요.`);
      router.back();
    } catch (e) {
      if (handleAccess(e)) return;
      notify('옷을 저장하지 못했어요', errorAlertMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const header = <Header title={`${ownerName}님 옷장에 등록`} />;

  if (stage === 'working') {
    return (
      <Screen header={header} scroll={false}>
        <View style={s.center}>
          <ActivityIndicator color={c.primary} />
          <Title style={s.workTitle}>사진을 올리고 분석하고 있어요</Title>
          <Text style={s.sub}>사진은 {ownerName}님의 옷장에 저장돼요.</Text>
        </View>
      </Screen>
    );
  }

  if (stage === 'form' && draft) {
    return (
      <Screen
        header={header}
        footer={<Button title={saving ? '저장하는 중…' : `${ownerName}님 옷장에 저장`} onPress={() => void save()} disabled={saving} />}
      >
        <Row style={{ gap: 16, marginTop: 4, marginBottom: 22 }}>
          <View style={s.preview}>
            {draft.imageUri ? (
              <Image source={{ uri: draft.imageUri }} style={StyleSheet.absoluteFill} contentFit="cover" />
            ) : (
              <GarmentArt subType={draft.subType} category={draft.category} color={draft.color} pattern={draft.pattern} size={86} />
            )}
          </View>
          <View style={{ flex: 1, gap: 6 }}>
            {aiDone && <Badge tone="green">AI 제안</Badge>}
            <Text style={s.formTitle}>{aiDone ? '분석이 끝났어요' : '직접 입력해 주세요'}</Text>
            <Text style={s.sub}>{aiDone ? '틀린 항목은 바로 수정하세요' : draft.imageUri ? '사진은 그대로 저장돼요' : '사진은 나중에 추가할 수 있어요'}</Text>
            {remaining !== null && aiDone && <Text style={s.remaining}>오늘 남은 내 AI 분석 {remaining}회</Text>}
          </View>
        </Row>
        <ItemForm value={draft} onChange={setDraft} aiFields={aiDone ? ['category', 'color', 'pattern'] : []} />
      </Screen>
    );
  }

  return (
    <Screen
      header={header}
      footer={
        <View style={{ gap: 4 }}>
          <Button title="앨범에서 고르기" onPress={() => void pick('library')} />
          {Platform.OS !== 'web' && <Button title="사진 찍기" variant="outline" onPress={() => void pick('camera')} />}
          <Button title="사진 없이 직접 입력" variant="ghost" small onPress={() => manual()} />
        </View>
      }
    >
      <Title style={s.pickTitle}>{ownerName}님의 옷을 등록해요</Title>
      <Text style={s.sub}>
        옷 사진을 고르면 AI가 종류·색을 알아봐요. AI 분석은 내 하루 횟수에서 빠지고, 사진은 {ownerName}님의 옷장에 저장돼요.
      </Text>
      {problem && (
        <View style={s.notice}>
          <Text style={s.noticeText}>{problem.message}</Text>
          <TraceLabel traceId={problem.trace} />
        </View>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
  workTitle: { fontSize: 20, textAlign: 'center', marginTop: 14 },
  pickTitle: { fontSize: 24, lineHeight: 32, marginTop: 8, marginBottom: 10 },
  sub: { color: t.colors.textSub, fontSize: 13, lineHeight: 19 },
  formTitle: { fontSize: 17, fontWeight: '800', color: t.colors.text },
  remaining: { fontSize: 12, color: t.colors.textMuted },
  preview: {
    width: 96,
    height: 96,
    borderRadius: 18,
    overflow: 'hidden',
    backgroundColor: t.colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  notice: { backgroundColor: t.colors.accentSoft, borderRadius: t.radius.md, padding: 14, gap: 4, marginTop: 18 },
  noticeText: { fontSize: 13, color: t.colors.text, lineHeight: 19 },
}));
