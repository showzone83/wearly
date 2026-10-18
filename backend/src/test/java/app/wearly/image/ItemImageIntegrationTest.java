package app.wearly.image;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.image.application.port.in.ImageCleanupUseCase;
import app.wearly.image.application.port.in.dto.CleanupResultDto;
import app.wearly.image.domain.ImageFormats;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestImages;
import app.wearly.support.TestMinio;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;

/**
 * 옷 사진 서버 저장 (18 문서): 업로드 URL → 서명 URL로 실제 PUT → complete → 아이템에 붙이기 → 서명 URL로 GET, 남의 이미지, 잘못된 파일,
 * 사용 중, 교체·완전 삭제·탈퇴 시 객체 삭제, 하루 한도, 정리 작업, imageId로 AI 분석. 저장소는 Testcontainers MinIO(TestMinio).
 */
class ItemImageIntegrationTest extends IntegrationTestBase {

  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  static final S3Client S3 =
      S3Client.builder()
          .region(Region.US_EAST_1)
          .endpointOverride(URI.create(TestMinio.endpoint()))
          .forcePathStyle(true)
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(TestMinio.ACCESS_KEY, TestMinio.SECRET_KEY)))
          .build();

  @Autowired JdbcTemplate jdbc;
  @Autowired ImageCleanupUseCase cleanup;

  @AfterAll
  static void closeClient() {
    S3.close();
  }

  @Test
  void 업로드_URL_PUT_complete_아이템_등록_서명_URL로_받기() throws Exception {
    Session s = signup();
    Instant before = Instant.now();
    byte[] photo = TestImages.withExif(TestImages.jpeg(3000, 1500), 1);
    Resp url = uploadUrl(s, "image/jpeg", photo.length).expect(200);

    JsonNode body = url.body();
    assertThat(body.get("method").asString()).isEqualTo("PUT");
    assertThat(body.get("headers").get("Content-Type").asString()).isEqualTo("image/jpeg");
    assertThat(body.get("headers").size()).isEqualTo(1);
    assertThat(URI.create(body.get("uploadUrl").asString()).getPort())
        .isEqualTo(URI.create(TestMinio.endpoint()).getPort());
    Instant expires = Instant.parse(body.get("expiresAt").asString());
    assertThat(expires).isBetween(before.plusSeconds(590), before.plusSeconds(660));
    String imageId = body.get("imageId").asString();
    assertThat(UUID.fromString(imageId).toString()).isEqualTo(imageId);

    // 아직 안 올렸으면 complete는 IMAGE_NOT_UPLOADED
    complete(s, imageId).expectError(400, "IMAGE_NOT_UPLOADED");
    assertThat(put(body, photo)).isEqualTo(200);
    Resp done = complete(s, imageId).expect(200);
    assertThat(done.body().get("imageId").asString()).isEqualTo(imageId);
    assertThat(done.body().get("width").asInt()).isEqualTo(2048);
    assertThat(done.body().get("height").asInt()).isEqualTo(1024);
    assertThat(Instant.parse(done.body().get("expiresAt").asString()))
        .isBetween(before.plusSeconds(3590), before.plusSeconds(3660));

    // 받은 original은 JPEG이고 EXIF(위치 정보)가 없다
    HttpResponse<byte[]> original = get(done.body().get("url").asString());
    assertThat(original.statusCode()).isEqualTo(200);
    assertThat(ImageFormats.sniff(original.body())).contains(ImageFormats.JPEG);
    assertThat(
            ImageMetadataReader.readMetadata(new ByteArrayInputStream(original.body()))
                .getFirstDirectoryOfType(GpsDirectory.class))
        .isNull();
    assertThat(get(done.body().get("thumbUrl").asString()).statusCode()).isEqualTo(200);
    // 업로드 원본은 지웠고, 버킷은 비공개라 서명 없이 받을 수 없다
    long userId = Long.parseLong(s.userId());
    assertThat(exists("uploads/" + userId + "/" + imageId)).isFalse();
    assertThat(exists(originalKey(userId, imageId))).isTrue();
    assertThat(
            get(TestMinio.endpoint() + "/" + TestMinio.BUCKET + "/" + originalKey(userId, imageId))
                .statusCode())
        .isEqualTo(403);
    // 이미 READY면 같은 응답
    assertThat(complete(s, imageId).expect(200).body().get("width").asInt()).isEqualTo(2048);

    // 아이템에 붙이기 → 응답·목록·상세·retire·restore에 image
    Resp created = api().post("/api/v1/wardrobe/items", s.accessToken(), item(imageId)).expect(201);
    JsonNode image = created.body().get("image");
    assertThat(image.get("imageId").asString()).isEqualTo(imageId);
    assertThat(get(image.get("url").asString()).statusCode()).isEqualTo(200);
    assertThat(get(image.get("thumbUrl").asString()).statusCode()).isEqualTo(200);
    assertThat(image.get("expiresAt").asString()).isNotBlank();
    String itemId = created.body().get("id").asString();
    JsonNode listed =
        api().get("/api/v1/wardrobe/items", s.accessToken()).expect(200).body().get("items");
    assertThat(listed.get(0).get("image").get("imageId").asString()).isEqualTo(imageId);
    assertThat(
            api()
                .get("/api/v1/wardrobe/items/" + itemId, s.accessToken())
                .expect(200)
                .body()
                .get("image")
                .get("imageId")
                .asString())
        .isEqualTo(imageId);
    Resp retired =
        api()
            .post(
                "/api/v1/wardrobe/items/" + itemId + "/retire",
                s.accessToken(),
                Map.of("reason", "DONATED"))
            .expect(200);
    assertThat(retired.body().get("image").get("imageId").asString()).isEqualTo(imageId);
    assertThat(exists(originalKey(userId, imageId))).isTrue();
    Resp restored =
        api()
            .post("/api/v1/wardrobe/items/" + itemId + "/restore", s.accessToken(), null)
            .expect(200);
    assertThat(restored.body().get("image").get("imageId").asString()).isEqualTo(imageId);

    // 사진 없는 아이템은 image: null
    Resp plain = api().post("/api/v1/wardrobe/items", s.accessToken(), item(null)).expect(201);
    assertThat(plain.body().has("image")).isTrue();
    assertThat(plain.body().get("image").isNull()).isTrue();
  }

  @Test
  void PNG도_받아_JPEG로_저장한다() throws Exception {
    Session s = signup();
    JsonNode done = uploadReady(s, TestImages.png(800, 600), "image/png");
    assertThat(done.get("width").asInt()).isEqualTo(800);
    HttpResponse<byte[]> original = get(done.get("url").asString());
    assertThat(ImageFormats.sniff(original.body())).contains(ImageFormats.JPEG);
  }

  @Test
  void 남의_이미지와_없는_이미지는_404() throws Exception {
    Session owner = signup();
    Session other = signup();
    String imageId =
        uploadReady(owner, TestImages.jpeg(100, 100), "image/jpeg").get("imageId").asString();

    complete(other, imageId).expectError(404, "NOT_FOUND");
    api()
        .post("/api/v1/wardrobe/items", other.accessToken(), item(imageId))
        .expectError(404, "NOT_FOUND");
    api()
        .post("/api/v1/wardrobe/items/analyze", other.accessToken(), Map.of("imageId", imageId))
        .expectError(404, "NOT_FOUND");
    complete(owner, UUID.randomUUID().toString()).expectError(404, "NOT_FOUND");
    complete(owner, "not-a-uuid").expectError(404, "NOT_FOUND");
    // 본문의 imageId 형식이 틀리면 400
    Resp bad =
        api()
            .post("/api/v1/wardrobe/items", owner.accessToken(), item("not-a-uuid"))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(bad.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("imageId");
    // 로그인 필요
    api()
        .post("/api/v1/images/upload-url", null, uploadBody("image/jpeg", 10))
        .expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 잘못된_파일은_IMAGE_INVALID이고_업로드_원본을_지운다() throws Exception {
    Session s = signup();
    long userId = Long.parseLong(s.userId());
    byte[] fake = TestImages.fakeJpeg();
    JsonNode url = uploadUrl(s, "image/jpeg", fake.length).expect(200).body();
    String imageId = url.get("imageId").asString();
    assertThat(put(url, fake)).isEqualTo(200);
    assertThat(exists("uploads/" + userId + "/" + imageId)).isTrue();

    Resp decode = complete(s, imageId).expectError(400, "IMAGE_INVALID");
    assertThat(decode.body().get("params").get("reason").asString()).isEqualTo("DECODE");
    assertThat(exists("uploads/" + userId + "/" + imageId)).isFalse();
    // 실패한 이미지는 버려져 다시 complete하면 404
    complete(s, imageId).expectError(404, "NOT_FOUND");

    // 글자 파일을 image/png라고 올려도 실제 바이트로 거른다
    byte[] text = "hello, not an image".getBytes();
    JsonNode url2 = uploadUrl(s, "image/png", text.length).expect(200).body();
    put(url2, text);
    Resp invalid = complete(s, url2.get("imageId").asString()).expectError(400, "IMAGE_INVALID");
    assertThat(invalid.body().get("params").get("reason").asString()).isEqualTo("FORMAT");
  }

  @Test
  void 업로드_URL_입력_검증() {
    Session s = signup();
    Resp r =
        api()
            .post(
                "/api/v1/images/upload-url",
                s.accessToken(),
                Map.of("purpose", "PROFILE", "contentType", "image/gif", "size", 0))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactly("purpose", "contentType", "size");
    Resp big =
        uploadUrl(s, "image/jpeg", 10L * 1024 * 1024 + 1).expectError(400, "VALIDATION_FAILED");
    JsonNode size = big.body().get("fieldErrors").get(0);
    assertThat(size.get("code").asString()).isEqualTo("OUT_OF_RANGE");
    assertThat(size.get("params").get("max").asLong()).isEqualTo(10L * 1024 * 1024);
  }

  @Test
  void PENDING_이미지는_아이템에_붙일_수_없다() {
    Session s = signup();
    String imageId = uploadUrl(s, "image/jpeg", 100).expect(200).body().get("imageId").asString();
    api()
        .post("/api/v1/wardrobe/items", s.accessToken(), item(imageId))
        .expectError(400, "IMAGE_NOT_UPLOADED");
    api()
        .post("/api/v1/wardrobe/items/analyze", s.accessToken(), Map.of("imageId", imageId))
        .expectError(400, "IMAGE_NOT_UPLOADED");
    // 붙이기가 실패하면 아이템도 만들지 않는다
    assertThat(api().get("/api/v1/wardrobe/items", s.accessToken()).expect(200).body().get("items"))
        .isEmpty();
  }

  @Test
  void 다른_아이템에_붙은_이미지는_IMAGE_IN_USE() throws Exception {
    Session s = signup();
    String imageId =
        uploadReady(s, TestImages.jpeg(100, 100), "image/jpeg").get("imageId").asString();
    String first =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item(imageId))
            .expect(201)
            .body()
            .get("id")
            .asString();

    api()
        .post("/api/v1/wardrobe/items", s.accessToken(), item(imageId))
        .expectError(409, "IMAGE_IN_USE");
    String second =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item(null))
            .expect(201)
            .body()
            .get("id")
            .asString();
    api()
        .patch("/api/v1/wardrobe/items/" + second, s.accessToken(), Map.of("imageId", imageId))
        .expectError(409, "IMAGE_IN_USE");
    // 같은 아이템에 다시 붙이면 그대로
    Resp same =
        api()
            .patch("/api/v1/wardrobe/items/" + first, s.accessToken(), Map.of("imageId", imageId))
            .expect(200);
    assertThat(same.body().get("image").get("imageId").asString()).isEqualTo(imageId);
  }

  @Test
  void 사진을_바꾸거나_빼면_예전_객체를_지운다() throws Exception {
    Session s = signup();
    long userId = Long.parseLong(s.userId());
    String oldId =
        uploadReady(s, TestImages.jpeg(100, 100), "image/jpeg").get("imageId").asString();
    String newId = uploadReady(s, TestImages.png(120, 80), "image/png").get("imageId").asString();
    String itemId =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item(oldId))
            .expect(201)
            .body()
            .get("id")
            .asString();

    Resp changed =
        api()
            .patch("/api/v1/wardrobe/items/" + itemId, s.accessToken(), Map.of("imageId", newId))
            .expect(200);
    assertThat(changed.body().get("image").get("imageId").asString()).isEqualTo(newId);
    assertThat(exists(originalKey(userId, oldId))).isFalse();
    assertThat(exists(thumbKey(userId, oldId))).isFalse();
    assertThat(exists(originalKey(userId, newId))).isTrue();
    assertThat(status(oldId)).isEqualTo("DELETED");
    // 버린 사진은 다시 붙일 수 없다
    api()
        .patch("/api/v1/wardrobe/items/" + itemId, s.accessToken(), Map.of("imageId", oldId))
        .expectError(404, "NOT_FOUND");

    // 다른 필드만 고치면 사진은 그대로
    Resp renamed =
        api()
            .patch("/api/v1/wardrobe/items/" + itemId, s.accessToken(), Map.of("name", "새 이름"))
            .expect(200);
    assertThat(renamed.body().get("image").get("imageId").asString()).isEqualTo(newId);

    Map<String, Object> remove = new HashMap<>();
    remove.put("imageId", null);
    Resp removed =
        api().patch("/api/v1/wardrobe/items/" + itemId, s.accessToken(), remove).expect(200);
    assertThat(removed.body().get("image").isNull()).isTrue();
    assertThat(exists(originalKey(userId, newId))).isFalse();
  }

  @Test
  void 아이템을_완전_삭제하면_사진_객체도_지운다() throws Exception {
    Session s = signup();
    long userId = Long.parseLong(s.userId());
    String imageId =
        uploadReady(s, TestImages.jpeg(100, 100), "image/jpeg").get("imageId").asString();
    String itemId =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item(imageId))
            .expect(201)
            .body()
            .get("id")
            .asString();

    api().delete("/api/v1/wardrobe/items/" + itemId, s.accessToken()).expect(204);
    assertThat(exists(originalKey(userId, imageId))).isFalse();
    assertThat(exists(thumbKey(userId, imageId))).isFalse();
    assertThat(status(imageId)).isEqualTo("DELETED");
  }

  @Test
  void 탈퇴하면_회원의_사진_객체를_모두_지운다() throws Exception {
    Session s = signup();
    final long userId = Long.parseLong(s.userId());
    String attached =
        uploadReady(s, TestImages.jpeg(100, 100), "image/jpeg").get("imageId").asString();
    api().post("/api/v1/wardrobe/items", s.accessToken(), item(attached)).expect(201);
    uploadReady(s, TestImages.jpeg(50, 50), "image/jpeg");
    JsonNode pending = uploadUrl(s, "image/jpeg", 10).expect(200).body();
    put(pending, TestImages.jpeg(10, 10));
    assertThat(count("users/" + userId + "/")).isEqualTo(4);
    assertThat(count("uploads/" + userId + "/")).isEqualTo(1);

    api().delete("/api/v1/users/me", s.accessToken()).expect(204);
    assertThat(count("users/" + userId + "/")).isZero();
    assertThat(count("uploads/" + userId + "/")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_item_image WHERE user_id = ?", Long.class, userId))
        .isZero();
  }

  @Test
  void 하루_업로드_한도를_넘으면_429() {
    Session s = signup();
    long userId = Long.parseLong(s.userId());
    // 오늘 이미 199장 받은 것으로 둔다 (상태와 상관없이 센다)
    jdbc.update(
        "INSERT INTO tb_item_image (image_id, user_id, status, content_type, declared_size,"
            + " upload_key, created_at)"
            + " SELECT gen_random_uuid(), ?, 'DELETED', 'image/jpeg', 1, 'x', now()"
            + " FROM generate_series(1, 199)",
        userId);
    uploadUrl(s, "image/jpeg", 10).expect(200);
    uploadUrl(s, "image/jpeg", 10).expectError(429, "IMAGE_UPLOAD_LIMIT");
    // 다른 회원은 따로 센다
    uploadUrl(signup(), "image/jpeg", 10).expect(200);
  }

  @Test
  void 정리_작업은_오래된_PENDING_안_붙은_READY_DELETED를_지운다() throws Exception {
    Session s = signup();
    final long userId = Long.parseLong(s.userId());
    JsonNode pending = uploadUrl(s, "image/jpeg", 10).expect(200).body();
    put(pending, TestImages.jpeg(10, 10));
    final String pendingId = pending.get("imageId").asString();
    final String unattached =
        uploadReady(s, TestImages.jpeg(60, 60), "image/jpeg").get("imageId").asString();
    String attached =
        uploadReady(s, TestImages.jpeg(70, 70), "image/jpeg").get("imageId").asString();
    String fresh = uploadReady(s, TestImages.jpeg(80, 80), "image/jpeg").get("imageId").asString();
    api().post("/api/v1/wardrobe/items", s.accessToken(), item(attached)).expect(201);
    // 교체로 버린 사진 (DELETED)
    String replaced =
        uploadReady(s, TestImages.jpeg(90, 90), "image/jpeg").get("imageId").asString();
    String itemId =
        api()
            .post("/api/v1/wardrobe/items", s.accessToken(), item(replaced))
            .expect(201)
            .body()
            .get("id")
            .asString();
    api()
        .patch("/api/v1/wardrobe/items/" + itemId, s.accessToken(), Map.of("imageId", fresh))
        .expect(200);
    String keep = uploadReady(s, TestImages.jpeg(30, 30), "image/jpeg").get("imageId").asString();
    // keep만 빼고 하루 넘게 지난 것으로 만든다
    jdbc.update(
        "UPDATE tb_item_image SET created_at = now() - interval '2 days',"
            + " ready_at = CASE WHEN ready_at IS NULL THEN NULL ELSE now() - interval '2 days' END"
            + " WHERE user_id = ? AND image_id <> ?::uuid",
        userId,
        keep);

    CleanupResultDto result = cleanup.cleanup();

    assertThat(result.pending()).isGreaterThanOrEqualTo(1);
    assertThat(result.unattached()).isGreaterThanOrEqualTo(1);
    assertThat(result.deleted()).isGreaterThanOrEqualTo(1);
    assertThat(rowExists(pendingId)).isFalse();
    assertThat(rowExists(unattached)).isFalse();
    assertThat(rowExists(replaced)).isFalse();
    assertThat(exists("uploads/" + userId + "/" + pendingId)).isFalse();
    assertThat(exists(originalKey(userId, unattached))).isFalse();
    // 아이템에 붙은 것과 하루가 안 지난 것은 남는다
    assertThat(status(attached)).isEqualTo("READY");
    assertThat(status(fresh)).isEqualTo("READY");
    assertThat(status(keep)).isEqualTo("READY");
    assertThat(exists(originalKey(userId, attached))).isTrue();
    assertThat(exists(originalKey(userId, keep))).isTrue();
  }

  @Test
  void imageId로_AI_분석() throws Exception {
    Session s = signup();
    String imageId =
        uploadReady(s, TestImages.jpeg(200, 300), "image/jpeg").get("imageId").asString();

    Resp r =
        api()
            .post("/api/v1/wardrobe/items/analyze", s.accessToken(), Map.of("imageId", imageId))
            .expect(200);
    assertThat(r.body().get("provider").asString()).isEqualTo("mock");
    assertThat(r.body().get("suggestion").get("category").asString()).isNotBlank();
    assertThat(r.body().get("remainingToday").asInt()).isEqualTo(2);
    // 같은 사진이면 같은 결과 (목업은 바이트 해시로 정한다)
    Resp again =
        api()
            .post("/api/v1/wardrobe/items/analyze", s.accessToken(), Map.of("imageId", imageId))
            .expect(200);
    assertThat(again.body().get("suggestion")).isEqualTo(r.body().get("suggestion"));
    Resp missing =
        api()
            .post("/api/v1/wardrobe/items/analyze", s.accessToken(), Map.of())
            .expectError(400, "VALIDATION_FAILED");
    assertThat(missing.body().get("fieldErrors").get(0).get("field").asString())
        .isEqualTo("imageId");
  }

  // ── 도우미 ──

  private Resp uploadUrl(Session s, String contentType, long size) {
    return api().post("/api/v1/images/upload-url", s.accessToken(), uploadBody(contentType, size));
  }

  private static Map<String, Object> uploadBody(String contentType, long size) {
    return Map.of("purpose", "ITEM", "contentType", contentType, "size", size);
  }

  private Resp complete(Session s, String imageId) {
    return api().post("/api/v1/images/" + imageId + "/complete", s.accessToken(), null);
  }

  /** upload-url → PUT → complete. complete 응답 본문 */
  private JsonNode uploadReady(Session s, byte[] bytes, String contentType) throws Exception {
    JsonNode url = uploadUrl(s, contentType, bytes.length).expect(200).body();
    assertThat(put(url, bytes)).isEqualTo(200);
    return complete(s, url.get("imageId").asString()).expect(200).body();
  }

  /** 서명 URL로 실제 PUT (받은 headers를 그대로 붙인다) */
  private static int put(JsonNode uploadUrl, byte[] bytes) throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(uploadUrl.get("uploadUrl").asString()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
    uploadUrl
        .get("headers")
        .properties()
        .forEach(e -> b.header(e.getKey(), e.getValue().asString()));
    return HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private static HttpResponse<byte[]> get(String url) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  private static boolean exists(String key) {
    try {
      S3.headObject(r -> r.bucket(TestMinio.BUCKET).key(key));
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  private static int count(String prefix) {
    return S3.listObjectsV2(r -> r.bucket(TestMinio.BUCKET).prefix(prefix)).contents().size();
  }

  private static String originalKey(long userId, String imageId) {
    return "users/" + userId + "/items/" + imageId + "/original.jpg";
  }

  private static String thumbKey(long userId, String imageId) {
    return "users/" + userId + "/items/" + imageId + "/thumb.jpg";
  }

  private String status(String imageId) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT status FROM tb_item_image WHERE image_id = ?::uuid", String.class, imageId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private boolean rowExists(String imageId) {
    return status(imageId) != null;
  }

  private static Map<String, Object> item(String imageId) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", "화이트 셔츠");
    m.put("category", "TOP");
    m.put("subType", "SHIRT");
    m.put("color", "white");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    if (imageId != null) {
      m.put("imageId", imageId);
    }
    return m;
  }
}
