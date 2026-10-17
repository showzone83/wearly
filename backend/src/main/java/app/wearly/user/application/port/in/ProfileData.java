package app.wearly.user.application.port.in;

import app.wearly.user.domain.UserProfile;

/** 프로필 읽기·쓰기 값: 회원 이름 + 프로필 (앱 Profile 모양은 웹 어댑터가 만든다) */
public record ProfileData(String name, UserProfile profile) {
}
