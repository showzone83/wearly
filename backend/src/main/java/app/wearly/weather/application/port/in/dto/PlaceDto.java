package app.wearly.weather.application.port.in.dto;

/**
 * 날씨를 고른 장소.
 *
 * @param regionCode REGION 코드. 좌표 요청이면 null
 * @param label REGION 이름, 좌표 요청이면 공급자가 알려 준 장소 이름(없으면 격자 키)
 * @param lat REGION 좌표, 좌표 요청이면 격자(소수 첫째 자리) 좌표
 */
public record PlaceDto(String regionCode, String label, double lat, double lon) {}
