package app.wearly.admin.adapter.in.web.request;

import java.util.List;

/** PUT /admin/codes/{group}/order: 그룹의 코드 전부를 원하는 순서로 */
public record OrderRequest(List<String> codes) {}
