package app.wearly.ai.domain;

/** 분석할 사진. 저장하지 않고 요청 동안만 메모리에 둔다. */
public record AiImage(byte[] bytes, String mediaType, String sha256) {}
