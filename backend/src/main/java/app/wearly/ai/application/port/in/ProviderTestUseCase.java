package app.wearly.ai.application.port.in;

import app.wearly.common.domain.ImageUpload;

/**
 * 관리자 시험 분석: 사용 여부와 관계없이 그 공급자로 한 번 호출한다. 일일 한도에 세지 않고 feature='ADMIN_TEST'로 기록한다. 목업 실행이어도 DB 행의
 * 실제 공급자를 부른다. 공급자가 없으면 404
 */
public interface ProviderTestUseCase {

  ProviderTestResult test(long adminUserId, String providerId, ImageUpload image);
}
