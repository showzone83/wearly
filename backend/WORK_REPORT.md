# 백엔드 5차 작업 보고 (2026-10-17): 헥사고날 구조·입출력 모델 정리와 점검 항목 수정

대상: [08_백엔드_구조.md](../doc/08_백엔드_구조.md) 2장·7장(패키지 트리·입출력 모델), [11_백엔드_아키텍처_점검.md](../doc/11_백엔드_아키텍처_점검.md) H1·H2·M1~M9·L1~L10. API 계약(05·06·07·09 문서)과 관리자 사이트·앱이 기대하는 응답·저장 JSON 모양은 바꾸지 않았다. 4차 보고는 git 이력(`git show origin/dev:backend/WORK_REPORT.md`)에 있다.

## 1. 브랜치

- `feat/backend-structure`. 기준은 `origin/dev`의 `1d56f95`("백엔드 요청 흐름 문서(doc/10), 문서 목차에 05~11 추가"). 이 이름으로 푸시됐다(거부 없음). **PR은 만들지 않았다. main·dev에는 푸시하지 않았다.**
- 단계·항목마다 커밋하고 바로 푸시했다. 커밋 전마다 `git status --ignored backend/src`로 무시되는 소스가 없음을 확인했다(0건).
- 바뀐 곳: `backend/` 전부, `doc/10_백엔드_흐름.md`, `doc/11_백엔드_아키텍처_점검.md`(맨 위 절 추가, 본문 유지). `mobile/`·`harness/`·`admin/`·`doc/05`~`doc/09`는 건드리지 않았다.

커밋 (오래된 순):

| 커밋 | 내용 |
|---|---|
| `7ce39fb` · `b4f5f99` | 0단계: 응답 동일성 스냅샷 테스트. **리팩터링 전 코드(origin/dev)에서** 155개 요청의 응답을 기록 |
| `8d9acab` | 1단계: 패키지 이동·이름 변경 (동작 변경 없음) |
| `bdc446a` | 2단계: 요청 → 입력 DTO → 유스케이스 → 출력 DTO → 응답 분리 |
| `d6dda2e` | H1 코드 사용처 SPI |
| `ae333fd` | H2 아이템·회원 불변식을 도메인으로 |
| `8c24fb0` · `1928746` | M1 사진 분석 프롬프트·검증을 도메인으로 · L7 재시도 루프 하나로(`AiCallRunner`) |
| `cda945a` · `58616b5` · `66f4d2f` · `e6ce71d` | M2 공급자 모듈 · M3 관리자 조회 결과 record · M4 PATCH `Patch<T>` · M5 포트에서 flush 제거 |
| `4358aa3` · `a425788` | M6(·L10) 키 검증을 트랜잭션 밖으로 · M7(·L8) 한도 예약 + 동시성 테스트 |
| `dfeb1c3` · `ea0f42b` | M8 common 경계·공유 커널 · M9 서비스 단위 테스트 |
| `4eaf698` · `3072f8a` · `2391a34` · `4ed2867` · `1a9da78` · `d0751f2` · `9821b86` | L1 · L3 · L4 · L5 · L6 · L9 · L10 |
| `4deeb83` | 4단계: ArchUnit 규칙 추가 |
| `9619394` · (이 보고) | 5단계: 문서 |

각 단계·항목 커밋 전에 `LC_ALL=C.UTF-8 ./gradlew check`(Spotless·Checkstyle·전체 테스트·ArchUnit·응답 스냅샷)가 통과했다.

## 2. 단계별 요약

