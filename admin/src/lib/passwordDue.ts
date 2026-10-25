// 비밀번호 변경 주기(doc/28) 화면의 순수 함수. import가 없어 Node에서 그대로 테스트한다 (tests/passwordDue.test.mjs).

/** 서버 기본 변경 주기 (wearly.auth.password.change-interval). 서버 응답(intervalDays)이 없을 때만 쓴다 */
export const DEFAULT_INTERVAL_DAYS = 90;

interface IPasswordDueLike {
  dueAt: string | null;
  due: boolean;
  intervalDays: number;
}

const daysOf = (intervalDays: number | null | undefined): number => {
  return intervalDays && intervalDays > 0 ? intervalDays : DEFAULT_INTERVAL_DAYS;
};

/** ISO 시각 → "1월 23일" (브라우저 시간대). 읽을 수 없으면 null */
export const dueDateText = (iso: string | null | undefined): string | null => {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
};

/** 화면 위 안내 띠 문구. 기한이 지나지 않았거나 상태를 모르면 null (띄우지 않는다) */
export const passwordDueBannerText = (status: IPasswordDueLike | null | undefined): string | null => {
  if (!status?.due) return null;
  return `비밀번호를 바꾼 지 ${daysOf(status.intervalDays)}일이 지났어요. 새 비밀번호로 바꾸거나 알림을 미룰 수 있어요.`;
};

/** 연장 버튼 글자 */
export const postponeLabel = (intervalDays: number | null | undefined): string => `${daysOf(intervalDays)}일 연장`;

/** 연장한 뒤 토스트 (서버 응답의 새 기한) */
export const postponedToastText = (dueAt: string | null | undefined): string => {
  const when = dueDateText(dueAt);
  return when ? `비밀번호 변경 알림을 미뤘어요. ${when}에 다시 알려 드릴게요.` : '비밀번호 변경 알림을 미뤘어요.';
};

/** 내 계정 화면의 "다음에 바꿀 날" 한 줄. 기한이 없으면(기능 꺼짐·비밀번호 없음) null */
export const nextChangeText = (status: IPasswordDueLike | null | undefined): string | null => {
  if (!status) return null;
  const when = dueDateText(status.dueAt);
  if (!when) return null;
  return status.due ? `${when}까지 바꿀 예정이었어요. 지금 바꿔 주세요.` : `다음에 바꿀 날: ${when}`;
};
