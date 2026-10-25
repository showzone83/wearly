package app.wearly.image.application.service;

import app.wearly.image.application.port.in.ImageUploadUseCase;
import app.wearly.image.application.port.in.OwnerImageUploadUseCase;
import app.wearly.image.application.port.in.dto.CreateUploadUrlDto;
import app.wearly.image.application.port.in.dto.ImageDto;
import app.wearly.image.application.port.in.dto.UploadUrlDto;
import app.wearly.pairing.application.port.in.ClosetAccessUseCase;
import org.springframework.stereotype.Service;

/**
 * 옷장 도우미의 사진 올리기 (doc/26 2단계). 연결·권한은 pairing 입력 포트로 확인하고 올리기는 {@link ImageUploadUseCase}에 주인 ID로
 * 맡긴다. 사진 올리기는 옷이 아니라 기록하지 않는다 (옷에 붙일 때 옷 수정·등록으로 남는다)
 */
@Service
public class OwnerImageUploadService implements OwnerImageUploadUseCase {

  private final ClosetAccessUseCase access;
  private final ImageUploadUseCase uploads;

  public OwnerImageUploadService(ClosetAccessUseCase access, ImageUploadUseCase uploads) {
    this.access = access;
    this.uploads = uploads;
  }

  @Override
  public UploadUrlDto createUploadUrl(long helperId, long ownerId, CreateUploadUrlDto input) {
    access.requireEdit(helperId, ownerId);
    return uploads.createUploadUrl(ownerId, input);
  }

  @Override
  public ImageDto complete(long helperId, long ownerId, String imageId) {
    access.requireEdit(helperId, ownerId);
    return uploads.complete(ownerId, imageId);
  }
}
