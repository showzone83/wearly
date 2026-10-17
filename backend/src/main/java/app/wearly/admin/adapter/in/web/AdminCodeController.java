package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.request.CreateCodeRequest;
import app.wearly.admin.adapter.in.web.request.OrderRequest;
import app.wearly.admin.adapter.in.web.response.AdminCodeResponse;
import app.wearly.admin.adapter.in.web.response.AdminCodesResponse;
import app.wearly.admin.application.port.in.AdminCodeUseCase;
import app.wearly.code.application.port.in.dto.PatchCodeDto;
import app.wearly.common.application.Patch;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatches;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 코드 API (07 문서 4장 "코드"). ADMIN 전용 (SecurityConfig) */
@RestController
@RequestMapping("/api/v1/admin/codes")
public class AdminCodeController {

  private final AdminCodeUseCase service;
  private final JsonMapper jsonMapper;

  public AdminCodeController(AdminCodeUseCase service, JsonMapper jsonMapper) {
    this.service = service;
    this.jsonMapper = jsonMapper;
  }

  @GetMapping
  public AdminCodesResponse list() {
    return AdminCodesResponse.from(service.list());
  }

  @PostMapping("/{group}")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminCodeResponse create(
      @CurrentUser long adminId,
      @PathVariable String group,
      @RequestBody(required = false) CreateCodeRequest request) {
    return AdminCodeResponse.from(
        service.create(adminId, group, request == null ? null : request.toDto()));
  }

  /** /{group}/order가 /{group}/{code}보다 먼저 맞도록 PUT으로만 둔다 (PATCH·DELETE에는 order 경로가 없다) */
  @PutMapping("/{group}/order")
  public AdminCodesResponse reorder(
      @CurrentUser long adminId,
      @PathVariable String group,
      @RequestBody(required = false) OrderRequest request) {
    service.reorder(adminId, group, request == null ? null : request.codes());
    return AdminCodesResponse.from(service.list());
  }

  @PatchMapping("/{group}/{code}")
  public AdminCodeResponse patch(
      @CurrentUser long adminId,
      @PathVariable String group,
      @PathVariable String code,
      @RequestBody(required = false) JsonNode body) {
    return AdminCodeResponse.from(service.patch(adminId, group, code, toPatch(body)));
  }

  @DeleteMapping("/{group}/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @CurrentUser long adminId, @PathVariable String group, @PathVariable String code) {
    service.delete(adminId, group, code);
  }

  /** JSON → 코드 부분 수정. 형식(JSON 타입) 판단과 그 오류 문구는 여기서 정한다 (M4) */
  private PatchCodeDto toPatch(JsonNode body) {
    if (!JsonPatches.isObject(body)) {
      return null;
    }
    Patch<String> parent = Patch.absent();
    if (body.has("parent")) {
      JsonNode p = body.get("parent");
      // 문자열이 아닌 값은 빈 값으로 본다 (세부 종류면 "상위 종류가 필요해요")
      parent = Patch.of(p.isNull() ? null : p.isString() ? p.asString() : "");
    }
    return new PatchCodeDto(
        JsonPatches.scalar(body, "code"),
        JsonPatches.string(body, "label"),
        parent,
        JsonPatches.integer(body, "sortOrder", "정수여야 해요."),
        JsonPatches.bool(body, "active", "true 또는 false여야 해요."),
        JsonPatches.object(body, "attrs", jsonMapper, "JSON 객체여야 해요."));
  }
}
