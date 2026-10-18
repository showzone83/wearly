package app.wearly.auth.application.port.in.dto;

import java.util.List;

/**
 * 2단계 인증 성공: 정식 토큰. recoveryCodes는 처음 등록할 때만 10개(한 번만 보여 준다), 그 밖에는 null
 *
 * @param recoveryCodesLeft 쓰지 않은 복구 코드 수
 */
public record MfaVerifyResultDto(
    AuthResultDto tokens, List<String> recoveryCodes, int recoveryCodesLeft) {}
