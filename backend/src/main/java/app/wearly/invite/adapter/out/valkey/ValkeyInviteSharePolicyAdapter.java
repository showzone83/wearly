package app.wearly.invite.adapter.out.valkey;

import app.wearly.invite.application.port.out.InviteSharePolicyPort;
import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSharePolicy.Gate;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * InviteSharePolicyPort 구현 (Valkey). 키 두 개를 스크립트 하나로 다뤄 동시에 와도 한도를 넘지 않는다.
 *
 * <ul>
 *   <li>wearly:invite:share:cool:&lt;회원&gt;:&lt;방법&gt; = 쿨다운 (cooldown 뒤 사라진다)
 *   <li>wearly:invite:share:day:&lt;날짜&gt;:&lt;회원&gt; = 그날 시도 수 (한도를 넘은 시도도 센다, 이틀 뒤 사라진다)
 * </ul>
 */
@Component
class ValkeyInviteSharePolicyAdapter implements InviteSharePolicyPort {

  static final String COOL_PREFIX = "wearly:invite:share:cool:";
  static final String DAY_PREFIX = "wearly:invite:share:day:";
  static final Duration KEEP = Duration.ofDays(2);

  /**
   * KEYS[1] = 쿨다운, KEYS[2] = 하루 수. ARGV[1] = 쿨다운 초, ARGV[2] = 하루 한도, ARGV[3] = 하루 수 유지 초. 돌려주는 값: 1
   * 기록, 0 쿨다운, -1 한도에 처음 걸림, -2 한도를 이미 넘음
   */
  static final RedisScript<Long> ACQUIRE =
      new DefaultRedisScript<>(
          """
          if redis.call('EXISTS', KEYS[1]) == 1 then
            return 0
          end
          local used = tonumber(redis.call('GET', KEYS[2]) or '0')
          redis.call('INCR', KEYS[2])
          redis.call('EXPIRE', KEYS[2], ARGV[3])
          if used >= tonumber(ARGV[2]) then
            if used == tonumber(ARGV[2]) then
              return -1
            end
            return -2
          end
          redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redis;

  ValkeyInviteSharePolicyAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Gate tryAcquire(
      long userId, InviteChannel channel, LocalDate day, Duration cooldown, int dailyLimit) {
    List<String> keys =
        List.of(COOL_PREFIX + userId + ":" + channel.name(), DAY_PREFIX + day + ":" + userId);
    Long r =
        redis.execute(
            ACQUIRE,
            keys,
            Long.toString(Math.max(1, cooldown.toSeconds())),
            Integer.toString(dailyLimit),
            Long.toString(KEEP.toSeconds()));
    if (r == null) {
      return Gate.RECORD;
    }
    return switch (r.intValue()) {
      case 1 -> Gate.RECORD;
      case 0 -> Gate.COOLDOWN;
      case -1 -> Gate.DAILY_LIMIT_FIRST;
      default -> Gate.DAILY_LIMIT;
    };
  }
}
