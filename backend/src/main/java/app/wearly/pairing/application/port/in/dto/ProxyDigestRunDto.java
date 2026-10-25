package app.wearly.pairing.application.port.in.dto;

/**
 * 묶음 알림 한 번의 결과.
 *
 * @param due 보낼 때가 된 연결 수
 * @param notified 주인에게 알림이 간 연결 수 (기기에 닿은 것)
 * @param skipped 알리지 않고 표시만 한 연결 수 (주인이 알림을 껐거나 연결이 끝남)
 */
public record ProxyDigestRunDto(int due, int notified, int skipped) {}
