package app.wearly.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 웹 입력 키 형식과 키 출처 우선순위 (07 문서 6장, Spring 없이) */
class ApiKeyRulesTest {

    @Test
    void 앞뒤_공백을_지우고_20에서_300자_공백_없음() {
        List<FieldError> errors = new ArrayList<>();
        assertThat(ApiKeyRules.normalize("  sk-0123456789abcdefghij \n", errors)).isEqualTo("sk-0123456789abcdefghij");
        assertThat(errors).isEmpty();
        assertThat(ApiKeyRules.normalize("short", errors)).isNull();
        assertThat(ApiKeyRules.normalize("sk-0123456789 abcdefghij", errors)).isNull();
        assertThat(ApiKeyRules.normalize("x".repeat(301), errors)).isNull();
        assertThat(ApiKeyRules.normalize(null, errors)).isNull();
        assertThat(errors).extracting(FieldError::field).containsOnly("apiKey").hasSize(4);
        assertThat(errors).extracting(FieldError::message).noneMatch(m -> m.contains("sk-"));
        assertThat(ApiKeyRules.last4("sk-0123456789abcdefghij")).isEqualTo("ghij");
    }

    @Test
    void DB_키가_환경변수보다_먼저고_둘_다_없으면_NONE() {
        Optional<String> env = Optional.of("env-key");
        assertThat(ApiKeyRules.choose(true, true, Optional.of("db-key"), env)).isEqualTo(
                new ApiKeyRules.Choice(KeySource.DB, "db-key"));
        assertThat(ApiKeyRules.choose(true, false, Optional.empty(), env).source()).isEqualTo(KeySource.ENV);
        assertThat(ApiKeyRules.choose(true, false, Optional.empty(), Optional.empty())).isEqualTo(ApiKeyRules.Choice.NONE);
    }

    @Test
    void 마스터_키가_없으면_DB_키는_보지_않고_복호화에_실패하면_쓸_수_없다() {
        Optional<String> env = Optional.of("env-key");
        assertThat(ApiKeyRules.choose(false, true, Optional.empty(), env).source()).isEqualTo(KeySource.ENV);
        assertThat(ApiKeyRules.choose(true, true, Optional.empty(), env).source()).isEqualTo(KeySource.NONE);
        assertThat(new ApiKeyRules.Choice(KeySource.DB, "secret-value").toString()).doesNotContain("secret-value");
    }
}
