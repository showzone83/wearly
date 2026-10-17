package app.wearly.code.adapter.in.web.response;

import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.List;
import java.util.Map;

/** GET /api/v1/codes 응답 (06 문서 4장): { version, groups: [{ group, name, codes: [...] }] } */
public record CodesResponse(String version, List<Group> groups) {

  public record Group(String group, String name, List<Code> codes) {}

  public record Code(
      String code,
      String label,
      String parent,
      int sortOrder,
      boolean active,
      Map<String, Object> attrs) {}

  public static CodesResponse from(CodeSnapshot s) {
    return new CodesResponse(
        s.version(),
        s.groups().stream()
            .map(
                g ->
                    new Group(
                        g.group(), g.name(), g.codes().stream().map(CodesResponse::code).toList()))
            .toList());
  }

  private static Code code(CodeEntry c) {
    return new Code(c.code(), c.label(), c.parent(), c.sortOrder(), c.active(), c.attrs());
  }
}