1. **패키지 이동** — 08 문서 2장 트리대로. JPA는 `adapter.out.persistence.jpa`(`*Entity`·`*JpaRepository`·`*JpaAdapter`), MyBatis는 `adapter.out.persistence.mybatis`(`*MybatisAdapter`, `mapper/*Mapper`, `dto/*RowDto`), 유스케이스 입출력은 `application.port.in.dto`(`*Dto`). XML 파일·namespace도 함께 옮겼다. 이름 변경표는 3장.
2. **입출력 모델** — 08 문서 7장대로 컨텍스트마다 `adapter.in.web.request`(`*Request`, Bean Validation·Jackson, `toDto()`)·`adapter.in.web.response`(`*Response`, `static from(dto)`)를 두었다. 컨트롤러는 항상 `*Response`(또는 그 목록·페이지)를 돌려준다. ID 문자열·밀리초 ISO 시각 변환(`Ids`·`Times`)은 응답 클래스에서만 한다. 관리자 MyBatis 결과는 `port.out.dto` record로. 감사 로그 before/after는 서비스가 Map으로 만들고(`AuditSnapshots`), 시각은 어댑터가 저장 직전에 밀리초 ISO 문자열로 바꿔 예전 저장 모양을 지킨다.
3. **점검 항목** — H → M → L 순서, 항목별 커밋. 처리 방법은 4장, 요약은 doc/11 맨 위 절.
4. **ArchUnit** — 12개 → 19개 규칙. 5장.
5. **문서** — `doc/10`(클래스·패키지·시퀀스 갱신, 모든 클래스 링크가 실제 파일인지 스크립트로 확인, 본문의 클래스 이름도 코드에 있는지 확인), `doc/11` 맨 위 "5차에서 처리한 항목" 절, `backend/README.md`(패키지 구조, 마이그레이션, 테스트 표), 이 보고.

## 3. 패키지 표 (컨텍스트별 전 → 후)

공통 규칙: `adapter.out.persistence.*` → `adapter.out.persistence.jpa.*`, `*Repository` → `*JpaRepository`, `*PersistenceAdapter` → `*JpaAdapter`, `adapter.out.query.*` → `adapter.out.persistence.mybatis.*`(매퍼는 `mapper/`, 행은 `dto/*RowDto`), 웹 DTO 묶음(`*Dtos`) → `request/*Request`·`response/*Response`, `port.in`의 명령·조회·결과 → `port.in.dto/*Dto`.

