package app.wearly.pairing.application.port.in.dto;

import java.util.List;

/**
 * 내 연결 목록 (최근 순).
 *
 * @param asOwner 내 옷장을 돕는 사람
 * @param asHelper 내가 돕는 옷장
 */
public record PairingListDto(List<PairingDto> asOwner, List<PairingDto> asHelper) {}
