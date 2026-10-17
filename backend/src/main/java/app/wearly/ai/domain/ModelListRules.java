package app.wearly.ai.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 공급자 모델 목록 규칙 (07 문서 4장 모델 목록): 제공사 목록에서 사진 분석에 쓸 수 있는 모델만 남기기, 내장 목록의 추천 표시 붙이기, 정렬. 순수 함수라 단위
 * 테스트로 확인한다.
 */
public final class ModelListRules {

  /** OpenAI: 채팅·이미지 입력이 되는 계열 (gpt-*, o1·o3·o4-mini 같은 o 추론 계열) */
  private static final Pattern OPENAI_FAMILY = Pattern.compile("^(gpt-|o\\d).*");

  /** OpenAI: 이미지 입력이 없는 옛 채팅 모델 (gpt-3.5*, gpt-4, gpt-4-0613 …) */
  private static final Pattern OPENAI_NO_VISION =
      Pattern.compile("^(gpt-3\\.5.*|gpt-4(-0\\d{3})?)$");

  /** OpenAI: 채팅·이미지 분석이 아닌 용도 (이름에 들어 있으면 뺀다) */
  private static final List<String> OPENAI_EXCLUDED =
      List.of(
          "embedding",
          "tts",
          "whisper",
          "dall-e",
          "moderation",
          "realtime",
          "audio",
          "transcribe",
          "search",
          "image",
          "instruct",
          "computer-use",
          "deep-research");

  /** Gemini: generateContent가 되더라도 사진 분석(JSON 출력)에 맞지 않는 계열 */
  private static final List<String> GEMINI_EXCLUDED =
      List.of("embedding", "aqa", "imagen", "veo", "tts", "audio", "image", "gemma", "robotics");

  private static final String GEMINI_PREFIX = "models/";

  private static final String GENERATE_CONTENT = "generateContent";

  /** 추천 먼저, 그다음 createdAt 최신순(없는 것은 뒤), 그다음 ID 순 */
  private static final Comparator<AiModelEntry> ORDER =
      Comparator.comparing((AiModelEntry m) -> !m.recommended())
          .thenComparing(AiModelEntry::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(AiModelEntry::id);

  private ModelListRules() {}

  /** OpenAI GET /v1/models의 id 중 채팅·이미지 입력이 되는 계열만 */
  public static boolean isOpenAiChatModel(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    String v = id.strip().toLowerCase(Locale.ROOT);
    if (!OPENAI_FAMILY.matcher(v).matches() || OPENAI_NO_VISION.matcher(v).matches()) {
      return false;
    }
    return OPENAI_EXCLUDED.stream().noneMatch(v::contains);
  }

  /**
   * Gemini GET /v1beta/models의 한 줄을 모델 ID로. supportedGenerationMethods에 generateContent가 없거나 사진 분석에
   * 맞지 않는 계열(embedding·aqa·imagen …)이면 null. "models/" 접두사는 뗀다.
   */
  public static String geminiModelId(String name, Collection<String> generationMethods) {
    if (name == null || name.isBlank() || generationMethods == null) {
      return null;
    }
    if (!generationMethods.contains(GENERATE_CONTENT)) {
      return null;
    }
    String id = name.strip();
    if (id.startsWith(GEMINI_PREFIX)) {
      id = id.substring(GEMINI_PREFIX.length());
    }
    String lower = id.toLowerCase(Locale.ROOT);
    if (id.isEmpty() || GEMINI_EXCLUDED.stream().anyMatch(lower::contains)) {
      return null;
    }
    return id;
  }

  /**
   * 제공사 목록에 내장 목록의 추천 표시를 붙이고 정렬한다. 같은 ID가 두 번 오면 처음 것만 쓴다. 제공사가 화면 이름을 주지 않았으면(=ID) 내장 목록의 이름을 쓴다.
   */
  public static List<AiModelEntry> withCatalog(
      List<AiModelEntry> live, List<AiModelEntry> catalog) {
    Map<String, AiModelEntry> known = new LinkedHashMap<>();
    catalog.forEach(c -> known.putIfAbsent(c.id(), c));
    Map<String, AiModelEntry> out = new LinkedHashMap<>();
    for (AiModelEntry m : live) {
      if (m.id() == null || m.id().isBlank() || out.containsKey(m.id())) {
        continue;
      }
      AiModelEntry c = known.get(m.id());
      String name = c != null && m.displayName().equals(m.id()) ? c.displayName() : m.displayName();
      out.put(m.id(), new AiModelEntry(m.id(), name, m.createdAt(), c != null && c.recommended()));
    }
    return sorted(out.values());
  }

  /** 정렬: 추천 먼저, 그다음 createdAt 최신순(없는 것은 뒤), 그다음 ID 순 */
  public static List<AiModelEntry> sorted(Collection<AiModelEntry> models) {
    List<AiModelEntry> list = new ArrayList<>(models);
    list.sort(ORDER);
    return List.copyOf(list);
  }
}
