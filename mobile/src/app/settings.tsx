import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { StylePrefs, WorkPattern } from '@/components/StyleForm';
import { Button, Header, notify, Screen } from '@/components/ui';
import { saveProfile } from '@/lib/account';
import { errorMessage } from '@/lib/api';
import { useStore } from '@/store/useStore';

export default function Settings() {
  const profile = useStore((s) => s.profile);
  const [draft, setDraft] = useState(profile);
  const [busy, setBusy] = useState(false);

  // 계정 모드는 PUT /users/me/profile에 저장한 뒤 기기 상태를 바꾼다
  const save = async () => {
    setBusy(true);
    try {
      await saveProfile(draft);
      router.back();
    } catch (e) {
      notify('설정을 저장하지 못했어요', errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <Screen
      header={<Header title="스타일 설정" />}
      footer={
        <Button title={busy ? '' : '저장'} icon={busy ? <ActivityIndicator color="#fff" /> : undefined} disabled={busy} onPress={save} />
      }
    >
      <View style={{ gap: 28, paddingTop: 10 }}>
        <StylePrefs value={draft} onChange={setDraft} />
        <WorkPattern value={draft} onChange={setDraft} />
      </View>
    </Screen>
  );
}
