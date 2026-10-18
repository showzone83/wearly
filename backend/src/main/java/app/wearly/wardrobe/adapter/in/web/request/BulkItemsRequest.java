package app.wearly.wardrobe.adapter.in.web.request;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** POST /wardrobe/items/bulk (05 문서 6장). 한 번에 100개까지 */
public record BulkItemsRequest(@NotNull @Size(max = 100) List<@NotNull @Valid ItemRequest> items) {

  public List<CreateItemDto> toDto() {
    return items.stream().map(ItemRequest::toDto).toList();
  }
}
