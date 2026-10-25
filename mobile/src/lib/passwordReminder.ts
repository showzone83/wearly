// 비밀번호 변경 알림 (doc/28 3단계): 계정 모드에서 앱을 열 때(세션 확인 뒤)와 로그인한 뒤 한 번 서버에 묻고,
// 기한이 지났으면 "바꾸기 / N일 연장" 창을 띄운다. 막지는 않는다. 같은 실행에서 같은 계정은 한 번만 묻는다.
import { router } from 'expo-router';

import { isApiError } from '@/api/errors';
import { api, errorAlertMessage } from '@/lib/api';
import { MOCK_MODE } from '@/lib/mode';
import { dueReminderText, postponedText } from '@/lib/passwordRules';
import { useDialogStore } from '@/store/useDialogStore';
import { useStore } from '@/store/useStore';

/** 이번 실행에서 이미 물어본 계정 (다시 로그인하면 다른 계정일 수 있어 계정별로 둔다) */
const asked = new Set<string>();

const notice = (title: string, message?: string) =>
  useDialogStore.getState().show({ kind: 'notify', title, message, okLabel: '확인' });

/** 연장 (창·변경 화면이 같이 쓴다). 성공하면 새 기한을 알린다. 성공 여부 */
export const postponePassword = async (): Promise<boolean> => {
  try {
    const next = await api.password.postpone();
    const t = postponedText(next.dueAt);
    notice(t.title, t.message);
    return true;
  } catch (e) {
    notice('미루지 못했어요', errorAlertMessage(e));
    return false;
  }
};

/** 기한이 지났으면 창을 띄운다. 게스트·목업 실행·예전 서버(404)·오류면 조용히 넘어간다 */
export const remindPasswordIfDue = async (): Promise<void> => {
  const s = useStore.getState();
  const userId = s.user?.id;
  if (MOCK_MODE || s.authMode !== 'account' || s.authLost || !userId || asked.has(userId)) return;
  asked.add(userId);
  try {
    const status = await api.password.status();
    if (!status.due) return;
    const t = dueReminderText(status.intervalDays);
    useDialogStore.getState().show({
      kind: 'confirm',
      title: t.title,
      message: t.message,
      okLabel: t.ok,
      onOk: () => router.push('/password'),
      cancelLabel: t.cancel,
      onCancel: () => void postponePassword(),
      danger: false,
    });
  } catch (e) {
    if (!isApiError(e) || e.status !== 404) console.warn('[password] 변경 주기를 확인하지 못했어요', e);
  }
};
