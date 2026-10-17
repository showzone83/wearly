package app.wearly.admin;

import app.wearly.admin.AdminDtos.AiLogDetail;
import app.wearly.admin.AdminDtos.AiLogItem;
import app.wearly.admin.AdminDtos.PageResponse;
import app.wearly.admin.AdminDtos.ProviderCreateRequest;
import app.wearly.admin.AdminDtos.ProviderDto;
import app.wearly.admin.AdminDtos.ProviderTestResult;
import app.wearly.admin.AdminDtos.ProviderTypeInfo;
import app.wearly.auth.CurrentUser;
import app.wearly.common.Ids;
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

/** 관리자 AI 공급자·분석 기록 (ADMIN 전용) */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final AdminAiProviderService providers;
    private final AdminReportService reports;

    public AdminAiController(AdminAiProviderService providers, AdminReportService reports) {
        this.providers = providers;
        this.reports = reports;
    }

    @GetMapping("/providers")
    public List<ProviderDto> list() {
        return providers.list();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderDto create(@CurrentUser long adminId, @RequestBody ProviderCreateRequest body) {
        return providers.create(adminId, body);
    }

    @PatchMapping("/providers/{providerId}")
    public ProviderDto patch(@CurrentUser long adminId, @PathVariable String providerId, @RequestBody JsonNode body) {
        return providers.patch(adminId, providerId, body);
    }

    @DeleteMapping("/providers/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long adminId, @PathVariable String providerId) {
        providers.delete(adminId, providerId);
    }

    @PostMapping(path = "/providers/{providerId}/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProviderTestResult test(@CurrentUser long adminId, @PathVariable String providerId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        return providers.test(adminId, providerId, image);
    }

    @GetMapping("/types")
    public List<ProviderTypeInfo> types() {
        return providers.types();
    }

    @GetMapping("/logs")
    public PageResponse<AiLogItem> logs(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return reports.aiLogs(providerId, status, feature, userEmail, from, to, Paging.of(page, size));
    }

    @GetMapping("/logs/{id}")
    public AiLogDetail log(@PathVariable String id) {
        return reports.aiLog(Ids.parse(id));
    }
}
