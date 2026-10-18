package app.wearly.notify.application.port.out.dto;

/** 보낼 곳 한 행: 회원의 기기 하나 (platform은 DB 값 IOS · ANDROID) */
public record PushTargetRowDto(Long userId, String deviceId, String platform, String token) {}
