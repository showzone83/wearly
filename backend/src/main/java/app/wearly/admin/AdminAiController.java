package app.wearly.admin;

import app.wearly.admin.AdminAiDtos.CreateProviderRequest;
import app.wearly.admin.AdminAiDtos.ProviderDto;
import app.wearly.admin.AdminAiDtos.TypesResponse;
import app.wearly.ai.AiAnalysisService;
import app.wearly.auth.CurrentUser;
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

/** 관리자 AI 공급자·분석 기록 API (07 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final AdminAiService service;
    private final AdminLogService logs;

    public AdminAiController(AdminAiService service, AdminLogService logs) {
        this.service = service;
        this.logs = logs;
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return service.list();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderDto create(@CurrentUser long adminId, @RequestBody(required = false) CreateProviderRequest request) {
        return service.create(adminId, request);
    }

    @PatchMapping("/providers/{providerId}")
    public ProviderDto patch(@CurrentUser long adminId, @PathVariable String providerId,
            @RequestBody(required = false) JsonNode body) {
        return service.patch(adminId, providerId, body);
    }

    @DeleteMapping("/providers/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long adminId, @PathVariable String providerId) {
        service.delete(adminId, providerId);
    }

    @PostMapping(path = "/providers/{providerId}/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiAnalysisService.TestResult test(@CurrentUser long adminId, @PathVariable String providerId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        return service.test(adminId, providerId, image);
    }

    @GetMapping("/types")
    public TypesResponse types() {
        return service.types();
    }

    @GetMapping("/logs")
    public PageResponse<AdminLogService.AiLogDto> logs(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return logs.aiLogs(providerId, status, userEmail, feature, from, to, PageResponse.paging(page, size));
    }

    @GetMapping("/logs/{id}")
    public AdminLogService.AiLogDetailDto log(@PathVariable String id) {
        return logs.aiLog(id);
    }
}