| 컨텍스트 | 전 | 후 |
|---|---|---|
| auth | `persistence.CredentialPersistenceAdapter`·`RefreshTokenPersistenceAdapter`·`*Repository`, `port.in.LoginCommand`·`SignupCommand`·`AuthResult`, `web.AuthDtos`, `common.config.SecurityConfig` | `persistence.jpa.CredentialJpaAdapter`·`RefreshTokenJpaAdapter`·`*JpaRepository`, `port.in.dto.LoginDto`·`SignupDto`·`AuthResultDto`, `web.request.{Signup,Login,Refresh,Logout}Request`, `web.response.AuthResponse`·`AuthUserResponse`, `auth.config.SecurityConfig`, `domain.EmailTakenException` |
| user | `persistence.UserPersistenceAdapter`·`ProfilePersistenceAdapter`, `port.in.UserAccount`·`ProfileData`·`ProfileTaste`, `web.ProfileDto`, `domain.DressCode` | `persistence.jpa.UserJpaAdapter`·`ProfileJpaAdapter`, `persistence.mybatis.ProfileMybatisAdapter`(+`mapper.ProfileMapper`), `port.in.dto.UserAccountDto`·`ProfileDto`·`ProfileTasteDto`·`UpdateProfileDto`, `web.request.ProfileRequest`, `web.response.ProfileResponse`·`UserResponse`, `adapter.in.code.ProfileCodeUsageSource`, `domain.UserStatus`·`UserRole`, `DressCode`는 `sharedkernel.code` |
| wardrobe | `persistence.ItemPersistenceAdapter`, `query.ItemQueryAdapter`·`WardrobeQueryMapper`·`ItemRow`·`CountRow`, `port.in.ItemView`·`ListItemsQuery`·`WardrobeStats`, `web.WardrobeDtos`·`JsonItemPatch` | `persistence.jpa.WardrobeItemJpaAdapter`, `persistence.mybatis.WardrobeItemMybatisAdapter`·`mapper.WardrobeItemMapper`·`dto.ItemRowDto`, `port.out.dto.KeyCountDto`·`CodeCountDto`, `port.in.dto.ItemDto`·`ItemSearchDto`·`CreateItemDto`·`PatchItemDto`·`WardrobeStatsDto`, `web.request.ItemRequest`·`BulkItemsRequest`·`PatchItemRequest`, `web.response.ItemResponse`·`ItemListResponse`·`WardrobeStatsResponse`, `adapter.in.code.ItemCodeUsageSource`, `domain.ItemRules` |
| code | `persistence.CodePersistenceAdapter`, `query.CodeSnapshotCache`·`CodeUsageQueryAdapter`·`CodeQueryMapper`, `port.out.CodeUsagePort`, `port.in.CodeInfo`·`CreateCodeCommand`, `common.domain.code.*` | `persistence.jpa.CodeJpaAdapter`, `persistence.mybatis.CodeSnapshotMybatisAdapter`·`mapper.CodeMapper`, `port.in.CodeUsageSource`(SPI, 사용 수 SQL은 wardrobe·user로), `port.in.dto.CodeDto`·`CreateCodeDto`·`PatchCodeDto`·`CodeChangeDto`, `web.response.CodesResponse`, 스냅샷·검증 정책은 `sharedkernel.code.*` |
| ai | `persistence.*PersistenceAdapter`·`TransactionHooksAdapter`, `query.AiUsageQueryAdapter`·`AiUsageMapper`·`AiLogRow`, `port.in.AnalyzeResult`·`JsonGenerationCommand`·`GeneratedJson`·`*Info` 등, `ai.SuggestionParser`, `common.config.ApplicationSettingsConfig`·`AiProviderFailureAnalyzer` | `persistence.jpa.AiCallLogJpaAdapter`·`AiProviderJpaAdapter`·`ProviderSecretJpaAdapter`·`AiReservationJpaAdapter`, `persistence.mybatis.AiUsageMybatisAdapter`·`mapper.AiQuotaMapper`, `out.transaction.TransactionHooksAdapter`, `out.ai.{AiProviderModule(s), claude/openai/gemini/*Module, JsonObjectParser}`, `port.in.dto.*Dto`, `port.in.AiProviderKeyUseCase`·`AiLimitExceededException`·`AiUnavailableException`, `service.AiCallRunner`·`AiQuotaService`, `domain.AiSuggestionValidator`·`AiJsonPrompt`·`MockSuggestions`, `web.response.AnalyzeResponse`·`SuggestionResponse`, `ai.config.AiConfig`·`AiProviderFailureAnalyzer` |
| admin | `persistence.AuditLogPersistenceAdapter`, `query.AdminQueryAdapter`, `port.out.AdminRows`, `port.in.AdminViews`·`Page`, `web.AdminDtos` | `persistence.jpa.AuditLogJpaAdapter`, `persistence.mybatis.AdminQueryMybatisAdapter`·`mapper.AdminQueryMapper`, `port.out.dto.*RowDto`·`*CountsDto`·`ProviderStatDto`, `port.in.dto.*Dto`(대시보드·로그·회원·코드·공급자·페이지), `web.request.*Request`(키·재인증·순서·코드·공급자), `web.response.*Response`, `service.AdminAiKeyWriter`·`AdminCodeOrderWriter`·`AuditSnapshots` |
| outfit | `port.in.RecommendOutfitCommand`·`OutfitRecommendation` | `port.in.dto.RecommendOutfitDto`·`OutfitRecommendationDto`·`OutfitCandidateDto`, `port.in.OutfitFeatures`, `web.request.RecommendOutfitRequest`, `web.response.OutfitRecommendationResponse`, `service.OutfitLogResults`, `outfit.config.OutfitConfig` |
| common | `domain`(FieldError·PatchBody·ImageUpload·Ids·Times·Zones·code.*), `web`(JsonPatchBody…), `config`(Security·ApplicationSettings 포함) | `domain`(FieldError·DomainValidationException·Zones), `application`(Patch·ImageUpload), `web`(Ids·Times·JsonPatches·Uploads·PathIds·CurrentUser), `config`(WearlyProperties·ClockConfig·CorsConfig만), `mybatis`(+LikePatterns), `persistence.PersistenceClock`, `transaction.AfterTransaction`. 새 최상위 `sharedkernel.code` |

MyBatis XML: `wardrobe/WardrobeQueryMapper.xml` → `WardrobeItemMapper.xml`, `code/CodeQueryMapper.xml` → `CodeMapper.xml`, `ai/AiUsageMapper.xml` → `AiQuotaMapper.xml`, 새 `user/ProfileMapper.xml`.

