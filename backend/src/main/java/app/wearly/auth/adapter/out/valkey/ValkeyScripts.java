package app.wearly.auth.adapter.out.valkey;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 여러 키를 함께 바꾸는 동작을 원자적으로 하는 Lua 스크립트 (Valkey는 스크립트 하나를 다른 명령과 섞지 않고 실행한다). 세션 키처럼 토큰을 읽어야 알 수 있는 키는
 * 스크립트 안에서 접두사(ARGV)로 만든다 — 단일 노드 전제 (클러스터로 가면 해시 태그로 슬롯을 맞춰야 한다).
 */
final class ValkeyScripts {

  private ValkeyScripts() {}

  /**
   * 새 세션. KEYS: rt, session, userSessions, INDEX, EXPIRY. ARGV: tokenHash, userId, sessionId,
   * nowMs, expireAtMs, client, userAgent, ip
   */
  static final RedisScript<Long> CREATE =
      new DefaultRedisScript<>(
          """
          redis.call('HSET', KEYS[1], 'userId', ARGV[2], 'sessionId', ARGV[3],
            'status', 'ACTIVE', 'createdAt', ARGV[4])
          redis.call('PEXPIREAT', KEYS[1], ARGV[5])
          redis.call('HSET', KEYS[2], 'userId', ARGV[2], 'currentHash', ARGV[1], 'client', ARGV[6],
            'userAgent', ARGV[7], 'ip', ARGV[8], 'createdAt', ARGV[4], 'lastSeenAt', ARGV[4])
          redis.call('PEXPIREAT', KEYS[2], ARGV[5])
          redis.call('SADD', KEYS[3], ARGV[3])
          if redis.call('PEXPIRETIME', KEYS[3]) < tonumber(ARGV[5]) then
            redis.call('PEXPIREAT', KEYS[3], ARGV[5])
          end
          redis.call('ZADD', KEYS[4], ARGV[4], ARGV[3])
          redis.call('ZADD', KEYS[5], ARGV[5], ARGV[3])
          return 1
          """,
          Long.class);

  /**
   * 회전 (compare-and-set). 지금 토큰이 ACTIVE이고 세션의 현재 토큰일 때만 바꾼다. KEYS: oldRt, newRt, INDEX, EXPIRY.
   * ARGV: oldHash, newHash, nowMs, expireAtMs, prefix. @return 1 회전함, 0 조건이 맞지 않음
   */
  static final RedisScript<Long> ROTATE =
      new DefaultRedisScript<>(
          """
          local t = redis.call('HMGET', KEYS[1], 'status', 'sessionId', 'userId')
          if t[1] ~= 'ACTIVE' then return 0 end
          local sKey = ARGV[5] .. 'session:' .. t[2]
          if redis.call('HGET', sKey, 'currentHash') ~= ARGV[1] then return 0 end
          redis.call('HSET', KEYS[1], 'status', 'ROTATED', 'replacedBy', ARGV[2])
          redis.call('HSET', KEYS[2], 'userId', t[3], 'sessionId', t[2],
            'status', 'ACTIVE', 'createdAt', ARGV[3])
          redis.call('PEXPIREAT', KEYS[2], ARGV[4])
          redis.call('HSET', sKey, 'currentHash', ARGV[2], 'lastRefreshAt', ARGV[3],
            'lastSeenAt', ARGV[3])
          redis.call('PEXPIREAT', sKey, ARGV[4])
          local uKey = ARGV[5] .. 'user:' .. t[3] .. ':sessions'
          if redis.call('PEXPIRETIME', uKey) < tonumber(ARGV[4]) then
            redis.call('PEXPIREAT', uKey, ARGV[4])
          end
          redis.call('ZADD', KEYS[3], ARGV[3], t[2])
          redis.call('ZADD', KEYS[4], ARGV[4], t[2])
          return 1
          """,
          Long.class);

  /**
   * 세션 끊기. 지금 토큰을 REVOKED로 두고 세션·회원 집합·인덱스에서 지운다. KEYS: session, INDEX, EXPIRY. ARGV: sessionId,
   * prefix. @return 지운 세션 해시(HGETALL 모양, 없으면 빈 목록)
   */
  @SuppressWarnings("rawtypes")
  static final RedisScript<List> DELETE =
      new DefaultRedisScript<>(
          """
          local s = redis.call('HGETALL', KEYS[1])
          redis.call('ZREM', KEYS[2], ARGV[1])
          redis.call('ZREM', KEYS[3], ARGV[1])
          if #s == 0 then return s end
          local f = {}
          for i = 1, #s, 2 do f[s[i]] = s[i + 1] end
          local rtKey = ARGV[2] .. 'rt:' .. f['currentHash']
          if redis.call('HGET', rtKey, 'status') == 'ACTIVE' then
            redis.call('HSET', rtKey, 'status', 'REVOKED')
          end
          redis.call('DEL', KEYS[1])
          redis.call('SREM', ARGV[2] .. 'user:' .. f['userId'] .. ':sessions', ARGV[1])
          return s
          """,
          List.class);

  /**
   * 매 요청 인증. 세션이 있고 회원이 같으면 1, lastSeenAt이 간격보다 오래됐으면 고친다. KEYS: session, INDEX. ARGV: userId,
   * nowMs, intervalMs, sessionId
   */
  static final RedisScript<Long> TOUCH =
      new DefaultRedisScript<>(
          """
          local f = redis.call('HMGET', KEYS[1], 'userId', 'lastSeenAt')
          if f[1] ~= ARGV[1] then return 0 end
          local now = tonumber(ARGV[2])
          if now - tonumber(f[2] or '0') >= tonumber(ARGV[3]) then
            redis.call('HSET', KEYS[1], 'lastSeenAt', ARGV[2])
            redis.call('ZADD', KEYS[2], now, ARGV[4])
          end
          return 1
          """,
          Long.class);

  /** 만료된 세션을 두 인덱스에서 지운다. KEYS: INDEX, EXPIRY. ARGV: nowMs. @return 지운 수 */
  static final RedisScript<Long> PRUNE =
      new DefaultRedisScript<>(
          """
          local gone = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
          for _, sid in ipairs(gone) do
            redis.call('ZREM', KEYS[1], sid)
            redis.call('ZREM', KEYS[2], sid)
          end
          return #gone
          """,
          Long.class);
}
