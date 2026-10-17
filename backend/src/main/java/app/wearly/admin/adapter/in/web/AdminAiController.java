package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.request.ApiKeyRequest;
import app.wearly.admin.adapter.in.web.request.CreateProviderRequest;
import app.wearly.admin.adapter.in.web.request.ReauthRequest;
import app.wearly.admin.adapter.in.web.response.AiLogDetailResponse;
import app.wearly.admin.adapter.in.web.response.AiLogResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.adapter.in.web.response.ProviderModelsResponse;
import app.wearly.admin.adapter.in.web.response.ProviderResponse;
import app.wearly.admin.adapter.in.web.response.ProviderTestResultResponse;
import app.wearly.admin.adapter.in.web.response.TypesResponse;
import app.wearly.admin.application.port.in.AdminAiProviderUseCase;
import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.dto.AiLogSearchDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.ai.application.port.in.dto.PatchProviderDto;
import app.wearly.common.application.Patch;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatches;
import app.wearly.common.web.PathIds;
import app.wearly.common.web.Uploads;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 AI 공급자·분석 기록 API (07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

  private final AdminAiProviderUseCase providers;
  private final AdminDashboardUseCase logs;
  private final JsonMapper jsonMapper;

  public AdminAiController(
      AdminAiProviderUseCase providers, AdminDashboardUseCase logs, JsonMapper jsonMapper) {
    this.providers = providers;
    this.logs = logs;
    this.jsonMapper = jsonMapper;
  }

  @GetMapping("/providers")
  public List<ProviderResponse> providers() {
    return providers.list().stream().map(ProviderResponse::from).toList();
  }

  @PostMapping("/providers")
  @ResponseStatus(HttpStatus.CREATED)
  public ProviderResponse create(
      @CurrentUser long adminId, @RequestBody(required = false) CreateProviderRequest request) {
    return ProviderResponse.from(
        providers.create(adminId, request == null ? null : request.toDto()));
  }

  @PatchMapping("/providers/{providerId}")
  public ProviderResponse patch(
      @CurrentUser long adminId,
      @PathVariable String providerId,
      @RequestBody(required = false) JsonNode body) {
    return ProviderResponse.from(providers.patch(adminId, providerId, toPatch(body)));
  }

  @DeleteMapping("/providers/{providerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser long adminId, @PathVariable String providerId) {
    providers.delete(adminId, providerId);
  }

  /** 웹 키 넣기·바꾸기 (쓰기 전용, 07 문서 6장) */
  @PutMapping("/providers/{providerId}/api-key")
  public ProviderResponse setKey(
      @CurrentUser long adminId,
      @PathVariable String providerId,
      @RequestBody(required = false) ApiKeyRequest request) {
    ApiKeyRequest r = request == null ? ApiKeyRequest.EMPTY : request;
    return ProviderResponse.from(
        providers.setKey(adminId, providerId, r.apiKey(), r.currentPassword(), r.verify()));
  }

  /** 웹 키 지우기 (서버 설정 키로 되돌아감) */
  @DeleteMapping("/providers/{providerId}/api-key")
  public ProviderResponse deleteKey(
      @CurrentUser long adminId,
      @PathVariable String providerId,
      @RequestBody(required = false) ReauthRequest request) {
    return ProviderResponse.from(
        providers.deleteKey(
            adminId, providerId, request == null ? null : request.currentPassword()));
  }

  /**
   * 모델 드롭다운 목록. 키가 있으면 제공사 목록(LIVE, 10분 캐시, refresh=true면 다시 받기), 없거나 실패하면 내장 목록(CATALOG + error)
   */
  @GetMapping("/providers/{providerId}/models")
  public ProviderModelsResponse models(
      @PathVariable String providerId,
      @RequestParam(required = false, defaultValue = "false") boolean refresh) {
    return ProviderModelsResponse.from(providers.models(providerId, refresh));
  }

  @PostMapping(
      path = "/providers/{providerId}/test",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ProviderTestResultResponse test(
      @CurrentUser long adminId,
      @PathVariable String providerId,
      @RequestPart(name = "image", required = false) MultipartFile image) {
    return ProviderTestResultResponse.from(providers.test(adminId, providerId, Uploads.of(image)));
  }

  @GetMapping("/types")
  public TypesResponse types() {
    return TypesResponse.from(providers.types());
  }

  @GetMapping("/logs")
  public PageResponse<AiLogResponse> logs(
      @RequestParam(required = false) String providerId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String userEmail,
      @RequestParam(required = false) String feature,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        logs.aiLogs(
            new AiLogSearchDto(providerId, status, userEmail, feature, from, to),
            PagingDto.of(page, size)),
        AiLogResponse::from);
  }

  @GetMapping("/logs/{id}")
  public AiLogDetailResponse log(@PathVariable String id) {
    return AiLogDetailResponse.from(logs.aiLog(PathIds.parse(id)));
  }

  /** JSON → 공급자 부분 수정. 형식(JSON 타입) 판단과 그 오류 문구는 여기서 정한다 (M4) */
  private PatchProviderDto toPatch(JsonNode body) {
    if (!JsonPatches.isObject(body)) {
      return null;
    }
    Patch<Map<String, Object>> options =
        body.has("options") && body.get("options").isNull()
            ? Patch.of(Map.of())
            : JsonPatches.object(body, "options", jsonMapper, "JSON 객체여야 해요.");
    return new PatchProviderDto(
        JsonPatches.scalar(body, "providerId"),
        JsonPatches.text(body, "type"),
        JsonPatches.text(body, "displayName"),
        JsonPatches.text(body, "model"),
        JsonPatches.text(body, "apiKeyEnv"),
        JsonPatches.bool(body, "enabled", "true 또는 false여야 해요."),
        JsonPatches.integer(body, "priority", "0~10000 정수여야 해요."),
        options);
  }
}