## 4. 점검 항목별 처리

| 항목 | 처리 | 방법 |
|---|---|---|
| H1 | 완료 | code가 `CodeUsageSource` SPI를 정하고 wardrobe·user의 입력 어댑터가 구현(자기 유스케이스 → 자기 MyBatis). code의 옷장·프로필 SQL 삭제, 역방향 의존 없음. `CodeUsageSourceIntegrationTest` |
| H2 | 완료 | `ItemRules`(아이템 불변식, Bean Validation과 같은 문구), `User`(이름·상태·역할, enum). 어기면 `DomainValidationException` → 400. `ItemRulesTest`·`UserTest` |
| M1 | 완료 | `AiModelPort.generateJson(prompt, image)`로 일반화, `AnalysisPrompt.request`·`AiSuggestionValidator`(도메인). 어댑터는 JSON 객체까지만. `AiSuggestionValidatorTest` |
| M2 | 대부분 | `AiProviderModule` + `AiProviderModules`(default 없는 switch). 새 공급자 = enum 상수 + 공급자 + 모듈 + case + CHECK 마이그레이션(전 6곳 + 1). `AiProviderModulesTest` |
| M3 | 완료 | `AdminRows` → `port.out.dto` record, XML은 생성자 resultMap |
| M4 | 완료 | `Patch<T>`(안 보냄·값·형식 오류) 하나. 웹 어댑터가 JSON을 바꾸고 유스케이스는 JSON을 모른다. `WardrobePatchIntegrationTest` |
| M5 | 완료 | 포트의 `flush()` 제거, JPA 어댑터가 저장·삭제 때 flush |
| M6 | 완료 | `setKey`는 트랜잭션 없이 확인·재인증·외부 검증, 저장·감사만 `AdminAiKeyWriter`(트랜잭션). 오류 순서 그대로. `AdminAiServiceTest`가 순서 확인 |
| M7 | 완료 | V6 예약 테이블 + advisory lock(`AiQuotaService`). 동시 12개 요청에서 정확히 3개 성공(`AiQuotaConcurrencyIntegrationTest`, 잠금을 빼면 8개 성공해 실패함을 확인한 뒤 되돌림) |
| M8 | 완료 | `sharedkernel.code`, `Slots`, 컨텍스트 설정은 `<ctx>.config`, `common`은 컨텍스트를 모른다(ArchUnit) |
| M9 | 완료 | 서비스 단위 테스트 8클래스 추가(가짜 포트): Auth, CodeManagement, AdminUser, AdminAi, AiCallRunner, AiQuota, AiJsonGeneration, Wardrobe |
| L1 | 완료 | `EmailTakenException`(도메인) → `AuthService`가 409. ArchUnit: 출력 어댑터는 `common.error`를 모른다 |
| L2 | 완료 | 2단계에서 관리자·코디 요청을 웹 Request로 |
| L3 | 완료 | 원래 검색어를 포트로, 이스케이프는 XML `<bind>` + `LikePatterns`. `LikePatternsTest` |
| L4 | 대부분 | 코디 기록 JSON은 `OutfitLogResults`로. `UserProfile.Notify.toMap/fromMap`은 기존 테스트(`UserProfileTest`)가 고정해 남김 |
| L5 | 완료 | `Slots`·`DressCode`·`Seasons`를 공유 커널로 |
| L6 | 완료 | V7: feature 형식 CHECK, `result_summary`(기존 행 채움). 기능 이름은 호출자 소유(`OutfitFeatures`), 요약은 호출자가 `Accepted.summary`로. `AiLogSummaryMigrationTest` |
| L7 | 완료 | `AiCallRunner` |
| L8 | 완료 | 목업 출력은 `Supplier`(목업 실행일 때만 계산) |
| L9 | 완료 | `PersistenceClock`으로 엔티티 시각도 주입한 `Clock` |
| L10 | 완료 | `reorder` 유스케이스 하나(`AdminCodeOrderWriter`), outfit은 타입 있는 예외로 구분, `AfterTransaction` 공유, 테스트 전용 조회는 테스트 쪽으로(`app.wearly.support.AiUsageMapper`·`AiLogRowDto`), 키 관리 포트는 단계 대신 동작(`checkKey`·`saveKey`·`deleteKey`) |

