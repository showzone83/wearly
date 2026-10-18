package app.wearly.image.application.port.in;

import app.wearly.image.application.port.in.dto.CreateUploadUrlDto;
import app.wearly.image.application.port.in.dto.ImageDto;
import app.wearly.image.application.port.in.dto.UploadUrlDto;

/** 옷 사진 올리기 (17 문서 3장): 업로드 URL 발급 → 앱이 저장소에 바로 PUT → complete(검증·다시 인코딩) */
public interface ImageUploadUseCase {

  /**
   * PENDING 이미지를 만들고 PUT 서명 URL을 준다. 입력이 틀리면 400 VALIDATION_FAILED, 하루 한도를 넘으면 429
   * IMAGE_UPLOAD_LIMIT
   */
  UploadUrlDto createUploadUrl(long userId, CreateUploadUrlDto input);

  /**
   * 올린 객체를 검증하고 메타데이터 없이 다시 인코딩해 original·thumb를 저장한다. 없거나 남의 이미지·지운 이미지는 404, 객체가 없으면 400
   * IMAGE_NOT_UPLOADED, 사진으로 쓸 수 없으면 400 IMAGE_INVALID(업로드 원본을 지우고 이미지는 DELETED). 이미 READY면 같은 응답(새
   * 서명 URL)
   */
  ImageDto complete(long userId, String imageId);
}
