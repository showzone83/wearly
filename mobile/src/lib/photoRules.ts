// 옷 사진 서버 저장의 순수 규칙 (React Native·Expo를 쓰지 않는다. 단위 점검을 Node에서 바로 돌린다).
// 흐름은 src/lib/photoUpload.ts(올리기)·src/lib/photoSync.ts(옮기기·주소 갱신), 화면은 src/components/ItemThumb.tsx.
import { isApiError } from '@/api/errors';
import type { IItemImage, WardrobeItem } from '@/data/types';

// ── 표시 ──

/** 목록은 썸네일(긴 변 400), 상세는 원본(긴 변 2048) */
export type TImageVariant = 'thumb' | 'full';

/**
 * expo-image 디스크 캐시 키. 서명 주소는 받을 때마다 바뀌므로 주소 대신 imageId로 캐시한다.
 * 올린 직후에는 기기에 있는 파일로 같은 키를 미리 채워 둔다(photoUpload.seedImageCache).
 */
export const imageCacheKey = (imageId: string, variant: TImageVariant): string => `img-${imageId}-${variant}`;

export interface IServerImageSource {
  uri: string;
  cacheKey: string;
}

/** 서버 사진 → expo-image source. 사진이 없거나 주소가 비었으면 undefined */
export const serverImageSource = (
  image: IItemImage | null | undefined,
  variant: TImageVariant,
): IServerImageSource | undefined => {
  if (!image?.imageId) return undefined;
  const uri = variant === 'full' ? image.url || image.thumbUrl : image.thumbUrl || image.url;
  return uri ? { uri, cacheKey: imageCacheKey(image.imageId, variant) } : undefined;
};

// ── 서버 지원 여부 ──

/** 앱이 만드는 오류 코드: 서버에 사진 API가 없다 (예전 서버) */
export const IMAGES_UNSUPPORTED = 'IMAGES_UNSUPPORTED';

/**
 * upload-url 요청 실패가 "서버에 이 API가 없다"는 뜻인지. 예전 서버는 모르는 경로에 404(또는 405·501)를 준다.
 * upload-url은 남의 이미지를 다루지 않으므로 여기서 404는 기능 없음으로 본다.
 */
export const isImagesUnsupported = (e: unknown): boolean =>
  isApiError(e) && (e.code === IMAGES_UNSUPPORTED || [404, 405, 501].includes(e.status));

/** 기능 없음을 한 번 확인하면 이 시간 동안은 다시 묻지 않는다 (서버를 올리면 그 뒤에 다시 시도) */
export const UNSUPPORTED_RECHECK_MS = 10 * 60_000;

export const createSupportMemo = (recheckMs = UNSUPPORTED_RECHECK_MS) => {
  let unsupportedAt: number | null = null;
  return {
    markUnsupported: (now: number) => {
      unsupportedAt = now;
    },
    markSupported: () => {
      unsupportedAt = null;
    },
    /** 최근에 기능 없음을 확인했으면 true */
    knownUnsupported: (now: number) => unsupportedAt !== null && now - unsupportedAt < recheckMs,
  };
};

// ── 기존 기기 사진 옮기기 (계약 4장) ──

/** 아이템별 옮기기 기록. 기기에 저장한다 */
export interface IPhotoSyncEntry {
  /** 연속 실패 횟수 (오프라인·한도 초과는 세지 않는다) */
  fails: number;
  /** 마지막 시도 시각(ms) */
  lastAt: number;
  /** 기기 파일이 이미 사라져 옮길 수 없음 */
  gone?: boolean;
}

/** 한 번에 옮기는 장 수 */
export const MIGRATION_BATCH = 3;
/** 이만큼 실패하면 그 사진은 더 시도하지 않는다 */
export const MIGRATION_MAX_FAILS = 8;
const RETRY_BASE_MS = 5 * 60_000;
const RETRY_MAX_MS = 24 * 60 * 60_000;

/** 실패 n번 뒤 다음 시도까지 기다릴 시간: 5분, 10분, 20분 … 최대 하루 */
export const retryDelayMs = (fails: number): number =>
  fails <= 0 ? 0 : Math.min(RETRY_BASE_MS * 2 ** (fails - 1), RETRY_MAX_MS);

export const canTryMigration = (entry: IPhotoSyncEntry | undefined, now: number): boolean => {
  if (!entry) return true;
  if (entry.gone || entry.fails >= MIGRATION_MAX_FAILS) return false;
  return now - entry.lastAt >= retryDelayMs(entry.fails);
};

export const afterFailure = (entry: IPhotoSyncEntry | undefined, now: number): IPhotoSyncEntry => ({
  fails: (entry?.fails ?? 0) + 1,
  lastAt: now,
});

/** 기기 파일 주소 (샘플 wearly-sample://, 웹 blob:·data:는 아니다) */
export const isLocalFileUri = (uri: string | undefined): uri is string =>
  typeof uri === 'string' && uri.startsWith('file://');

export interface IMigrationCandidate {
  id: string;
  uri: string;
}

/** 서버 사진이 없고 기기 사진 파일이 있으며 지금 시도해도 되는 아이템 (최대 limit개) */
export const migrationCandidates = (
  items: Pick<WardrobeItem, 'id' | 'image'>[],
  photos: Record<string, string>,
  sync: Record<string, IPhotoSyncEntry>,
  now: number,
  limit = MIGRATION_BATCH,
): IMigrationCandidate[] => {
  const out: IMigrationCandidate[] = [];
  for (const item of items) {
    if (out.length >= limit) break;
    const uri = photos[item.id];
    if (item.image || !isLocalFileUri(uri) || !canTryMigration(sync[item.id], now)) continue;
    out.push({ id: item.id, uri });
  }
  return out;
};

