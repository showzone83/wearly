package app.wearly.image.domain;

import java.time.Duration;

/**
 * 사진 저장 설정 (wearly.storage.*, image.config.ImageConfig가 만든다). 유스케이스는 Spring 설정 클래스를 직접 보지 않는다.
 *
 * @param uploadUrlTtl 업로드(PUT) 서명 URL 수명
 * @param downloadUrlTtl 받기(GET) 서명 URL 수명
 * @param maxUploadBytes 업로드 한 장 최대 바이트
 * @param dailyUploadLimit 회원별 하루(Asia/Seoul) 업로드 URL 발급 수
 * @param staleAfter 정리 작업이 오래된 이미지로 보는 시간
 */
public record ImageSettings(
    Duration uploadUrlTtl,
    Duration downloadUrlTtl,
    long maxUploadBytes,
    int dailyUploadLimit,
    Duration staleAfter) {}
