package app.wearly.ai.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

/** POST /wardrobe/items/analyze JSON 본문 (18 문서): 서버에 저장한 내 사진(READY)으로 분석 */
public record AnalyzeStoredImageRequest(@NotBlank String imageId) {}
