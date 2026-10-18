package app.wearly.image.application.port.in;

import app.wearly.image.application.port.in.dto.ImageContentDto;

/** 저장한 사진 읽기 (ai 컨텍스트의 사진 분석이 부른다) */
public interface ImageContentUseCase {

  /** 내 READY 이미지의 original. 없거나 남의 것·지운 것은 404, PENDING이면 400 IMAGE_NOT_UPLOADED */
  ImageContentDto readOriginal(long userId, String imageId);
}
