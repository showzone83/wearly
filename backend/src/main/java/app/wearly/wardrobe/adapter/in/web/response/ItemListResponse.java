package app.wearly.wardrobe.adapter.in.web.response;

import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import java.util.List;

/** { items: ItemDto[] } */
public record ItemListResponse(List<ItemResponse> items) {

  public static ItemListResponse from(List<ItemDto> items) {
    return new ItemListResponse(items.stream().map(ItemResponse::from).toList());
  }
}
