// 옷 사진 올리기 (계약 4장): 긴 변 1600 이하 JPEG(0.8)로 바꾼 뒤 upload-url → 서명 주소 PUT → complete.
// 올린 직후에는 기기 파일로 expo-image 캐시(imageId 기반 키)를 채워 두어, 서버 주소를 처음 그릴 때도 내려받지 않는다.
// 게스트 사진처럼 기기에만 두는 사진은 선택기 캐시가 아니라 앱 문서 폴더(photos/)에 복사해 둔다.
import { Directory, File, Paths } from 'expo-file-system';
import { Image } from 'expo-image';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { Platform } from 'react-native';

import { ApiError } from '@/api/errors';
import type { ImageContentType, ImageRef, UploadBody } from '@/api/types';
import { api } from './api';
import {
  createSupportMemo,
  IMAGES_UNSUPPORTED,
  imageCacheKey,
  isImagesUnsupported,
  needsLocalCopy,
  photoFileName,
} from './photoRules';

/** 서버로 올리는 사진의 긴 변 (계약 4장) */
const MAX_SIDE = 1600;
const QUALITY = 0.8;
/** 썸네일 캐시를 채울 때 쓰는 긴 변 (서버 썸네일과 같은 크기) */
const THUMB_SIDE = 400;

const isWeb = Platform.OS === 'web';

/** 올릴 준비가 끝난 사진 (기기 캐시 폴더의 JPEG) */
export interface IPreparedPhoto {
  uri: string;
  contentType: ImageContentType;
}

/** SharedObject(이미지 참조·조작 문맥)의 네이티브 메모리를 바로 놓는다 */
const release = (ref: unknown) => {
  try {
    (ref as { release?: () => void } | null)?.release?.();
  } catch {
    // 이미 놓았거나 웹이면 무시
  }
};

/** 긴 변을 maxSide 이하로 줄여 JPEG로 저장한다 (HEIC 등도 JPEG가 된다). 캐시 폴더에 새 파일이 생긴다 */
export const toJpeg = async (uri: string, maxSide = MAX_SIDE, compress = QUALITY): Promise<string> => {
  const first = await ImageManipulator.manipulate(uri).renderAsync();
  let image = first;
  try {
    if (Math.max(first.width, first.height) > maxSide) {
      const size = first.width >= first.height ? { width: maxSide } : { height: maxSide };
      image = await ImageManipulator.manipulate(first).resize(size).renderAsync();
    }
    return (await image.saveAsync({ format: SaveFormat.JPEG, compress })).uri;
  } finally {
    if (image !== first) release(image);
    release(first);
  }
};

