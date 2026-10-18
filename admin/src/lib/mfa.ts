// 관리자 2단계 인증(TOTP, doc/22) 화면의 순수 함수. import가 없어 Node에서 그대로 테스트한다 (tests/mfa.test.mjs).

/** 인증 앱 코드 자릿수 */
export const TOTP_DIGITS = 6;

/** 복구 코드가 이만큼 이하로 남으면 다시 등록하라고 알린다 */
export const LOW_RECOVERY_CODES = 3;

/** 수동 입력용 비밀(BASE32)을 4자씩 띄어 쓴다: "JBSW Y3DP EHPK 3PXP" */
export const formatSecret = (secret: string): string => {
  return (
    secret
      .replace(/\s+/g, '')
      .toUpperCase()
      .match(/.{1,4}/g)
      ?.join(' ') ?? ''
  );
};

/** 입력한 인증 코드에서 숫자만 남긴다 (붙여 넣은 "123 456"도 받는다) */
export const normalizeTotp = (raw: string): string => {
  return raw.replace(/\D/g, '').slice(0, TOTP_DIGITS);
};

export const isTotpCode = (code: string): boolean => {
  return new RegExp(`^\\d{${TOTP_DIGITS}}$`).test(code);
};

/** 복구 코드는 앞뒤 공백만 지우고 소문자로 보낸다 ("ABCDE-FGHJK" → "abcde-fghjk") */
export const normalizeRecoveryCode = (raw: string): string => {
  return raw.trim().toLowerCase();
};

/** 비밀번호 규칙 (서버와 같다): 8~64자, 영문과 숫자를 함께. 맞으면 null */
export const passwordRuleError = (pw: string): string | null => {
  if (pw.length < 8 || pw.length > 64) return '8~64자로 입력해 주세요.';
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return '영문과 숫자를 함께 넣어 주세요.';
  return null;
};

/** 복구 코드 내려받기(.txt) 내용 */
export const recoveryCodesFileText = (codes: readonly string[], email: string | null, issuedAt: string): string => {
  return [
    'Wearly 관리자 2단계 인증 복구 코드',
    email ? `계정: ${email}` : null,
    `발급: ${issuedAt}`,
    '',
    '인증 앱을 쓸 수 없을 때 코드 하나로 한 번 로그인할 수 있습니다. 쓴 코드는 다시 쓸 수 없습니다.',
    '',
    ...codes.map((c, i) => `${String(i + 1).padStart(2, ' ')}. ${c}`),
    '',
  ]
    .filter((line): line is string => line !== null)
    .join('\n');
};

/** 복구 코드가 적게 남았을 때 문구. 넉넉하면 null */
export const lowRecoveryCodesText = (left: number | null | undefined): string | null => {
  if (left === null || left === undefined || left > LOW_RECOVERY_CODES) return null;
  return left <= 0
    ? '복구 코드를 모두 썼어요. 내 계정에서 2단계 인증을 다시 등록해 새 복구 코드를 받으세요.'
    : `복구 코드가 ${left}개 남았어요. 내 계정에서 2단계 인증을 다시 등록해 새 복구 코드를 받으세요.`;
};
