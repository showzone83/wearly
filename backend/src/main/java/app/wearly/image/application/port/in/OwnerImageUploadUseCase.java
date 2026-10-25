package app.wearly.image.application.port.in;

import app.wearly.image.application.port.in.dto.CreateUploadUrlDto;
import app.wearly.image.application.port.in.dto.ImageDto;
import app.wearly.image.application.port.in.dto.UploadUrlDto;

/**
 * 돕는 사람이 옷장 주인 대신 옷 사진을 올린다 (doc/26 2단계). 등록·수정 권한을 확인하고(연결 없음 404, 권한 부족 403) {@link
 * ImageUploadUseCase}와 같은 일을 주인 이름으로 한다: 사진은 주인 경로에 쌓이고 하루 업로드 한도도 주인 것을 쓴다.
 */
public interface OwnerImageUploadUseCase {

  UploadUrlDto createUploadUrl(long helperId, long ownerId, CreateUploadUrlDto input);

  ImageDto complete(long helperId, long ownerId, String imageId);
}
