package app.wearly.admin.adapter.in.startup;

import app.wearly.admin.application.port.in.EnsureAdminUseCase;
import app.wearly.common.config.WearlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장). WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD가 있으면 그 이메일 계정을 만들거나(이름
 * WEARLY_ADMIN_NAME, 기본 "관리자") 이미 있으면 ADMIN으로 올린다.
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
          "관리자 계정 설정 없음 (WEARLY_ADMIN_EMAIL·WEARLY_ADMIN_PASSWORD). 관리자 사이트에 들어가려면 둘 다 넣고 다시 시작하세요.");
      return;
    }
    ensureAdmin(props.email(), props.password(), props.name());
  }

  /**
   * @return 관리자 회원 ID
   */
  public long ensureAdmin(String email, String password, String name) {
    return ensureAdmin.ensureAdmin(email, password, name);
  }
}
