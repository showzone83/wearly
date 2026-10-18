package app.wearly.user.adapter.out.persistence.jpa;

import app.wearly.sharedkernel.code.DressCode;
import app.wearly.user.application.port.out.ProfilePort;
import app.wearly.user.domain.EnumCodes;
import app.wearly.user.domain.UserProfile;
import app.wearly.user.domain.WorkType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** ProfilePort 구현 (JPA). 열거형은 문자열, 근무 요일은 SMALLINT[]로 저장한다 */
@Component
class ProfileJpaAdapter implements ProfilePort {

  private final UserProfileJpaRepository profiles;

  ProfileJpaAdapter(UserProfileJpaRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  public Optional<UserProfile> findByUserId(long userId) {
    return profiles.findByUserId(userId).map(ProfileJpaAdapter::toDomain);
  }

  @Override
  public void save(long userId, UserProfile p) {
    UserProfileEntity entity =
        profiles.findByUserId(userId).orElseGet(() -> new UserProfileEntity(userId));
    entity.update(
        EnumCodes.names(p.dressCodes()),
        p.moods(),
        p.colors(),
        p.workType().name(),
        p.workDays().stream().map(Integer::shortValue).toList(),
        p.workRule(),
        p.offRule(),
        p.notifications().toMap(),
        p.onboarded(),
        p.personal().ageRange(),
        p.personal().bodyType(),
        p.personal().heightCm() == null ? null : p.personal().heightCm().shortValue(),
        p.personal().fitPreference());
    profiles.saveAndFlush(entity);
  }

  private static UserProfile toDomain(UserProfileEntity e) {
    return new UserProfile(
        EnumCodes.parse(e.getDressCodes(), DressCode::valueOf),
        List.copyOf(e.getMoods()),
        List.copyOf(e.getColors()),
        WorkType.valueOf(e.getWorkType()),
        e.getWorkDays().stream().map(Short::intValue).toList(),
        e.isWorkRule(),
        e.isOffRule(),
        UserProfile.Notify.fromMap(e.getNotify()),
        e.isOnboarded(),
        new UserProfile.PersonalInfo(
            e.getAgeRange(),
            e.getBodyType(),
            e.getHeightCm() == null ? null : e.getHeightCm().intValue(),
            e.getFitPreference()));
  }
}
