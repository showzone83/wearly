package app.wearly.admin;

import java.time.Instant;
import java.util.Map;

/** 관리자 화면용 MyBatis 조회 결과 (setter 없이 필드로 매핑한다) */
public final class AdminRows {

    private AdminRows() {
    }

    public static class Counts {
        public long users;
        public long newUsers7d;
        public long items;
    }

    public static class AiCounts {
        public long total;
        public long success;
        public long failed;
    }

    public static class ProviderStat {
        public String providerId;
        public long success;
        public long failed;
        public Long avgLatencyMs;
        public Long inputTokens;
        public Long outputTokens;
    }

    public static class UserRow {
        public Long id;
        public String email;
        public String name;
        public String role;
        public String status;
        public Instant createdAt;
        public long itemCount;
        public long aiToday;
    }

    public static class AiLogRow {
        public Long id;
        public Instant createdAt;
        public Long userId;
        public String userEmail;
        public String feature;
        public String providerId;
        public String model;
        public String status;
        public int latencyMs;
        public Integer inputTokens;
        public Integer outputTokens;
        public Integer imageBytes;
        public String imageSha256;
        public String errorCode;
        public Map<String, Object> result;
    }

    public static class AuditRow {
        public Long id;
        public Long adminUserId;
        public String adminEmail;
        public String action;
        public String targetType;
        public String targetId;
        public Map<String, Object> before;
        public Map<String, Object> after;
        public Instant createdAt;
    }
}
