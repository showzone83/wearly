// 비밀번호 규칙과 변경 주기 문구 (doc/28). 화면·스토어·expo 없이 순수 함수만 둔다 (단위 점검 대상).
// 비밀번호 형식, 변경 화면 입력 확인, 기한 알림·연장·변경 완료 문구, 알림을 눌렀을 때 알아보기.

/** 서버 푸시 data.type (backend PasswordReminderNotice.TYPE와 같다) */
export const PASSWORD_DUE_TYPE = 'password-change-due';
/** 서버 푸시의 안드로이드 알림 채널 (backend PasswordReminderNotice.CHANNEL_ID와 같다) */
export const ACCOUNT_CHANNEL = 'account';
/** 서버 기본 변경 주기 (wearly.auth.password.change-interval). 서버 응답(intervalDays)이 없을 때만 쓴다 */
export const DEFAULT_INTERVAL_DAYS = 90;

/** 계약 5장: 비밀번호 8~64자, 영문과 숫자를 모두 포함. 맞으면 null */
export const passwordProblem = (pw: string): string | null => {
  if (pw.length < 8 || pw.length > 64) return '비밀번호는 8~64자로 입력해 주세요.';
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return '영문과 숫자를 모두 넣어 주세요.';
  return null;
};

export type TChangeField = 'currentPassword' | 'newPassword' | 'confirm';

/** 비밀번호 변경 화면 입력 확인. 문제가 없으면 빈 객체 (서버도 같은 규칙으로 다시 본다) */
export const changePasswordProblems = (
  current: string,
  next: string,
  confirm: string,
): Partial<Record<TChangeField, string>> => {
  const errs: Partial<Record<TChangeField, string>> = {};
  if (!current) errs.currentPassword = '지금 비밀번호를 입력해 주세요.';
  const p = passwordProblem(next);
  if (p) errs.newPassword = p;
  else if (current && next === current) errs.newPassword = '지금 비밀번호와 다르게 정해 주세요.';
  if (!errs.newPassword && confirm !== next) errs.confirm = '새 비밀번호와 같게 한 번 더 입력해 주세요.';
  return errs;
};

/** 서버 응답의 주기 일수 (0·없음이면 기본값) */
const daysOf = (intervalDays: number | null | undefined): number =>
  intervalDays && intervalDays > 0 ? intervalDays : DEFAULT_INTERVAL_DAYS;

/** ISO 시각 → "1월 23일" (기기 시간대). 읽을 수 없으면 null */
export const dueDateText = (iso: string | null | undefined): string | null => {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
};

/** 기한이 지났을 때 띄우는 창 (앱 시작·로그인 뒤): 바꾸기 / N일 연장 */
export const dueReminderText = (
  intervalDays: number | null | undefined,
): { title: string; message: string; ok: string; cancel: string } => {
  const n = daysOf(intervalDays);
  return {
    title: '비밀번호를 바꿀 때가 됐어요',
    message: `비밀번호를 바꾼 지 ${n}일이 지났어요. 안전하게 쓰려면 새 비밀번호로 바꿔 주세요. 지금 바꾸기 어려우면 ${n}일 뒤에 다시 알려 드릴게요.`,
    ok: '바꾸기',
    cancel: `${n}일 연장`,
  };
};

/** 변경 화면 위 안내 (기한이 지났을 때) */
export const dueNoticeText = (intervalDays: number | null | undefined): string =>
  `비밀번호를 바꾼 지 ${daysOf(intervalDays)}일이 지났어요. 새 비밀번호로 바꾸거나, 지금은 미루고 나중에 바꿀 수 있어요.`;

/** 연장 버튼 글자 */
export const postponeLabel = (intervalDays: number | null | undefined): string => `${daysOf(intervalDays)}일 연장`;

/** 연장한 뒤 안내 (서버 응답의 새 기한) */
export const postponedText = (dueAt: string | null | undefined): { title: string; message: string } => {
  const when = dueDateText(dueAt);
  return {
    title: '알림을 미뤘어요',
    message: when ? `${when}에 다시 알려 드릴게요.` : '다음 기한에 다시 알려 드릴게요.',
  };
};

/** 바꾼 뒤 안내 */
export const changedText = (): { title: string; message: string } => ({
  title: '비밀번호를 바꿨어요',
  message: '다른 기기에서는 로그아웃됐어요. 그 기기에서는 새 비밀번호로 다시 로그인해 주세요.',
});

/** 알림 data가 비밀번호 변경 알림인지 */
export const isPasswordDueNotice = (data: unknown): boolean =>
  (data as { type?: unknown } | null | undefined)?.type === PASSWORD_DUE_TYPE;
