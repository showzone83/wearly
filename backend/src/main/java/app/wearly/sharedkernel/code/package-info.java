/**
 * 공유 커널: 코드 테이블 스냅샷(읽기 전용 값)과 코드 값 검증 정책, 코디 칸. 소유자는 code 컨텍스트다. 코드 테이블은 code가 관리하지만 옷장·프로필 검증, AI
 * 프롬프트·스키마, 코디 추천이 같은 값을 읽으므로 이름을 붙인 공유 커널로 둔다. 스냅샷은 code의 입력 포트 CodeCatalog로 얻는다.
 *
 * <p>여기에 넣을 수 있는 것: 불변 값, 조회 메서드, 두 컨텍스트 이상이 쓰는 정책. 넣지 않는 것: 저장·전송 형식, Spring·JPA·Jackson, 한 컨텍스트만
 * 쓰는 규칙. 공유 커널은 자바 표준과 common.domain만 본다 (ArchUnit).
 */
package app.wearly.sharedkernel.code;
