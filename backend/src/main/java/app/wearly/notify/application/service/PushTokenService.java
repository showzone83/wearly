package app.wearly.notify.application.service;

import app.wearly.common.error.BusinessException;
import app.wearly.notify.application.port.in.PushTokenUseCase;
import app.wearly.notify.application.port.in.dto.PushRegistrationDto;
import app.wearly.notify.application.port.in.dto.RegisterPushTokenDto;
import app.wearly.notify.application.port.out.PushTokenPort;
import app.wearly.notify.domain.PushPlatform;
import app.wearly.notify.domain.PushSettings;
import app.wearly.notify.domain.PushTokenRules;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 푸시 토큰 등록·삭제 (JPA) */
@Service
public class PushTokenService implements PushTokenUseCase {

  private final PushTokenPort tokens;
  private final PushSettings settings;
  private final Clock clock;

  public PushTokenService(PushTokenPort tokens, PushSettings settings, Clock clock) {
    this.tokens = tokens;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PushRegistrationDto register(long userId, RegisterPushTokenDto input) {
    BusinessException.throwIfAny(
        PushTokenRules.validate(input.token(), input.platform(), input.deviceId()));
    PushPlatform platform = PushPlatform.parse(input.platform()).orElseThrow();
    tokens.upsert(userId, input.deviceId(), platform, input.token().strip(), clock.instant());
    return new PushRegistrationDto(
        input.deviceId(), platform, settings.deliversToDevices(), settings.dayBeforeTime());
  }

  @Override
  @Transactional
  public void unregister(long userId, String deviceId) {
    BusinessException.throwIfAny(PushTokenRules.validateDeviceId(deviceId));
    tokens.delete(userId, deviceId);
  }

  @Override
  @Transactional
  public void unregisterAll(long userId) {
    tokens.deleteAllOf(userId);
  }
}
