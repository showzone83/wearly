package app.wearly.user.application.port.in.dto;

import java.util.List;

/**
 * 다른 컨텍스트(코디 추천)가 읽는 취향 값. 코드 문자열 그대로 둔다.
 *
 * @param dressCodes 드레스 코드 (CASUAL · BIZ_CASUAL · FORMAL)
 * @param moods 선호 스타일 (STYLE 코드)
 * @param colors 선호 색 (COLOR 코드)
 */
public record ProfileTasteDto(List<String> dressCodes, List<String> moods, List<String> colors) {}
