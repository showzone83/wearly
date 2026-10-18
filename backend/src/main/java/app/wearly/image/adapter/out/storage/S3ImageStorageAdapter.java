package app.wearly.image.adapter.out.storage;

import app.wearly.common.config.WearlyProperties;
import app.wearly.image.application.port.out.ImageStoragePort;
import app.wearly.image.domain.PresignedUrl;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * ImageStoragePort 구현: S3 호환 오브젝트 스토리지 하나의 어댑터(AWS SDK v2 S3Client + S3Presigner)로 로컬 MinIO와 AWS
 * S3·Cloudflare R2·NCP Object Storage를 모두 쓴다 (wearly.storage.* 설정만 바꾼다, 17 문서 1장).
 *
 * <p>버킷은 비공개다. 앱은 서명 URL로만 올리고 받는다. 서명 URL의 호스트는 {@link StoragePresigners}가 정한다.
 */
@Component
class S3ImageStorageAdapter implements ImageStoragePort {

  private static final Logger log = LoggerFactory.getLogger(S3ImageStorageAdapter.class);

  private final S3Client client;
  private final StoragePresigners presigners;
  private final String bucket;

  S3ImageStorageAdapter(WearlyProperties properties) {
    WearlyProperties.Storage s = properties.storage();
    Region region = Region.of(s.region());
    AwsCredentialsProvider credentials = credentials(s);
    URI endpoint = blank(s.endpoint()) ? null : URI.create(s.endpoint().strip());
    URI publicEndpoint = blank(s.publicEndpoint()) ? null : URI.create(s.publicEndpoint().strip());
    S3ClientBuilder b =
        S3Client.builder()
            .region(region)
            .credentialsProvider(credentials)
            .forcePathStyle(s.pathStyle())
            // MinIO·R2·NCP 호환: 새 SDK가 기본으로 붙이는 CRC 체크섬은 필요한 요청에만 쓴다
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    if (endpoint != null) {
      b.endpointOverride(endpoint);
    }
    this.client = b.build();
    this.presigners =
        new StoragePresigners(region, credentials, s.pathStyle(), endpoint, publicEndpoint);
    this.bucket = s.bucket();
    if (s.autoCreateBucket()) {
      ensureBucket();
    }
  }

  @Override
  public PresignedUrl presignPut(String key, String contentType, Duration ttl) {
    PutObjectRequest put =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
    PresignedPutObjectRequest p =
        presigners.with(
            presigner ->
                presigner.presignPutObject(r -> r.signatureDuration(ttl).putObjectRequest(put)));
    return new PresignedUrl(
        p.url().toString(), Map.of("Content-Type", contentType), p.expiration());
  }

  @Override
  public PresignedUrl presignGet(String key, Duration ttl) {
    GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
    PresignedGetObjectRequest p =
        presigners.with(
            presigner ->
                presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(get)));
    return new PresignedUrl(p.url().toString(), Map.of(), p.expiration());
  }

  @Override
  public Optional<Long> size(String key) {
    try {
      HeadObjectResponse head = client.headObject(r -> r.bucket(bucket).key(key));
      return Optional.of(head.contentLength());
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }

  @Override
  public byte[] read(String key) {
    return client.getObjectAsBytes(r -> r.bucket(bucket).key(key)).asByteArray();
  }

  @Override
  public void write(String key, byte[] bytes, String contentType) {
    client.putObject(
        r -> r.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
  }

  @Override
  public void delete(Collection<String> keys) {
    // 없는 키를 지워도 S3는 204를 준다
    for (String key : keys) {
      client.deleteObject(r -> r.bucket(bucket).key(key));
    }
  }

  @Override
  public void deletePrefix(String prefix) {
    if (prefix == null || prefix.isBlank() || !prefix.endsWith("/")) {
      throw new IllegalArgumentException("접두사는 /로 끝나야 해요: " + prefix);
    }
    ListObjectsV2Request list =
        ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
    int count = 0;
    for (S3Object o : client.listObjectsV2Paginator(list).contents()) {
      client.deleteObject(r -> r.bucket(bucket).key(o.key()));
      count++;
    }
    log.info("사진 객체 {}개 삭제: {}{}", count, bucket + "/", prefix);
  }

  /** 버킷이 없으면 만든다 (wearly.storage.auto-create-bucket, 테스트·하네스용) */
  private void ensureBucket() {
    try {
      client.headBucket(r -> r.bucket(bucket));
    } catch (NoSuchBucketException e) {
      createBucket();
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      createBucket();
    }
  }

  private void createBucket() {
    client.createBucket(r -> r.bucket(bucket));
    log.info("사진 버킷을 만들었어요: {}", bucket);
  }

  @PreDestroy
  void close() {
    presigners.close();
    client.close();
  }

  private static AwsCredentialsProvider credentials(WearlyProperties.Storage s) {
    if (blank(s.accessKey()) || blank(s.secretKey())) {
      return DefaultCredentialsProvider.builder().build();
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(s.accessKey().strip(), s.secretKey().strip()));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
