package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import java.util.List;

/**
 * 공급자 종류 하나의 어댑터 묶음 (M2): 클라이언트 만들기와 키 확인. 새 공급자를 더하려면 도메인 AiProviderType에 상수(설명·options)를, 여기에 모듈
 * 클래스 하나를, AiProviderModules의 switch에 한 줄을 더한다 (빠뜨리면 컴파일 오류). DB CHECK(ck_ai_provider_type)를 쓰므로
 * 마이그레이션도 하나 필요하다.
 */
public interface AiProviderModule {

  AiProviderType type();

  /**
   * @param key 고른 키 (DB 또는 서버 설정). 로그·예외에 남기지 않는다
   */
  AiModelPort create(AiProviderSetting setting, String key, ProviderOptions options);

  /**
   * 각 사의 모델 목록 조회로 키를 확인한다 (사진 분석보다 싸다).
   *
   * @throws app.wearly.ai.domain.AiProviderException 거부·오류·시간 초과. 메시지에는 키 조각을 남기지 않는다
   */
  void verify(String key, ProviderOptions options);

  /**
   * 각 사의 모델 목록에서 사진 분석에 쓸 수 있는 모델만 (관리자 화면 모델 드롭다운, ModelListRules로 거른다).
   *
   * @throws app.wearly.ai.domain.AiProviderException 거부·오류·시간 초과·응답 형식 오류(BAD_RESPONSE). 메시지에는 키
   *     조각을 남기지 않는다
   */
  List<AiModelEntry> listModels(String key, ProviderOptions options);
}
