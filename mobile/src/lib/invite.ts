// 친구 초대 (doc/25): 코드 표시 형식과 공유 문구.
// 카카오톡 템플릿 카드는 카카오 로그인(개발 빌드) 때 함께 붙인다. 지금은 OS 공유 시트·문자·복사로 보낸다.

/**
 * 앱 다운로드·초대 페이지 주소. 스토어 등록이나 공개 웹 체험판이 생기면 넣는다 (doc/02 D1).
 * 값이 있으면 공유 문구에 한 줄 더한다. 앱에서는 `wearly://auth?invite=코드`로 가입 화면에 코드가 채워진다.
 */
export const INVITE_URL: string | null = null;

/** 입력·표시용: 대문자, 공백·하이픈을 빼고 8자 이하면 4자 뒤에 하이픈 (ABCD-EFGH) */
export const formatInviteCode = (raw: string): string => {
  const n = raw.replace(/[\s-]/g, '').toUpperCase();
  return n.length > 4 && n.length <= 8 ? `${n.slice(0, 4)}-${n.slice(4)}` : n;
};

/** 공유·문자로 보낼 문구. Android 공유 시트는 url을 따로 받지 않으므로 모두 문구에 넣는다 */
export const inviteMessage = (code: string): string => {
  const lines = [
    '웨어리에서 내 옷장 옷으로 출근 코디를 추천받아 봐요. 오늘 뭐 입지? 이제 고민 끝!',
    `가입할 때 초대 코드 ${formatInviteCode(code)} 를 넣어 주세요.`,
  ];
  if (INVITE_URL) lines.push(`${INVITE_URL}?invite=${encodeURIComponent(code)}`);
  return lines.join('\n');
};