/** 올릴 사진 준비(긴 변 1600 이하 JPEG 0.8). 변환에 실패하면 원본이 JPEG·PNG일 때만 그대로 쓴다 */
export const preparePhoto = async (uri: string): Promise<IPreparedPhoto> => {
  try {
    return { uri: await toJpeg(uri), contentType: 'image/jpeg' };
  } catch (e) {
    console.warn('[photo] 사진 변환 실패, 원본으로 시도', e);
    const ext = /\.(jpe?g|png)(?:[?#].*)?$/i.exec(uri)?.[1]?.toLowerCase();
    if (ext) return { uri, contentType: ext === 'png' ? 'image/png' : 'image/jpeg' };
    // 화면 문구는 errorText의 IMAGE_INVALID(reason DECODE): "사진을 읽을 수 없어요. 다른 사진으로 시도해 주세요."
    throw new ApiError(0, 'IMAGE_INVALID', 'IMAGE_INVALID', [], { reason: 'DECODE' });
  }
};

/** 웹은 Blob, 네이티브는 expo-file-system File(Blob 인터페이스) */
const readBody = async (uri: string): Promise<{ body: UploadBody; size: number }> => {
  if (isWeb) {
    const blob = await (await fetch(uri)).blob();
    return { body: blob, size: blob.size };
  }
  const file = new File(uri);
  return { body: file, size: file.size };
};

const support = createSupportMemo();

/** 최근에 서버에 사진 API가 없다고 확인했으면 true (그동안은 묻지 않는다) */
export const imagesKnownUnsupported = (): boolean => support.knownUnsupported(Date.now());

const unsupportedError = (cause?: unknown): ApiError => {
  const err = new ApiError(404, IMAGES_UNSUPPORTED, '서버에 아직 사진 저장 기능이 없어요.');
  if (cause !== undefined) (err as { cause?: unknown }).cause = cause;
  return err;
};

export interface IUploadedPhoto {
  image: ImageRef;
  /** 올린 파일(기기 캐시 폴더). 미리보기용 */
  localUri: string;
  /** 화면 캐시 채우기가 끝나면 true (기기 사진을 지워도 끊기지 않는다) */
  seeded: Promise<boolean>;
}

/**
 * 사진 한 장을 서버에 올린다 (준비 → upload-url → PUT → complete).
 * @throws ApiError. 서버에 사진 API가 없으면 code가 IMAGES_UNSUPPORTED(→ 기기 사진으로 되돌아간다)
 */
export const uploadPhoto = async (uri: string): Promise<IUploadedPhoto> => {
  if (imagesKnownUnsupported()) throw unsupportedError();
  return uploadPrepared(await preparePhoto(uri));
};

/** preparePhoto로 준비한 사진을 올린다. 오류는 uploadPhoto와 같다 */
export const uploadPrepared = async (prepared: IPreparedPhoto): Promise<IUploadedPhoto> => {
  if (imagesKnownUnsupported()) throw unsupportedError();
  const { body, size } = await readBody(prepared.uri);

  let target;
  try {
    target = await api.images.uploadUrl({ purpose: 'ITEM', contentType: prepared.contentType, size });
  } catch (e) {
    if (isImagesUnsupported(e)) {
      support.markUnsupported(Date.now());
      throw unsupportedError(e);
    }
    throw e;
  }
  support.markSupported();
  await api.images.put(target, body);
  const done = await api.images.complete(target.imageId);
  const image: ImageRef = { imageId: done.imageId, url: done.url, thumbUrl: done.thumbUrl, expiresAt: done.expiresAt };
  return { image, localUri: prepared.uri, seeded: seedImageCache(image.imageId, prepared.uri) };
};

/**
 * 올린 사진 파일로 expo-image 디스크 캐시를 채운다 (원본 키·썸네일 키).
 * 그러면 서버 주소를 처음 그릴 때 내려받지 않고, 기기 사진을 지워도 화면이 끊기지 않는다. 웹은 하지 않는다.
 */
export const seedImageCache = async (imageId: string, localUri: string): Promise<boolean> => {
  if (isWeb) return false;
  try {
    await Image.writeToCacheAsync(localUri, imageCacheKey(imageId, 'full'));
    const thumb = await toJpeg(localUri, THUMB_SIDE, 0.8);
    await Image.writeToCacheAsync(thumb, imageCacheKey(imageId, 'thumb'));
    return true;
  } catch (e) {
    console.warn('[photo] 사진 캐시 채우기 실패', e);
    return false;
  }
};

// ── 앱 문서 폴더의 기기 사진 ──

/** 기기에만 두는 사진 폴더: Paths.document/photos */
export const photoDir = (): Directory => new Directory(Paths.document, 'photos');

/**
 * 기기 사진을 앱 문서 폴더(photos/{itemId}.jpg)로 복사하고 새 주소를 돌려준다.
 * 선택기 캐시는 OS가 지울 수 있기 때문이다. 이미 폴더 안이거나 샘플·웹 주소면 그대로, 실패하면 원래 주소.
 * @param version 사진을 바꿀 때 붙이는 버전 (photos/{itemId}-{version}.jpg, photoRules.photoFileName)
 */
export const keepPhotoLocally = async (
  uri: string | undefined,
  itemId: string,
  version?: string,
): Promise<string | undefined> => {
  if (!uri || isWeb) return uri;
  try {
    const dir = photoDir();
    if (!needsLocalCopy(uri, dir.uri)) return uri;
    const src = new File(uri);
    if (!src.exists) return uri;
    dir.create({ intermediates: true, idempotent: true });
    const dest = new File(dir, photoFileName(itemId, version));
    await src.copy(dest, { overwrite: true });
    return dest.uri;
  } catch (e) {
    console.warn('[photo] 사진을 앱 폴더로 옮기지 못했어요', e);
    return uri;
  }
};

/** 기기 사진 파일이 남아 있는지 (주소가 이상하면 false) */
export const localPhotoExists = (uri: string): boolean => {
  if (isWeb) return true;
  try {
    return new File(uri).exists;
  } catch {
    return false;
  }
};

/** 앱 사진 폴더 안의 기기 사진 파일을 지운다 (바꾸거나 뺀 예전 사진). 실패해도 다음 시작 때 정리 작업이 지운다 */
export const deletePhotoFiles = (uris: string[]): void => {
  if (isWeb) return;
  for (const uri of uris) {
    try {
      const file = new File(uri);
      if (file.exists) file.delete();
    } catch (e) {
      console.warn('[photo] 예전 사진 파일을 지우지 못했어요', uri, e);
    }
  }
};
