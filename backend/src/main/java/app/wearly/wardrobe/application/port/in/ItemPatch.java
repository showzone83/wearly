package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.domain.ItemInput;

/**
 * PATCH 본문을 저장된 값 위에 덮어쓰는 함수 (Partial&lt;ItemInput&gt;). 웹 어댑터가 JSON으로 구현한다.
 * 형식·필수 값 검증까지 마친 전체 입력을 돌려주고, 틀리면 400 VALIDATION_FAILED를 던진다.
 */
@FunctionalInterface
public interface ItemPatch {

    ItemInput applyTo(ItemInput current);
}
