package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminCodesDto;
import java.util.List;

/** GET /admin/codes (07 문서 4장): /codes 모양 + 그룹 description, 코드별 usageCount */
public record AdminCodesResponse(String version, List<Group> groups) {

  public record Group(
      String group, String name, String description, List<AdminCodeResponse> codes) {}

  public static AdminCodesResponse from(AdminCodesDto d) {
    return new AdminCodesResponse(
        d.version(),
        d.groups().stream()
            .map(
                g ->
                    new Group(
                        g.group(),
                        g.name(),
                        g.description(),
                        g.codes().stream().map(AdminCodeResponse::from).toList()))
            .toList());
  }
}
