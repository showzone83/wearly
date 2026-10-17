package app.wearly.admin.application.port.out.dto;

/** 회원 요약 한 행 (로그인 세션 목록에 붙이는 이메일·이름·역할) */
public record UserBriefRowDto(Long id, String email, String name, String role) {}