## 5. ArchUnit 규칙 (`HexagonalArchitectureTest`, 19개)

기존 12개(의존 방향, 컨텍스트 사이는 `port.in`만, `@RestController`·`@Service`·출력 포트 구현 위치 등)에 더하거나 좁힌 것:

| 규칙 | 내용 |
|---|---|
| `JPA_엔티티는_영속성_어댑터에만` (좁힘) | `@Entity`는 `*.adapter.out.persistence.jpa..`에만 |
| `MyBatis_매퍼는_MyBatis_어댑터의_mapper에만` (1단계에서 좁힘) | `@Mapper`는 `*.adapter.out.persistence.mybatis.mapper..`에만 |
| `검증_Jackson_어노테이션은_웹_어댑터에만` (새) | `jakarta.validation.constraints..`·`jakarta.validation.Valid`·`com.fasterxml.jackson.annotation..`은 `*.adapter.in.web..` 밖에서 쓰지 않는다. 예외: 시작 설정 검증 `common.config.WearlyProperties`. 저장용 JSON 변환은 어댑터가 `JsonMapper`로 한다(어노테이션 없음) |
| `컨트롤러는_응답_타입만_돌려준다` (새) | `@RestController`의 public 메서드 반환 타입은 `*.adapter.in.web.response` 타입, 그 `Collection`·`ResponseEntity`(타입 인자 확인, `ResponseEntity<Void>` 허용), 또는 `void` |
| `포트_DTO는_어댑터를_모른다` (새) | `port.in.dto`·`port.out.dto`는 `*.adapter..`에 의존하지 않는다 |
| `출력_포트_DTO는_입력_포트_DTO를_모른다` (새) | 08 문서 7장 표 |
| `공유_커널은_자바_표준과_common_domain만_쓴다` (새, M8) | |
| `common은_컨텍스트를_모른다` (새, M8) | `common..`·`sharedkernel..`은 컨텍스트 패키지에 의존하지 않는다 |
| `출력_어댑터는_API_오류를_모른다` (새, L1) | `*.adapter.out..`은 `common.error..`에 의존하지 않는다 |
| `도메인은_자바_표준과_common_domain만_쓴다` (넓힘) | 공유 커널(`sharedkernel..`)도 허용 |

**잡는지 확인**: 일부러 어긴 클래스 7개(`adapter.out.persistence`의 `@Entity`, `mybatis`의 `@Mapper`, `@JsonProperty`와 `ItemResponse` 필드를 가진 `port.in.dto` record, `port.in.dto`를 쓰는 `port.out.dto`, `ItemDto`·`List<ItemDto>`를 돌려주는 컨트롤러, 컨텍스트를 아는 `common` 클래스, `ApiException`을 던지는 JPA 어댑터)를 넣고 돌려 위 새·변경 규칙 10개가 모두 실패(위반 위치까지 출력)하는 것을 확인한 뒤 지웠다.

## 6. 마이그레이션 (기존 V1~V5는 고치지 않음)

| 파일 | 내용 |
|---|---|
| `V6__ai_usage_reservation.sql` | `tb_ai_usage_reservation(reservation_id, user_id FK ON DELETE CASCADE, feature, created_at)` + 인덱스. 진행 중 AI 호출 예약(M7) |
| `V7__ai_log_summary.sql` | `ck_ai_analysis_log_feature`를 목록 CHECK에서 형식 CHECK(`^[A-Z][A-Z0-9_]{1,29}$`)로, `tb_ai_analysis_log.result_summary TEXT` 추가, 기존 행은 4차 관리자 서비스와 같은 문구로 채움(L6) |

새 DB(빈 PostgreSQL 17 + pgvector)에서 V1~V7이 차례로 적용되는 것을 `bootRunMock`으로 확인했다(7장).

## 7. 테스트 결과

