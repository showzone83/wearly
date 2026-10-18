package app.wearly.notify.application.port.in.dto;

import app.wearly.notify.domain.PushPlatform;
import java.time.LocalTime;

/**
 * 푸시 토큰 등록 결과.
 *
 * @param serverPush 서버가 이 기기에 D-1 알림을 실제로 보내는지 (wearly.notify.push.enabled=true이고 provider=expo).
 *     true면 앱은 로컬 D-1 알림을 끈다
 * @param dayBeforeTime 서버 D-1 작업 시각 (한국 시간)
 */
public record PushRegistrationDto(
    String deviceId, PushPlatform platform, boolean serverPush, LocalTime dayBeforeTime) {}
