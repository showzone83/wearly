package app.wearly.notify.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 푸시 토큰 등록 검증. 토큰은 Expo 푸시 토큰(ExponentPushToken[…] 또는 ExpoPushToken[…])만 받는다. 기기 ID는 앱이 처음 실행할 때 만든
 * 값이라 예정 clientId와 같은 글자 규칙을 쓴다.
 */
public final class PushTokenRules {

  public static final int MAX_TOKEN = 255;
  public static final int MAX_DEVICE_ID = 64;
  public static final String TOKEN_FORMAT = "ExponentPushToken[…]";

  private static final Pattern TOKEN =
      Pattern.compile("^Expo(nent)?PushToken\\[[^\\]\\s]{1,200}]$");
  private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9._:-]+$");
  private static final List<String> PLATFORMS =
      Arrays.stream(PushPlatform.values()).map(PushPlatform::wire).toList();

  private PushTokenRules() {}

  /** 등록 입력 검증. 틀린 칸을 모두 모아 돌려준다 (없으면 빈 목록) */
  public static List<FieldError> validate(String token, String platform, String deviceId) {
    List<FieldError> errors = new ArrayList<>();
    if (token == null || token.isBlank()) {
      errors.add(FieldError.required("token"));
    } else if (token.length() > MAX_TOKEN) {
      errors.add(FieldError.tooLong("token", MAX_TOKEN));
    } else if (!TOKEN.matcher(token.strip()).matches()) {
      errors.add(
          new FieldError("token", FieldErrorCode.INVALID_FORMAT, Map.of("format", TOKEN_FORMAT)));
    }
    if (platform == null || platform.isBlank()) {
      errors.add(FieldError.required("platform"));
    } else if (PushPlatform.parse(platform).isEmpty()) {
      errors.add(
          new FieldError("platform", FieldErrorCode.NOT_ALLOWED, Map.of("allowed", PLATFORMS)));
    }
    errors.addAll(validateDeviceId(deviceId));
    return errors;
  }

  /** 기기 ID 검증 (등록·삭제 같이 쓴다) */
  public static List<FieldError> validateDeviceId(String deviceId) {
    if (deviceId == null || deviceId.isBlank()) {
      return List.of(FieldError.required("deviceId"));
    }
    if (deviceId.length() > MAX_DEVICE_ID) {
      return List.of(FieldError.tooLong("deviceId", MAX_DEVICE_ID));
    }
    if (!DEVICE_ID.matcher(deviceId).matches()) {
      return List.of(new FieldError("deviceId", FieldErrorCode.INVALID_FORMAT));
    }
    return List.of();
  }
}