- 명령: `cd backend && LC_ALL=C.UTF-8 ./gradlew check` (Spotless·Checkstyle main/test·테스트 전체·ArchUnit). 마지막 실행: **BUILD SUCCESSFUL, 52클래스 222개 테스트, 실패 0, 건너뜀 0.**
- 기존 158개는 모두 통과한다. 기존 테스트 파일은 import·패키지·클래스 이름 변경만 고쳤다(`AiAnalyzeIntegrationTest`·`WardrobeIntegrationTest`·공급자 테스트 3개 등, `SuggestionParser` → `JsonObjectParser`, `WardrobeQueryMapper` → `WardrobeItemMapper`, `ItemRow` → `ItemRowDto`, `AiLogRow` → `AiLogRowDto`). `CodeChecksTest`는 공유 커널로 패키지만 옮겼다. 기대값은 바꾸지 않았다. 예외는 아키텍처 테스트(`HexagonalArchitectureTest`) 자체로, 규칙을 08 문서대로 좁히고 더했다.
- 새 테스트 64개: ArchUnit 규칙 7, 서비스 단위 테스트 36(8클래스), 응답 스냅샷 1, 도메인·어댑터 단위 12(`ItemRulesTest`·`UserTest`·`AiSuggestionValidatorTest`·`AiProviderModulesTest`·`LikePatternsTest`·`PersistenceClockTest`), 통합 8(`CodeUsageSourceIntegrationTest`·`WardrobePatchIntegrationTest`·`AiQuotaConcurrencyIntegrationTest`·`AiLogSummaryMigrationTest`).

## 8. 응답 동일성 확인

- **방법**: `ApiResponseSnapshotTest`(테스트 전용 PostgreSQL 컨테이너, 목업 AI + 가짜 공급자 HTTP 서버)가 155개 이름 붙인 요청(인증·회원·프로필·옷장·코드·사진 분석·코디 추천·관리자 대시보드·회원·코드·공급자·키·AI 기록·변경 이력, 오류 응답 포함)을 보내 상태 코드·주요 헤더(ETag·Cache-Control)·본문을 `src/test/resources/snapshots/api-responses.json`과 비교한다. 이 파일은 **리팩터링 전 코드(`origin/dev` 그대로)에서** 만들어 첫 커밋(`7ce39fb`)에 넣었고 이후 한 번도 고치지 않았다.
- **정규화**: 매번 달라지는 값만 자리표시자로 바꾼다 — 토큰, 시각(ISO-8601 UTC, 밀리초 3자리 또는 없음 형식만 인정하므로 형식이 바뀌면 실패), 코드 버전·ETag의 시각 부분, 소요 시간(`latencyMs`·`avgLatencyMs`), 오늘 날짜, 가짜 서버 주소. 문자열 ID, null 포함 여부, 메시지, 배열 순서는 그대로 비교한다. 객체 필드 순서는 보지 않는다. `fieldErrors`만 필드·메시지 순으로 정렬해 비교한다(일반 클래스 요청 DTO의 Bean Validation 오류 순서가 실행마다 달라질 수 있어서. 개수·내용은 그대로 비교).
- **결과**: 0단계부터 마지막 커밋까지 모든 단계에서 155개 모두 같았다. 감사 로그 before/after와 AI 기록 결과·요약은 관리자 API 응답(변경 이력 목록, AI 기록 목록·상세)으로 스냅샷에 들어 있어 저장 모양도 함께 확인된다. 그 밖에 기존 통합 테스트(변경 이력 내용·AI 기록 result)가 그대로 통과한다.
- **실제 서버 확인**: 새 DB에서 `./gradlew bootRunMock`으로 띄우고 curl로 가입, `/codes`, 아이템 등록·bulk·검색·PATCH 형식 오류·통계, 사진 분석, 코디 추천, 관리자 로그인·AI 기록(요약 표시)·코드 순서 바꾸기(새 version 응답)·키 저장(마스터 키 없음 409)·변경 이력을 호출해 모두 계약대로 응답하는 것을 확인했다.

## 9. 실행하지 못한 검증

