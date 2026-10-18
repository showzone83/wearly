import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { PersonalForm } from '@/components/PersonalForm';
import { Button, confirm, Header, notify, Screen } from '@/components/ui';
import { saveProfile } from '@/lib/account';
import { errorAlertMessage } from '@/lib/api';
import { EMPTY_PERSONAL, isPersonalEmpty, personalOf, withPersonal } from '@/lib/personalInfo';
import { useStore } from '@/store/useStore';
import { makeStyles, useColors } from '@/theme';

// 나의 정보 (GitHub 이슈 #5): 연령대·체형·키·핏 선호 보기·수정. 모두 선택이고 언제든 지울 수 있다.
// 계정 모드는 PUT /users/me/profile에 함께 저장하고, 게스트·목업 실행은 기기에만 저장한다.
export default function Personal() {
  const profile = useStore((s) => s.profile);
  const isAccount = useStore((s) => s.authMode === 'account');
  const [draft, setDraft] = useState(profile);
  const [valid, setValid] = useState(true);
  const [busy, setBusy] = useState(false);
  const s = useStyles();
  const c = useColors();

  const save = async (next = draft) => {
    setBusy(true);
    try {
      await saveProfile(next);
      router.back();
    } catch (e) {
      notify('나의 정보를 저장하지 못했어요', errorAlertMessage(e));
      setBusy(false);
    }
  };

  const clearAll = () =>
    confirm(
      '나의 정보를 모두 지울까요?',
      '연령대·체형·키·핏 선호가 지워지고, 추천에서도 빠져요.',
      () => void save(withPersonal(profile, EMPTY_PERSONAL)),
      '지우기',
    );

  const saved = personalOf(profile);

  return (
    <Screen
      header={
        <Header
          title="나의 정보"
          right={
            isPersonalEmpty(saved) ? null : (
              <Pressable onPress={clearAll} disabled={busy} hitSlop={8} accessibilityRole="button">
                <Text style={[s.clear, { color: c.danger }]}>모두 지우기</Text>
              </Pressable>
            )
          }
        />
      }
      footer={
        <Button
          title={busy ? '' : '저장'}
          icon={busy ? <ActivityIndicator color={c.onPrimary} /> : undefined}
          disabled={busy || !valid}
          onPress={() => void save()}
        />
      }
    >
      <View style={{ paddingTop: 10 }}>
        <Text style={s.desc}>
          나에게 맞는 핏과 실루엣을 고르는 데 참고해요. 모두 선택 사항이에요.
          {isAccount ? '' : '\n게스트로 쓰는 동안에는 이 기기에만 저장돼요.'}
        </Text>
        <View style={{ marginTop: 18 }}>
          <PersonalForm value={draft} onChange={setDraft} onValidChange={setValid} />
        </View>
      </View>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  desc: { color: t.colors.textSub, fontSize: 14, lineHeight: 21 },
  clear: { fontSize: 14, fontWeight: '600' },
}));
