package app.wearly.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.application.port.in.AiLimitExceededException;
import app.wearly.ai.application.port.in.AiUnavailableException;
import app.wearly.ai.application.port.in.JsonOutputHandler;
import app.wearly.ai.application.port.in.RejectedOutputException;
import app.wearly.ai.application.port.in.dto.GenerateJsonDto;
import app.wearly.ai.application.port.in.dto.GeneratedJsonDto;
import app.wearly.ai.application.port.out.AiModelFactoryPort;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.application.port.out.AiReservationPort;
import app.wearly.ai.application.port.out.AiUsagePort;
import app.wearly.ai.domain.AiCallRecord;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** JSON 생성의 한도 예약·해제와 목업 출력 (가짜 포트, 목업 실행, Spring 없이) */
class AiJsonGenerationServiceTest {

  final Set<Long> open = new HashSet<>();
  final List<AiCallRecord> records = new ArrayList<>();
  long success;
  int mockCalls;
  Map<String, Object> answer = Map.of("ok", "좋아요");

  final AiUsagePort usage =
      new AiUsagePort() {
        @Override
        public long countSuccess(long userId, String feature, Instant from, Instant to) {
          return success;
        }

        @Override
        public long countReservations(long userId, String feature, Instant since) {
          return open.size();
        }

        @Override
        public void lockQuota(long userId, String feature) {}
      };

  final AiReservationPort reservations =
      new AiReservationPort() {
        long next = 1;

        @Override
        public long reserve(long userId, String feature, Instant at) {
          open.add(next);
          return next++;
        }

        @Override
        public void release(long reservationId) {
          open.remove(reservationId);
        }

        @Override
        public void deleteOlderThan(long userId, String feature, Instant before) {}
      };

  /** 목업 공급자: 호출자가 넘긴 mockOutput을 그대로 돌려준다 */
  final AiModelFactoryPort factory =
      new AiModelFactoryPort() {
        @Override
        public AiModelPort create(AiProviderSetting setting, String apiKey) {
          throw new UnsupportedOperationException();
        }

        @Override
        public AiModelPort mock() {
          return new AiModelPort() {
            @Override
            public String name() {
              return "mock";
            }

            @Override
            public String model() {
              return null;
            }

            @Override
            public AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image) {
              return new AiJsonResult(prompt.mockOutput().get(), null, null, null);
            }
          };
        }
      };

  /** 목업 실행에서는 공급자 설정·키를 읽지 않는다 */
  final AiJsonGenerationService service =
      new AiJsonGenerationService(
          new AiProviderRegistry(null, factory, null, new AiSettings(true, 3, false)),
          new AiQuotaService(usage, reservations, Clock.systemUTC()),
          new AiCallRunner(records::add));

  final JsonOutputHandler<String> handler =
      json -> {
        if (!json.containsKey("ok")) {
          throw new RejectedOutputException("ok가 없어요.");
        }
        return new JsonOutputHandler.Accepted<>((String) json.get("ok"), json, "요약");
      };

  @Test
  void 성공하면_오늘_사용_수를_돌려주고_예약을_지운다() {
    success = 1;

    GeneratedJsonDto<String> r = service.generate(command(3), handler);

    assertThat(r.value()).isEqualTo("좋아요");
    assertThat(r.provider()).isEqualTo("mock");
    assertThat(r.usedToday()).isEqualTo(2L);
    assertThat(open).isEmpty();
    assertThat(mockCalls).isEqualTo(1);
    assertThat(records).singleElement().extracting(AiCallRecord::status).isEqualTo("SUCCESS");
  }

  @Test
  void 한도를_다_쓰면_공급자를_부르지_않는다() {
    success = 3;

    assertThatThrownBy(() -> service.generate(command(3), handler))
        .isInstanceOf(AiLimitExceededException.class);
    assertThat(mockCalls).isZero();
    assertThat(records).isEmpty();
  }

  @Test
  void 모든_공급자가_실패해도_예약을_지운다() {
    answer = Map.of("nope", 1);

    assertThatThrownBy(() -> service.generate(command(3), handler))
        .isInstanceOf(AiUnavailableException.class);
    assertThat(open).isEmpty();
    assertThat(records)
        .singleElement()
        .extracting(AiCallRecord::errorCode)
        .isEqualTo("REJECTED_OUTPUT");
  }

  @Test
  void 기능_이름은_형식만_본다() {
    assertThatThrownBy(() -> service.generate(command("outfit recommend", 3), handler))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.generate(command("NEW_FEATURE_2", 3), handler).value()).isEqualTo("좋아요");
    assertThat(records).extracting(AiCallRecord::feature).containsExactly("NEW_FEATURE_2");
  }

  private GenerateJsonDto command(int limit) {
    return command("OUTFIT_RECOMMEND", limit);
  }

  private GenerateJsonDto command(String feature, int limit) {
    return new GenerateJsonDto(
        5L,
        feature,
        limit,
        "plan",
        null,
        "프롬프트",
        Map.of("type", "object"),
        () -> {
          mockCalls++;
          return answer;
        });
  }
}
