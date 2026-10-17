import { Redirect } from 'expo-router';

import { MOCK_MODE } from '@/lib/mode';
import { useStore } from '@/store/useStore';

export default function Index() {
  const onboarded = useStore((s) => s.onboarded);
  const isAccount = useStore((s) => s.authMode === 'account');
  const authLost = useStore((s) => s.authMode === 'account' && s.authLost);
  if (authLost) return <Redirect href={{ pathname: '/auth', params: { reason: 'expired' } }} />;
  // 기본 실행은 서버 계정만 쓴다. 예전 게스트 데이터가 남아 있어도 로그인부터 하게 한다.
  if (!MOCK_MODE && !isAccount) return <Redirect href="/onboarding" />;
  return <Redirect href={onboarded ? '/home' : '/onboarding'} />;
}
