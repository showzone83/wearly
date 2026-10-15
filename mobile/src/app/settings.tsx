import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { StylePrefs, WorkPattern } from '@/components/StyleForm';
import { Button, Header, Screen } from '@/components/ui';
import { useStore } from '@/store/useStore';

export default function Settings() {
  const profile = useStore((s) => s.profile);
  const updateProfile = useStore((s) => s.updateProfile);
  const [draft, setDraft] = useState(profile);

  return (
    <Screen
      header={<Header title="스타일 설정" />}
      footer={
        <Button
          title="저장"
          onPress={() => {
            updateProfile(draft);
            router.back();
          }}
        />
      }
    >
      <View style={{ gap: 28, paddingTop: 10 }}>
        <StylePrefs value={draft} onChange={setDraft} />
        <WorkPattern value={draft} onChange={setDraft} />
      </View>
    </Screen>
  );
}
