package app.wearly.user.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.code.CodeChecks;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.common.error.ApiException;
import app.wearly.user.application.port.in.ProfileData;
import app.wearly.user.application.port.in.ProfileTaste;
import app.wearly.user.application.port.in.ProfileUseCase;
import app.wearly.user.application.port.out.ProfilePort;
import app.wearly.user.application.port.out.UserPort;
import app.wearly.user.domain.User;
import app.wearly.user.domain.UserProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService implements ProfileUseCase {

  private final UserPort users;
  private final ProfilePort profiles;
  private final CodeCatalog catalog;

  public ProfileService(UserPort users, ProfilePort profiles, CodeCatalog catalog) {
    this.users = users;
    this.profiles = profiles;
    this.catalog = catalog;
  }

  @Override
  @Transactional(readOnly = true)
  public ProfileData get(long userId) {
    User user = users.findById(userId).orElseThrow(ApiException::notFound);
    return new ProfileData(
        user.name(), profiles.findByUserId(userId).orElseGet(UserProfile::defaults));
  }

  @Override
  @Transactional(readOnly = true)
  public ProfileTaste taste(long userId) {
    UserProfile p = get(userId).profile();
    return new ProfileTaste(
        p.dressCodes().stream().map(Enum::name).toList(), p.moods(), p.colors());
  }

  @Override
  @Transactional
  public ProfileData put(long userId, ProfileData data) {
    final User user = users.findById(userId).orElseThrow(ApiException::notFound);
    Optional<UserProfile> saved = profiles.findByUserId(userId);
    UserProfile in = data.profile();
    // 새로 고르는 색·취향은 활성 코드만. 이미 저장돼 있던 값은 비활성이어도 둔다
    CodeSnapshot codes = catalog.current();
    List<FieldError> errors = new ArrayList<>();
    CodeChecks.many(
        codes,
        CodeGroups.STYLE,
        "moods",
        in.moods(),
        saved.map(UserProfile::moods).orElse(null),
        errors);
    CodeChecks.many(
        codes,
        CodeGroups.COLOR,
        "colors",
        in.colors(),
        saved.map(UserProfile::colors).orElse(null),
        errors);
    ApiException.throwIfAny(errors);
    user.rename(data.name().strip());
    users.save(user);
    UserProfile profile = in.normalized();
    profiles.save(userId, profile);
    return new ProfileData(user.name(), profile);
  }
}
