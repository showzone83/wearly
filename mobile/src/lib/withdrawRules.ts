// 탈퇴 유예 규칙 (doc/27). 화면·스토어·expo 없이 순수 함수만 둔다 (단위 점검 대상).
// 사유 보기, 하고 싶은 말 정리, 삭제 예정일 문구, 탈퇴 확인·로그인 취소 확인 문구.
import type { WithdrawalReason, WithdrawRequest } from '@/api/types';

/** 하고 싶은 말 최대 글자 수 (서버 tb_user_withdrawal.reason_text와 같다) */
export const REASON_TEXT_MAX = 200;

/** 서버 기본 유예 기간 (wearly.user.withdrawal.grace). 화면 안내용이고 실제 날짜는 서버 응답(purgeAt)을 쓴다 */
export const GRACE_DAYS = 30;

/** 사유 보기 (화면 순서). 하나는 꼭 고른다 */
export const WITHDRAW_REASONS: readonly { value: WithdrawalReason; label: string }[] = [
  { value: 'NOT_USEFUL', label: '자주 안 써요' },
  { value: 'BAD_RECOMMEND', label: '추천이 마음에 안 들어요' },
  { value: 'HARD_TO_USE', label: '쓰기 어려워요' },
  { value: 'PRIVACY', label: '개인정보가 걱정돼요' },
  { value: 'OTHER_APP', label: '다른 앱을 쓸래요' },
  { value: 'OTHER', label: '기타' },
];

/** 고른 사유와 하고 싶은 말로 요청 본문을 만든다. 사유가 없으면 null(아직 보낼 수 없음). 말은 앞뒤 공백을 지우고 비면 뺀다 */
export const withdrawRequest = (reason: WithdrawalReason | null, text: string): WithdrawRequest | null => {
  if (!reason) return null;
  const t = text.trim();
  return t ? { reason, reasonText: t.slice(0, REASON_TEXT_MAX) } : { reason };
};

/** ISO 시각 → "11월 24일" (기기 시간대). 읽을 수 없으면 null */
export const purgeDateText = (iso: string | null | undefined): string | null => {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
};

/** 탈퇴 화면의 마지막 확인 창 */
export const withdrawConfirmText = (): { title: string; message: string; ok: string } => ({
  title: '정말 탈퇴할까요?',
  message: `지금 로그아웃되고, ${GRACE_DAYS}일 뒤 계정과 옷장·설정이 모두 삭제돼요. 그 전에 다시 로그인하면 탈퇴를 취소할 수 있어요.`,
  ok: '탈퇴',
});

/** 탈퇴한 뒤 안내 */
export const withdrawDoneText = (purgeAt: string | null | undefined): { title: string; message: string } => {
  const when = purgeDateText(purgeAt);
  return {
    title: '탈퇴를 신청했어요',
    message: `${when ? `${when}에` : `${GRACE_DAYS}일 뒤`} 모든 정보가 삭제돼요. 그 전에 같은 계정으로 로그인하면 탈퇴를 취소할 수 있어요.`,
  };
};

/** 로그인할 때 탈퇴 예정 계정이면 묻는 창 (403 ACCOUNT_WITHDRAWN의 params.purgeAt) */
export const cancelWithdrawalText = (purgeAt: unknown): { title: string; message: string; ok: string } => {
  const when = purgeDateText(typeof purgeAt === 'string' ? purgeAt : null);
  return {
    title: '탈퇴를 취소할까요?',
    message: `${when ? `${when}에 ` : ''}삭제될 예정인 계정이에요. 탈퇴를 취소하면 옷장과 설정을 그대로 다시 쓸 수 있어요.`,
    ok: '탈퇴 취소하고 로그인',
  };
};
