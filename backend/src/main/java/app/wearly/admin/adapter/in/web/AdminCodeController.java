package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.AdminDtos.OrderRequest;
import app.wearly.admin.application.port.in.AdminCodeUseCase;
import app.wearly.admin.application.port.in.AdminViews.AdminCodeView;
import app.wearly.admin.application.port.in.AdminViews.AdminCodesView;
import app.wearly.code.application.port.in.CreateCodeCommand;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.JsonPatchBody;
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
    public AdminCodesView list() {
        return service.list();
    }

    @PostMapping("/{group}")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCodeView create(@CurrentUser long adminId, @PathVariable String group,
            @RequestBody(required = false) CreateCodeCommand request) {
        return service.create(adminId, group, request);
    }

    /** /{group}/order가 /{group}/{code}보다 먼저 맞도록 PUT으로만 둔다 (PATCH·DELETE에는 order 경로가 없다) */
    @PutMapping("/{group}/order")
    public AdminCodesView reorder(@CurrentUser long adminId, @PathVariable String group,
            @RequestBody(required = false) OrderRequest request) {
        service.reorder(adminId, group, request == null ? null : request.codes());
        return service.list();
    }

    @PatchMapping("/{group}/{code}")
    public AdminCodeView patch(@CurrentUser long adminId, @PathVariable String group, @PathVariable String code,
            @RequestBody(required = false) JsonNode body) {
        return service.patch(adminId, group, code, new JsonPatchBody(body, jsonMapper));
    }

    @DeleteMapping("/{group}/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long adminId, @PathVariable String group, @PathVariable String code) {
        service.delete(adminId, group, code);
    }
}
