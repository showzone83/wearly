package app.wearly.ai;

import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.Pattern;
import app.wearly.domain.Season;
import app.wearly.domain.StyleTag;
import app.wearly.domain.SubType;
import java.util.List;

/** AnalyzeResponse.suggestion (계약 6장) */
public record AiSuggestion(
        String name,
        Category category,
        SubType subType,
        ColorKey color,
        Pattern pattern,
        List<Season> seasons,
        List<StyleTag> styles,
        int formality,
        double confidence) {
}
