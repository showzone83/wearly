package app.wearly.admin;

import app.wearly.admin.AdminDtos.CodeCreateRequest;
import app.wearly.admin.AdminDtos.CodeOrderRequest;
import app.wearly.auth.CurrentUser;
import app.wearly.code.CodeDtos.CodeDto;
import app.wearly.code.CodeDtos.CodesResponse;
import java.util.List;
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

/** 관리자 코드 관리 (ADMIN 전용, SecurityConfig) */
@RestController
@RequestMapping("/api/v1/admin/codes")
public class AdminCodeController {

    private final AdminCodeService service;

    public AdminCodeController(AdminCodeService service) {
        this.service = service;
    }

    @GetMapping
    public CodesResponse list() {
        return service.list();
    }

    @PostMapping("/{group}")
    @ResponseStatus(HttpStatus.CREATED)
    public CodeDto create(@CurrentUser long adminId, @PathVariable String group, @RequestBody CodeCreateRequest body) {
        return service.create(adminId, group, body);
    }

    /** /order가 코드 이름보다 먼저 잡히도록 PUT만 받는다 (코드 PATCH·DELETE와 메서드가 다름) */
    @PutMapping("/{group}/order")
    public List<CodeDto> reorder(@CurrentUser long adminId, @PathVariable String group,
            @RequestBody CodeOrderRequest body) {
        return service.reorder(adminId, group, body.codes());
    }

    @PatchMapping("/{group}/{code}")
    public CodeDto patch(@CurrentUser long adminId, @PathVariable String group, @PathVariable String code,
            @RequestBody JsonNode body) {
        return service.patch(adminId, group, code, body);
    }

    @DeleteMapping("/{group}/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long adminId, @PathVariable String group, @PathVariable String code) {
        service.delete(adminId, group, code);
    }
}
