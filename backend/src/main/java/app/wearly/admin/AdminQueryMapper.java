package app.wearly.admin;

import app.wearly.admin.AdminRows.AiCounts;
import app.wearly.admin.AdminRows.AiLogRow;
import app.wearly.admin.AdminRows.AuditRow;
import app.wearly.admin.AdminRows.KeyCount;
import app.wearly.admin.AdminRows.ProviderStat;
import app.wearly.admin.AdminRows.UserRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 화면 읽기 전용 조회·집계 (MyBatis). SQL은 resources/mapper/AdminQueryMapper.xml */
@Mapper
public interface AdminQueryMapper {

    // 대시보드
    long countUsers();

    long countUsersSince(@Param("since") Instant since);

    long countItems();

    /** [from, to) AI 호출 수 (모든 기능) */
    AiCounts aiCounts(@Param("from") Instant from, @Param("to") Instant to);

    /** [from, to) 공급자별 호출 통계 (모든 기능) */
    List<ProviderStat> providerStats(@Param("from") Instant from, @Param("to") Instant to);

    // 코드 사용 수
    /** 그 그룹의 코드를 쓰는 아이템 수 (seasons·styles는 배열을 펼쳐 센다) */
    List<KeyCount> itemUsage(@Param("group") String group);

    /** 프로필이 쓰는 수 (COLOR → colors, STYLE → moods). 다른 그룹은 빈 목록 */
    List<KeyCount> profileUsage(@Param("group") String group);

    // 회원
    List<UserRow> findUsers(@Param("q") String qPattern, @Param("status") String status, @Param("role") String role,
            @Param("from") Instant todayFrom, @Param("to") Instant todayTo, @Param("offset") int offset,
            @Param("limit") int limit);

    UserRow findUser(@Param("id") long id, @Param("from") Instant todayFrom, @Param("to") Instant todayTo);

    long countUsersFiltered(@Param("q") String qPattern, @Param("status") String status, @Param("role") String role);

    // AI 분석 기록
    List<AiLogRow> findAiLogs(@Param("f") AiLogFilter filter, @Param("offset") int offset, @Param("limit") int limit);

    long countAiLogs(@Param("f") AiLogFilter filter);

    AiLogRow findAiLog(@Param("id") long id);

    // 변경 이력
    List<AuditRow> findAuditLogs(@Param("targetType") String targetType, @Param("offset") int offset,
            @Param("limit") int limit);

    long countAuditLogs(@Param("targetType") String targetType);

    /** AI 기록 검색 조건. 값은 이미 검증된 것 (userEmailPattern은 LIKE 이스케이프한 '%검색어%'). MyBatis OGNL이 getter로 읽는다 */
    final class AiLogFilter {

        private final String providerId;
        private final String status;
        private final String feature;
        private final String userEmailPattern;
        private final Instant from;
        private final Instant to;

        public AiLogFilter(String providerId, String status, String feature, String userEmailPattern, Instant from,
                Instant to) {
            this.providerId = providerId;
            this.status = status;
            this.feature = feature;
            this.userEmailPattern = userEmailPattern;
            this.from = from;
            this.to = to;
        }

        public String getProviderId() {
            return providerId;
        }

        public String getStatus() {
            return status;
        }

        public String getFeature() {
            return feature;
        }

        public String getUserEmailPattern() {
            return userEmailPattern;
        }

        public Instant getFrom() {
            return from;
        }

        public Instant getTo() {
            return to;
        }
    }
}
