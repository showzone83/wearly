package app.wearly.wardrobe;

import app.wearly.ai.AiAnalysisService;
import app.wearly.ai.AnalyzeResponse;
import app.wearly.auth.CurrentUser;
import app.wearly.common.Ids;
import app.wearly.wardrobe.WardrobeDtos.BulkRequest;
import app.wearly.wardrobe.WardrobeDtos.ItemDto;
import app.wearly.wardrobe.WardrobeDtos.ItemList;
import app.wearly.wardrobe.WardrobeDtos.WardrobeStats;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/wardrobe")
public class WardrobeController {

    private final WardrobeService wardrobe;
    private final WardrobeStatsService stats;
    private final AiAnalysisService ai;

    public WardrobeController(WardrobeService wardrobe, WardrobeStatsService stats, AiAnalysisService ai) {
        this.wardrobe = wardrobe;
        this.stats = stats;
        this.ai = ai;
    }

    @GetMapping("/items")
    public ItemList list(@CurrentUser long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return new ItemList(wardrobe.list(userId, category, status, color, q, sort));
    }

    @GetMapping("/items/{id}")
    public ItemDto get(@CurrentUser long userId, @PathVariable String id) {
        return wardrobe.get(userId, Ids.parse(id));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto create(@CurrentUser long userId, @Valid @RequestBody ItemInput input) {
        return wardrobe.create(userId, input);
    }

    @PostMapping("/items/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemList bulk(@CurrentUser long userId, @Valid @RequestBody BulkRequest request) {
        return new ItemList(wardrobe.bulkCreate(userId, request.items()));
    }

    @PatchMapping("/items/{id}")
    public ItemDto patch(@CurrentUser long userId, @PathVariable String id, @RequestBody JsonNode patch) {
        return wardrobe.patch(userId, Ids.parse(id), patch);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser long userId, @PathVariable String id) {
        wardrobe.delete(userId, Ids.parse(id));
    }

    @GetMapping("/stats")
    public WardrobeStats stats(@CurrentUser long userId) {
        return stats.stats(userId);
    }

    @PostMapping(path = "/items/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyze(@CurrentUser long userId, @RequestPart(name = "image", required = false) MultipartFile image) {
        return ai.analyze(userId, image);
    }
}
