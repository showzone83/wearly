package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminInviteUseCase;
import app.wearly.admin.application.port.in.dto.InviteShareLogDto;
import app.wearly.admin.application.port.in.dto.InviteSignupLogDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminInviteQueryPort;
import app.wearly.admin.application.port.out.AdminInviteQueryPort.ShareFilter;
import app.wearly.admin.application.port.out.AdminInviteQueryPort.SignupFilter;
import app.wearly.admin.domain.SearchText;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 친구 초대 이력 (MyBatis 읽기, doc/25) */
@Service
public class AdminInviteService implements AdminInviteUseCase {

  /** tb_invite_signup.result 값 (invite 컨텍스트 InviteSignupResult와 같다) */
  static final List<String> RESULTS = List.of("LINKED", "UNKNOWN_CODE");

  /** tb_invite_share.channel 값 (invite 컨텍스트 InviteChannel과 같다) */
  static final List<String> CHANNELS = List.of("SHARE", "SMS", "COPY");

  private final AdminInviteQueryPort queries;

  public AdminInviteService(AdminInviteQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<InviteSignupLogDto> signups(String q, String result, PagingDto paging) {
    SignupFilter filter =
        new SignupFilter(SearchText.blankToNull(q), allowed("result", result, RESULTS));
    List<InviteSignupLogDto> items =
        queries.findSignups(filter, paging.size(), paging.offset()).stream()
            .map(
                r ->
                    new InviteSignupLogDto(
                        r.id(),
                        r.createdAt(),
                        r.userId(),
                        r.userEmail(),
                        r.userName(),
                        r.enteredCode(),
                        r.result(),
                        r.referrerId(),
                        r.referrerEmail(),
                        r.referrerName()))
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countSignups(filter));
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<InviteShareLogDto> shares(String q, String channel, PagingDto paging) {
    ShareFilter filter =
        new ShareFilter(SearchText.blankToNull(q), allowed("channel", channel, CHANNELS));
    List<InviteShareLogDto> items =
        queries.findShares(filter, paging.size(), paging.offset()).stream()
            .map(
                r ->
                    new InviteShareLogDto(
                        r.id(),
                        r.createdAt(),
                        r.userId(),
                        r.userEmail(),
                        r.userName(),
                        r.channel()))
            .toList();
    return new PageDto<>(items, paging.page(), paging.size(), queries.countShares(filter));
  }

  /** 비어 있으면 null(모두), 목록에 없으면 400 NOT_ALLOWED */
  private static String allowed(String field, String value, List<String> values) {
    String v = SearchText.blankToNull(value);
    if (v != null && !values.contains(v)) {
      throw BusinessException.validation(FieldError.notAllowed(field, values));
    }
    return v;
  }
}
