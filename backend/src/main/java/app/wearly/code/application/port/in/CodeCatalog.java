package app.wearly.code.application.port.in;

import app.wearly.common.domain.code.CodeSnapshot;

/**
 * 코드 테이블 조회 (06 문서 4장 "캐시"). 다른 컨텍스트(옷장·프로필 검증, AI 프롬프트, 관리자)도 이 포트로 읽는다.
 * 서버는 코드를 메모리에 캐시하고, 관리자 API로 바뀌면 커밋 직후 다시 읽는다.
 */
public interface CodeCatalog {

    /** 현재 스냅샷 (불변) */
    CodeSnapshot current();
}
