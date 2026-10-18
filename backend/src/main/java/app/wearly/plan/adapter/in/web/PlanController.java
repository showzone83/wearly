package app.wearly.plan.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import app.wearly.plan.adapter.in.web.request.BulkPlansRequest;
import app.wearly.plan.adapter.in.web.request.PatchPlanRequest;
import app.wearly.plan.adapter.in.web.request.PlanRequest;
import app.wearly.plan.adapter.in.web.response.PlanBulkResponse;
import app.wearly.plan.adapter.in.web.response.PlanListResponse;
import app.wearly.plan.adapter.in.web.response.PlanResponse;
import app.wearly.plan.application.port.in.PlanUseCase;
import app.wearly.plan.application.port.in.dto.PlanSaveResultDto;
import app.wearly.plan.application.port.in.dto.PlanSearchDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 예정 코디 API (19 문서 "다음 단계": 예정 코디 서버 저장). 모두 로그인 필요, 남의 예정은 404 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

  private final PlanUseCase plans;
  private final JsonMapper jsonMapper;

  public PlanController(PlanUseCase plans, JsonMapper jsonMapper) {
    this.plans = plans;
    this.jsonMapper = jsonMapper;
  }

  /** from·to(YYYY-MM-DD, 선택, 양 끝 포함) */
  @GetMapping
  public PlanListResponse list(
      @CurrentUser long userId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return PlanListResponse.from(plans.list(new PlanSearchDto(userId, from, to)));
  }

  /** 새로 만들면 201, 같은 clientId의 예정을 고쳤으면 200 */
  @PostMapping
  public ResponseEntity<PlanResponse> create(
      @CurrentUser long userId, @RequestBody PlanRequest request) {
    PlanSaveResultDto r = plans.create(userId, request.toDto());
    return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(PlanResponse.from(r.plan()));
  }

  /** 기기에 있던 예정을 한 번에 (최대 100개, clientId 필수). 같은 clientId면 고친다 */
  @PostMapping("/bulk")
  public PlanBulkResponse bulk(@CurrentUser long userId, @RequestBody BulkPlansRequest request) {
    return PlanBulkResponse.from(plans.bulk(userId, request.toDto()));
  }

  @PatchMapping("/{id}")
  public PlanResponse patch(
      @CurrentUser long userId, @PathVariable String id, @RequestBody JsonNode body) {
    return PlanResponse.from(
        plans.patch(userId, PathIds.parse(id), PatchPlanRequest.read(body, jsonMapper)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUser long userId, @PathVariable String id) {
    plans.delete(userId, PathIds.parse(id));
  }
}
