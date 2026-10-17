package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.admin.application.port.in.dto.AdminProviderDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.ProviderStatDto;
import app.wearly.ai.application.port.in.AiProviderAdminUseCase;
import app.wearly.ai.application.port.in.AiProviderKeyUseCase;
import app.wearly.ai.application.port.in.dto.AiProviderDto;
import app.wearly.ai.application.port.in.dto.CreateProviderDto;
import app.wearly.ai.application.port.in.dto.KeyChangeDto;
import app.wearly.ai.application.port.in.dto.PatchProviderDto;
import app.wearly.ai.application.port.in.dto.ProviderCatalogDto;
import app.wearly.ai.application.port.in.dto.VerifiedKeyDto;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 웹 키 저장 순서 (M6): 공급자 확인(404) → 웹 키 저장소 확인(409) → 재인증(403) → 외부 검증(트랜잭션 밖) → 저장·감사(트랜잭션). 가짜 포트,
 * Spring 없이
 */
class AdminAiServiceTest {

  static final long ADMIN = 1L;

  final List<String> steps = new ArrayList<>();
  final List<Map<String, Object>> audits = new ArrayList<>();
  boolean secretStore = true;
  boolean keyValid = true;
  String keySource = "ENV";
  String keyLast4 = "abcd";

  final AiProviderAdminUseCase providers =
      new AiProviderAdminUseCase() {
        @Override
        public AiProviderDto get(String providerId) {
          steps.add("get " + providerId);
          if (!providerId.equals("openai")) {
            throw ApiException.notFound();
          }
          return provider();
        }

        @Override
        public List<AiProviderDto> list() {
          throw new UnsupportedOperationException();
        }

        @Override
        public AiProviderDto create(CreateProviderDto command) {
          throw new UnsupportedOperationException();
        }

        @Override
        public AiProviderDto patch(String providerId, PatchProviderDto patch) {
          throw new UnsupportedOperationException();
        }

        @Override
        public AiProviderDto delete(String providerId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public ProviderCatalogDto catalog() {
          throw new UnsupportedOperationException();
        }
      };

  final AiProviderKeyUseCase keys =
      new AiProviderKeyUseCase() {
        @Override
        public VerifiedKeyDto checkKey(String providerId, String apiKey, boolean verify) {
          steps.add("check verify=" + verify);
          if (!keyValid) {
            throw new ApiException(ErrorCode.API_KEY_INVALID);
          }
          return new VerifiedKeyDto(providerId, apiKey);
        }

        @Override
        public KeyChangeDto saveKey(VerifiedKeyDto key, long adminUserId) {
          steps.add("save by " + adminUserId);
          AiProviderDto before = provider();
          keySource = "DB";
          keyLast4 = key.key().substring(key.key().length() - 4);
          return new KeyChangeDto(before, provider());
        }

        @Override
        public Optional<KeyChangeDto> deleteKey(String providerId) {
          throw new UnsupportedOperationException();
        }
      };

  final AccountUseCase accounts =
      new AccountUseCase() {
        @Override
        public boolean passwordMatches(long userId, String rawPassword) {
          steps.add("reauth " + userId);
          return "admin1234".equals(rawPassword);
        }

        @Override
        public Optional<Long> findUserIdByEmail(String email) {
          throw new UnsupportedOperationException();
        }

        @Override
        public long createAccount(String email, String rawPassword, String name, String role) {
          throw new UnsupportedOperationException();
        }
      };

  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) -> {
        steps.add("audit " + action);
        audits.add(before);
        audits.add(after);
      };

  final AdminQueryPort queries =
      new UnsupportedAdminQueries() {
        @Override
        public List<ProviderStatDto> providerStats(Instant from, Instant to) {
          return List.of();
        }
      };

  final AdminAiService service =
      new AdminAiService(
          providers,
          keys,
          new AdminAiKeyWriter(keys, audit),
          null,
          null,
          accounts,
          queries,
          audit,
          Clock.systemUTC());

  @Test
  void 키를_검증한_뒤_저장하고_앞뒤_4자리만_감사_기록한다() {
    AdminProviderDto view = service.setKey(ADMIN, "openai", "sk-new-key-9876", "admin1234", null);

    assertThat(steps)
        .containsExactly(
            "get openai",
            "reauth 1",
            "check verify=true",
            "save by 1",
            "audit AI_PROVIDER_KEY_SET");
    assertThat(view.keySource()).isEqualTo("DB");
    assertThat(view.keyLast4()).isEqualTo("9876");
    assertThat(audits)
        .containsExactly(
            Map.of("keySource", "ENV", "keyLast4", "abcd"),
            Map.of("keySource", "DB", "keyLast4", "9876"));
  }

  @Test
  void 없는_공급자는_재인증_전에_404() {
    assertThatThrownBy(() -> service.setKey(ADMIN, "none", "sk-1234", "wrong", false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThat(steps).containsExactly("get none");
  }

  @Test
  void 웹_키_저장소가_꺼져_있으면_재인증_전에_409() {
    secretStore = false;

    assertThatThrownBy(() -> service.setKey(ADMIN, "openai", "sk-1234", "wrong", false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SECRET_KEY_MISSING));
    assertThat(steps).containsExactly("get openai");
  }

  @Test
  void 비밀번호가_틀리면_키를_검증하지_않는다() {
    assertThatThrownBy(() -> service.setKey(ADMIN, "openai", "sk-1234", "wrong", true))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REAUTH_FAILED));
    assertThat(steps).containsExactly("get openai", "reauth 1");
  }

  @Test
  void 검증에_실패하면_저장도_감사_기록도_하지_않는다() {
    keyValid = false;

    assertThatThrownBy(() -> service.setKey(ADMIN, "openai", "sk-1234", "admin1234", false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.API_KEY_INVALID));
    assertThat(steps).containsExactly("get openai", "reauth 1", "check verify=false");
    assertThat(audits).isEmpty();
  }

  private AiProviderDto provider() {
    return new AiProviderDto(
        "openai",
        "OPENAI",
        "OpenAI",
        "gpt",
        "OPENAI_API_KEY",
        true,
        true,
        1,
        Map.of(),
        true,
        keySource,
        keyLast4,
        null,
        null,
        secretStore);
  }
}
