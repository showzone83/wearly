package app.wearly.admin.application.port.in.dto;

import app.wearly.ai.application.port.in.dto.ProviderTypeDto;
import java.util.List;

/** 지원 공급자 종류와 서버 설정 (GET /admin/ai/types) */
public record ProviderTypesDto(
    List<ProviderTypeDto> types, boolean baseUrlAllowed, boolean mockMode) {}