/**
 * 옮기기 실패를 어떻게 다룰지.
 * - stopUnsupported: 서버에 사진 API가 없다 → 이번 묶음 중단, 한동안 묻지 않음
 * - stop: 오프라인·로그인 문제·하루 한도 → 이번 묶음 중단, 실패로 세지 않음(다음 기회에 다시)
 * - skipItem: 붙이려던 아이템이 서버에 없다(다른 기기에서 삭제) → 다음 동기화가 기기에서도 지운다
 * - countFail: 그 밖의 실패 → 실패 횟수를 올리고 나중에 다시
 */
export type TMigrationErrorAction = 'stopUnsupported' | 'stop' | 'skipItem' | 'countFail';

export const migrationErrorAction = (e: unknown, stage: 'upload' | 'attach'): TMigrationErrorAction => {
  if (!isApiError(e)) return 'countFail';
  if (e.code === IMAGES_UNSUPPORTED) return 'stopUnsupported';
  if (e.code === 'NETWORK' || e.status === 401) return 'stop';
  if (e.code === 'IMAGE_UPLOAD_LIMIT' || e.status === 429) return 'stop';
  if (stage === 'attach' && e.status === 404) return 'skipItem';
  return 'countFail';
};

// ── 앱 문서 폴더의 기기 사진 ──

/** 아이템 사진 파일 이름: photos/{itemId}.jpg (id의 특수 문자는 _로) */
export const photoFileName = (itemId: string): string => `${itemId.replace(/[^A-Za-z0-9_-]/g, '_')}.jpg`;

/** 앱 문서 폴더로 옮겨 둘 사진인지: 기기 파일인데 아직 사진 폴더 밖(선택기 캐시 등)에 있다 */
export const needsLocalCopy = (uri: string | undefined, photoDirUri: string): uri is string =>
  isLocalFileUri(uri) && !uri.startsWith(withSlash(photoDirUri));

/** 사진 폴더 안의 파일 중 어디에서도 쓰지 않는 파일 이름 (지울 대상) */
export const orphanPhotoNames = (listedNames: string[], referencedUris: (string | undefined)[], photoDirUri: string): string[] => {
  const dir = withSlash(photoDirUri);
  const used = new Set<string>();
  for (const uri of referencedUris) {
    if (!uri?.startsWith(dir)) continue;
    used.add(decodeURIComponent(uri.slice(dir.length).split(/[?#]/)[0]));
  }
  return listedNames.filter((name) => !used.has(name) && !used.has(decodeURIComponent(name)));
};

const withSlash = (uri: string): string => (uri.endsWith('/') ? uri : `${uri}/`);

// ── 만료 주소 다시 받기 ──

export interface IRefreshGateOptions {
  /** 실제로 아이템을 다시 받는 일 */
  run: () => Promise<unknown>;
  /** 같은 아이템은 이 시간 안에 다시 요청하지 않는다. 기본 10분 */
  perItemMs?: number;
  /** 다시 받기 사이의 최소 간격. 기본 30초 */
  minIntervalMs?: number;
  /** 여러 사진의 실패를 한 번에 모으는 시간. 기본 300ms */
  debounceMs?: number;
  now?: () => number;
  setTimer?: (fn: () => void, ms: number) => unknown;
}

/**
 * 사진 로드 실패(서명 주소 만료 등) → 아이템 다시 받기를 과하게 반복하지 않게 막는다.
 * 여러 사진이 한꺼번에 실패해도 목록 요청은 한 번, 같은 사진은 perItemMs에 한 번만 요청한다.
 * @returns request(itemId): 다시 받기를 예약했으면 true
 */
export const createRefreshGate = (opts: IRefreshGateOptions) => {
  const perItemMs = opts.perItemMs ?? 10 * 60_000;
  const minIntervalMs = opts.minIntervalMs ?? 30_000;
  const debounceMs = opts.debounceMs ?? 300;
  const now = opts.now ?? (() => Date.now());
  const setTimer = opts.setTimer ?? ((fn: () => void, ms: number) => setTimeout(fn, ms));
  const lastByItem = new Map<string, number>();
  let lastRun = -Infinity;
  let scheduled = false;
  let running = false;
  let pending = false;

  const flush = async () => {
    scheduled = false;
    if (running) {
      pending = true;
      return;
    }
    const wait = lastRun + minIntervalMs - now();
    if (wait > 0) {
      schedule(wait);
      return;
    }
    running = true;
    pending = false;
    lastRun = now();
    try {
      await opts.run();
    } catch {
      // 실패해도 다음 요청 때 다시 (perItemMs가 반복을 막는다)
    } finally {
      running = false;
      if (pending) schedule(debounceMs);
    }
  };

  const schedule = (ms: number) => {
    if (scheduled) return;
    scheduled = true;
    setTimer(() => void flush(), ms);
  };

  return {
    request: (itemId: string): boolean => {
      const t = now();
      const last = lastByItem.get(itemId);
      if (last !== undefined && t - last < perItemMs) return false;
      lastByItem.set(itemId, t);
      if (running) pending = true;
      else schedule(debounceMs);
      return true;
    },
  };
};
