package app.wearly.admin.adapter.in.web.request;

/** PUT /admin/app/theme 본문 (12 문서 3장). 허용 값 검증은 유스케이스가 한다 */
public record ThemeRequest(String themeId) {}
