package app.wearly.ai.application.port.in;

import app.wearly.ai.application.port.in.dto.KeyChangeDto;
import app.wearly.ai.application.port.in.dto.VerifiedKeyDto;
import java.util.Optional;

/**
 * 공급자 웹 입력 키 (07 문서 6장). 키 확인(외부 HTTP)과 저장(DB)을 나눠서, 확인이 DB 트랜잭션 밖에서 돌게 한다 (M6). 비밀번호 재확인 같은 관리자
 * 정책과 오류 순서(404 → 409 → 403 → 400 → 400)는 부르는 쪽이 정한다.
 */
public interface AiProviderKeyUseCase {

  /**
   * 키 형식을 확인하고 verify면 각 사의 모델 목록 조회로 확인한다. 트랜잭션을 열지 않는다. 공급자가 없으면 404, 마스터 키가 없으면 409
   * SECRET_KEY_MISSING, 형식이 틀리면 400 VALIDATION_FAILED(apiKey), 확인 실패는 400 API_KEY_INVALID.
   */
  VerifiedKeyDto checkKey(String providerId, String apiKey, boolean verify);

  /**
   * 확인한 키를 암호화해 저장한다 (부르는 쪽 트랜잭션에 들어간다). 그사이 공급자가 지워졌으면 404.
   *
   * @param adminUserId 넣은 관리자 (updated_by)
   */
  KeyChangeDto saveKey(VerifiedKeyDto key, long adminUserId);

  /** 웹 키 지우기 (서버 설정 키로 되돌아감). 지운 행이 없으면 빈 값. 공급자가 없으면 404 */
  Optional<KeyChangeDto> deleteKey(String providerId);
}
