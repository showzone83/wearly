package app.wearly.user.application.port.out;

import app.wearly.user.domain.UserProfile;
import java.util.Optional;

/** 프로필 저장·로드 (JPA) */
public interface ProfilePort {

  Optional<UserProfile> findByUserId(long userId);

  void save(long userId, UserProfile profile);
}
