package app.wearly.outfit.domain;

import java.util.List;

/** 프롬프트에 주는 사용자 취향 (프로필의 드레스 코드·선호 스타일·선호 색, 코드 문자열) */
public record OutfitTaste(List<String> dressCodes, List<String> moods, List<String> colors) {}
