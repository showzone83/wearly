package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageTypesTest {

    @Test
    void 선언된_형식을_정규화하고_없으면_첫_바이트로_판별한다() {
        assertThat(ImageTypes.resolve("image/JPG", new byte[0])).contains("image/jpeg");
        assertThat(ImageTypes.resolve("image/heif; charset=x", new byte[0])).contains("image/heic");
        assertThat(ImageTypes.resolve(null, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).contains("image/jpeg");
        assertThat(ImageTypes.resolve("application/octet-stream",
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'})).contains("image/webp");
        assertThat(ImageTypes.resolve("text/plain", "hello".getBytes())).isEmpty();
        assertThat(ImageTypes.resolve("image/gif", new byte[] {'G', 'I', 'F'})).isEmpty();
    }
}
