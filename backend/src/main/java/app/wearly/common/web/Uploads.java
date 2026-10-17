package app.wearly.common.web;

import app.wearly.common.domain.ImageUpload;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** multipart 파트 → ImageUpload (08 문서 3장: MultipartFile은 웹 어댑터 밖으로 내보내지 않는다) */
public final class Uploads {

    private Uploads() {
    }

    public static ImageUpload of(MultipartFile file) {
        if (file == null) {
            return ImageUpload.missing();
        }
        if (file.isEmpty()) {
            return new ImageUpload(new byte[0], file.getContentType(), 0, true, false);
        }
        try {
            return new ImageUpload(file.getBytes(), file.getContentType(), file.getSize(), true, false);
        } catch (IOException e) {
            return new ImageUpload(new byte[0], file.getContentType(), file.getSize(), true, true);
        }
    }
}