- 실제 Claude·OpenAI·Gemini 키로 하는 호출(이 세션에 키 없음). 공급자 요청·응답 모양은 가짜 HTTP 서버 테스트로만 확인했다.
- 연동 하네스(`harness/`), 앱(`mobile/`), 관리자 사이트(`admin/`)를 이 백엔드에 붙여 보는 것. 응답 JSON이 같으므로 영향은 없을 것으로 보지만 실행하지 않았다.
- `docker compose up -d --build`(백엔드 이미지 빌드)와 Mac 환경. 서버는 리눅스 컨테이너의 Gradle `bootRunMock` + 별도 pgvector 컨테이너로만 띄웠다.
- 기존 운영·개발 DB(4차까지 데이터가 있는 DB)에 V6·V7 적용. 빈 DB와 테스트 DB에서만 적용했다. V7의 기존 행 채우기는 `AiLogSummaryMigrationTest`가 같은 SQL로 확인한다.

## 10. 계약·08 문서와 다르게 정한 점

API 계약(05·06·07·09)과 응답 JSON은 바꾸지 않았다. 아래는 08 문서(고치지 않음)와 다르게 한 점과 DB 쪽 변화다.

- **새 패키지**: `common.application`(`Patch`·`ImageUpload` — 유스케이스 입력에 쓰므로 `common.web`이 아니라 여기), `sharedkernel.code`(M8), `<ctx>.config`(컨텍스트 설정 빈), `adapter.in.code`(H1 SPI 구현), `ai.adapter.out.transaction`, `common.persistence`·`common.transaction`. 08 문서 2장 트리에는 없다. 08 문서에 반영할지 결정이 필요하다.
- **트랜잭션 분리용 `*Writer` 서비스**(`AdminAiKeyWriter`, `AdminCodeOrderWriter`): 같은 빈 안 호출로는 `@Transactional`이 걸리지 않아 별도 빈으로 뒀다.
- **입력 포트의 예외 타입**(`AiLimitExceededException`·`AiUnavailableException`·`RejectedOutputException`)과 기능 이름 상수(`AiFeatures`·`OutfitFeatures`)를 `port.in`에 둔다.
- **`ProfileRequest`·`ProfileResponse`는 record가 아닌 클래스**: 필드 이름 `notify`가 `Object.notify()`와 겹쳐 record로 만들 수 없다. `PatchItemRequest`는 JSON → `PatchItemDto` 변환기라 클래스.
- **시작 설정 검증**(`WearlyProperties`의 Bean Validation)은 ArchUnit 어노테이션 규칙의 예외로 명시했다.
- **기존 테스트가 고정한 API를 남긴 것**: `UserProfile.Notify.toMap/fromMap`(L4), `AiProviderType` enum과 검증(M2), 공급자 단위 테스트가 부르는 `analyze(image, codes)`는 `AiModelPort`의 default 메서드로 남겼다.
- **DB**: `tb_ai_analysis_log.feature`가 목록이 아니라 형식만 검사한다(새 기능에 마이그레이션이 필요 없다). `result_summary` 컬럼이 생겼고 관리자 AI 기록의 `resultSummary`는 이 컬럼을 읽는다(값은 예전과 같은 문구). `ck_ai_provider_type`은 그대로(공급자 종류는 여전히 DB가 막는다).
- **실패 기록**: 사진 분석의 실패 기록은 예전처럼 설정된 모델만 남기고 토큰은 남기지 않는다(JSON 생성 실패는 받은 모델·토큰을 남긴다). 예전 동작을 그대로 지킨 것이다.
- **응답 스냅샷의 `fieldErrors` 정렬**: 8장.

## 11. 로컬(Mac)에서 보강할 것

1. `docker compose up -d --build`로 전체를 띄워 V6·V7이 기존 개발 DB(`wearly-pgdata` 볼륨)에 적용되는지, 관리자 AI 기록 화면의 요약이 그대로 보이는지 확인.
2. `harness/` 연동 테스트 실행.
3. 앱(Expo)·관리자 사이트로 로그인·옷 등록·사진 분석·코디 추천·관리자 코드·공급자·키 화면 한 바퀴.
4. 실제 AI 키(서버 설정 키 또는 웹 입력 키)로 사진 분석·코디 추천·관리자 시험 분석.
5. 08 문서에 5차의 새 패키지(10장 첫 항목)를 반영할지 결정.
