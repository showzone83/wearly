package app.wearly.admin.adapter.in.startup;

import app.wearly.admin.application.port.in.EnsureAdminUseCase;
import app.wearly.common.config.WearlyProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장, 22 문서 4.1). WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD가 있고 관리자가 한 명도 없으면
 * 그 이메일로 관리자를 만든다(이름 WEARLY_ADMIN_NAME, 기본 "관리자"). 그 이메일의 일반 회원이 이미 있으면 올리지 않는다.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final WearlyProperties.Admin props;
  private final EnsureAdminUseCase ensureAdmin;

  public AdminBootstrap(WearlyProperties properties, EnsureAdminUseCase ensureAdmin) {
    this.props = properties.admin();
    this.ensureAdmin = ensureAdmin;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!props.configured()) {
      log.info(
          "관리자 계정 설정 없음 (WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD). "
              + "관리자 사이트에 들어가려면 둘 다 넣고 다시 시작하세요.");
      return;
    }
    ensureAdmin(props.email(), props.password(), props.name());
  }

  /**
   * @return 새로 만든 관리자 회원 ID (만들지 않았으면 빈 값)
   */
  public Optional<Long> ensureAdmin(String email, String password, String name) {
    return ensureAdmin.ensureAdmin(email, password, name);
  }
}
