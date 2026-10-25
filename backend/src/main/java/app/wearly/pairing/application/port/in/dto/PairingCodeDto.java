package app.wearly.pairing.application.port.in.dto;

import java.time.Instant;

/**
 * 새로 만든 연결 코드.
 *
 * @param code 정리한 8자 (화면에는 XXXX-XXXX)
 * @param role 코드를 만든 자리 (OWNER · HELPER)
 */
public record PairingCodeDto(String code, String role, Instant expiresAt) {}
