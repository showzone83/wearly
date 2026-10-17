/**
 * 코드 테이블 스냅샷(읽기 전용 값)과 코드 값 검증 규칙.
 * 코드 테이블 자체는 code 컨텍스트가 관리하지만, 옷장·프로필 검증과 AI 프롬프트·스키마가 같은 스냅샷을 읽으므로
 * 여러 컨텍스트가 같이 쓰는 값 객체로 여기에 둔다 (08 문서 2장 common.domain). 스냅샷은 code의 입력 포트 CodeCatalog로 얻는다.
 */
package app.wearly.common.domain.code;
