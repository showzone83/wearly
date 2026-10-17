package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.AdminDtos.TypesResponse;
import app.wearly.admin.application.port.in.AdminAiProviderUseCase;
import app.wearly.admin.application.port.in.AdminDashboardUseCase;
import app.wearly.admin.application.port.in.AdminViews.AiLogDetailView;
import app.wearly.admin.application.port.in.AdminViews.AiLogSearch;
import app.wearly.admin.application.port.in.AdminViews.AiLogView;
import app.wearly.admin.application.port.in.AdminViews.ProviderView;
import app.wearly.admin.application.port.in.Page;
import app.wearly.ai.application.port.in.CreateProviderCommand;
import app.wearly.ai.application.port.in.ProviderTestResult;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatchBody;
import app.wearly.common.web.PathIds;
import app.wearly.common.web.Uploads;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public AdminAiController(AdminAiProviderUseCase providers, AdminDashboardUseCase logs, JsonMapper jsonMapper) {
        this.providers = providers;
        this.logs = logs;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/providers")
    public List<ProviderView> providers() {
        return providers.list();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderView create(@CurrentUser long adminId, @RequestBody(required = false) CreateProviderCommand request) {
        return providers.create(adminId, request);
    }

    @PatchMapping("/providers/{providerId}")
    public ProviderView patch(@CurrentUser long adminId, @PathVariable String providerId,
            @RequestBody(required = false) JsonNode body) {
        return providers.patch(adminId, providerId, new JsonPatchBody(body, jsonMapper));
    }

    @DeleteMapping("/providers/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long adminId, @PathVariable String providerId) {
        providers.delete(adminId, providerId);
    }

    @PostMapping(path = "/providers/{providerId}/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProviderTestResult test(@CurrentUser long adminId, @PathVariable String providerId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        return providers.test(adminId, providerId, Uploads.of(image));
    }

    @GetMapping("/types")
    public TypesResponse types() {
        return TypesResponse.from(providers.types());
    }

    @GetMapping("/logs")
    public Page<AiLogView> logs(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return logs.aiLogs(new AiLogSearch(providerId, status, userEmail, feature, from, to), Page.paging(page, size));
    }

    @GetMapping("/logs/{id}")
    public AiLogDetailView log(@PathVariable String id) {
        return logs.aiLog(PathIds.parse(id));
    }
}
