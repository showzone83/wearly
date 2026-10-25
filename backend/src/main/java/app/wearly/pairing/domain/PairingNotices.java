package app.wearly.pairing.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 옷장 도우미 알림 문구 (doc/26 3단계). 앱은 data.type으로 알림을 눌렀을 때 열 화면을 정한다. 이름은 회원이 정한 이름이라 그대로 넣는다.
 *
 * @param type 앱이 구분하는 종류
 */
public record PairingNotices(String type, String title, String body) {

  public static final String CHANNEL_ID = "closet-helper";

  public static final String TYPE_REQUEST = "closet-pairing-request";
  public static final String TYPE_ACCEPTED = "closet-pairing-accepted";
  public static final String TYPE_REJECTED = "closet-pairing-rejected";
  public static final String TYPE_ENDED = "closet-pairing-ended";
  public static final String TYPE_DIGEST = "closet-helper-digest";

  private static final String TITLE = "옷장 도우미";

  /** 돕는 사람이 주인 코드를 넣어 승인을 기다린다 (주인에게) */
  public static PairingNotices requested(String helperName) {
    return new PairingNotices(TYPE_REQUEST, TITLE, helperName + "님이 옷장 도우미가 되고 싶어 해요");
  }

  /** 연결됐다 (돕는 사람에게): 주인이 승인했거나 주인이 돕는 사람 코드를 넣었다 */
  public static PairingNotices accepted(String ownerName) {
    return new PairingNotices(TYPE_ACCEPTED, TITLE, ownerName + "님의 옷장을 도울 수 있어요");
  }

  /** 주인이 요청을 거절했다 (돕는 사람에게) */
  public static PairingNotices rejected(String ownerName) {
    return new PairingNotices(TYPE_REJECTED, TITLE, ownerName + "님이 옷장 도우미 요청을 거절했어요");
  }

  /** 연결이 해제됐다 (상대에게) */
  public static PairingNotices ended(String partnerName) {
    return new PairingNotices(TYPE_ENDED, TITLE, partnerName + "님과의 옷장 도우미 연결이 해제됐어요");
  }

  /**
   * 묶음 알림 (주인에게): 종류별로 건드린 옷 수를 센다. 예: "민지님이 옷 3벌을 등록하고 1벌을 수정했어요". 모두 0이면 null (보낼 것이 없다)
   *
   * @param created 등록한 옷 수
   * @param updated 수정한 옷 수
   * @param retired 사용 중지한 옷 수
   * @param restored 다시 사용한 옷 수
   */
  public static PairingNotices digest(
      String helperName, int created, int updated, int retired, int restored) {
    List<String> verbs = new ArrayList<>();
    List<Integer> counts = new ArrayList<>();
    add(verbs, counts, "등록", created);
    add(verbs, counts, "수정", updated);
    add(verbs, counts, "사용 중지", retired);
    add(verbs, counts, "다시 사용", restored);
    if (verbs.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder(helperName).append("님이 ");
    for (int i = 0; i < verbs.size(); i++) {
      sb.append(i == 0 ? "옷 " : "").append(counts.get(i)).append("벌을 ").append(verbs.get(i));
      sb.append(i == verbs.size() - 1 ? "했어요" : "하고 ");
    }
    return new PairingNotices(TYPE_DIGEST, TITLE, sb.toString());
  }

  private static void add(List<String> verbs, List<Integer> counts, String verb, int count) {
    if (count > 0) {
      verbs.add(verb);
      counts.add(count);
    }
  }

  /** 알림을 눌렀을 때 앱이 쓰는 값 */
  public Map<String, String> data(long pairingId) {
    return Map.of("type", type, "pairingId", String.valueOf(pairingId));
  }
}
