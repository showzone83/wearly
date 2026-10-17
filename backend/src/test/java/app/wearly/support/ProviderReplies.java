package app.wearly.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 가짜 공급자 서버가 돌려줄 각 사 응답 본문 */
public final class ProviderReplies {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private ProviderReplies() {
    }

    public static ObjectNode suggestion() {
        ObjectNode n = JSON.createObjectNode();
        n.put("name", "블루 청바지");
        n.put("category", "BOTTOM");
        n.put("subType", "JEANS");
        n.put("color", "denim");
        n.put("pattern", "SOLID");
        n.putArray("seasons").add("SPRING").add("FALL");
        n.putArray("styles").add("CASUAL");
        n.put("formality", 1);
        n.put("confidence", 0.91);
        return n;
    }

    /** Claude Messages API 응답 */
    public static String claude(JsonNode suggestion, String model) {
        ObjectNode m = JSON.createObjectNode();
        m.put("id", "msg_test");
        m.put("type", "message");
        m.put("role", "assistant");
        m.put("model", model);
        m.putArray("content").addObject().put("type", "text").put("text", JSON.writeValueAsString(suggestion));
        m.put("stop_reason", "end_turn");
        m.putNull("stop_sequence");
        m.putObject("usage").put("input_tokens", 1500).put("output_tokens", 80);
        return JSON.writeValueAsString(m);
    }

    /** OpenAI Responses API 응답 (reasoning 항목 + message/output_text) */
    public static String openai(String text, String model) {
        ObjectNode r = JSON.createObjectNode();
        r.put("id", "resp_test");
        r.put("object", "response");
        r.put("status", "completed");
        r.put("model", model);
        var output = r.putArray("output");
        output.addObject().put("type", "reasoning").put("id", "rs_1").putArray("summary");
        ObjectNode msg = output.addObject();
        msg.put("type", "message").put("id", "msg_1").put("role", "assistant").put("status", "completed");
        msg.putArray("content").addObject().put("type", "output_text").put("text", text).putArray("annotations");
        ObjectNode usage = r.putObject("usage");
        usage.put("input_tokens", 1200).put("output_tokens", 300).put("total_tokens", 1500);
        usage.putObject("input_tokens_details").put("cached_tokens", 0);
        usage.putObject("output_tokens_details").put("reasoning_tokens", 200);
        return JSON.writeValueAsString(r);
    }

    /** Gemini generateContent 응답 (생각 요약 part + 본문 part) */
    public static String gemini(String text, String modelVersion) {
        ObjectNode r = JSON.createObjectNode();
        ObjectNode cand = r.putArray("candidates").addObject();
        var parts = cand.putObject("content").put("role", "model").putArray("parts");
        parts.addObject().put("text", "생각 요약").put("thought", true);
        parts.addObject().put("text", text);
        cand.put("finishReason", "STOP");
        cand.put("index", 0);
        r.putObject("usageMetadata").put("promptTokenCount", 900).put("candidatesTokenCount", 70)
                .put("thoughtsTokenCount", 30).put("totalTokenCount", 1000);
        r.put("modelVersion", modelVersion);
        r.put("responseId", "resp-1");
        return JSON.writeValueAsString(r);
    }
}
