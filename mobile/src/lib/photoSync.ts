// 기기 사진 관리 (계약 4장).
// - 시작할 때: 선택기 캐시에 있는 기기 사진을 앱 문서 폴더로 복사하고, 쓰지 않는 사진 파일을 지운다.
// - 계정 모드 시작·복귀 때: 서버 사진이 없는 아이템의 기기 사진을 몇 장씩 서버로 옮긴다 (실패는 다음 기회에).
// - 서버 사진 로드 실패(서명 주소 만료) 때: 옷장을 다시 받아 새 주소로 (과한 반복 요청은 막는다).
import { File } from 'expo-file-system';
import { Platform } from 'react-native';

import { useStore } from '@/store/useStore';
import { isAccountMode, syncWardrobe, waitForAccountRefresh, wardrobeSyncedAt } from './account';
import { api } from './api';
import {
  afterFailure,
  createRefreshGate,
  migrationCandidates,
  migrationErrorAction,
  needsLocalCopy,
  orphanPhotoNames,
} from './photoRules';
import { imagesKnownUnsupported, keepPhotoLocally, localPhotoExists, photoDir, uploadPhoto } from './photoUpload';
import { imageFromDto } from './serverItems';

const isWeb = Platform.OS === 'web';

/** 복귀 때 옷장(사진 주소)을 다시 받는 최소 간격. 서명 주소 유효 시간(기본 1시간)보다 충분히 짧게 */
const RESUME_SYNC_MS = 2 * 60_000;

// ── 만료 주소 다시 받기 ──

const refreshGate = createRefreshGate({ run: () => syncWardrobe() });

/**
 * 서버 사진을 그리지 못했을 때(서명 주소 만료 등) 부른다. 계정 모드에서만, 같은 사진은 10분에 한 번,
 * 여러 사진은 한 번의 목록 요청으로 모아 새 주소를 받는다.
 */
export const requestImageRefresh = (itemId: string): void => {
  if (!isAccountMode() || useStore.getState().authLost) return;
  refreshGate.request(itemId);
};

// ── 기기 사진을 앱 문서 폴더로 ──

/**
 * 선택기 캐시 등 OS가 지울 수 있는 곳에 있는 기기 사진을 앱 문서 폴더로 복사한다.
 * 게스트는 items[].imageUri, 계정 모드는 서버 사진이 없는 아이템의 photos[id].
 */
const localizePhotos = async (): Promise<void> => {
  const dirUri = photoDir().uri;
  const s = useStore.getState();
  if (s.authMode === 'account') {
    const withImage = new Set(s.items.filter((i) => i.image).map((i) => i.id));
    for (const [id, uri] of Object.entries(s.photos)) {
      if (withImage.has(id) || !needsLocalCopy(uri, dirUri)) continue;
      const next = await keepPhotoLocally(uri, id);
      if (!next || next === uri) continue;
      const now = useStore.getState();
      if (now.photos[id] !== uri) continue; // 그사이 바뀌었으면 두지 않는다
      now.setPhoto(id, next);
      now.updateItem(id, { imageUri: next });
    }
    return;
  }
  for (const item of s.items) {
    const uri = item.imageUri;
    if (!needsLocalCopy(uri, dirUri)) continue;
    const next = await keepPhotoLocally(uri, item.id);
    if (!next || next === uri) continue;
    const now = useStore.getState();
    if (now.items.find((i) => i.id === item.id)?.imageUri !== uri) continue;
    now.updateItem(item.id, { imageUri: next });
  }
};

/** 앱 문서 폴더의 사진 중 어떤 아이템도 쓰지 않는 파일을 지운다 (삭제·로그아웃·서버로 옮긴 사진) */
const cleanupPhotoDir = (): void => {
  try {
    const dir = photoDir();
    if (!dir.exists) return;
    const s = useStore.getState();
    const refs = [...Object.values(s.photos), ...s.items.map((i) => i.imageUri)];
    const files = dir.list().filter((f): f is File => f instanceof File);
    const orphans = new Set(orphanPhotoNames(files.map((f) => f.name), refs, dir.uri));
    for (const f of files) {
      if (!orphans.has(f.name)) continue;
      try {
        f.delete();
      } catch (e) {
        console.warn('[photo] 쓰지 않는 사진 파일을 지우지 못했어요', f.uri, e);
      }
    }
  } catch (e) {
    console.warn('[photo] 사진 폴더 정리 실패', e);
  }
};

// ── 서버로 옮기기 ──

let migrating = false;

/** 옷 상세에서 사진을 바꾸거나 빼는 중인 아이템. 옮기기가 건너뛴다 (예전 사진을 새 사진 위에 붙이지 않게) */
const held = new Set<string>();
/** 지금 옮기는 중인 아이템과 그 작업 (끝나면 비운다) */
let inFlight: { id: string; done: Promise<void> } | null = null;

/**
 * 사진 바꾸기·빼기 동안 그 아이템의 옮기기를 막는다. 이미 그 아이템을 옮기는 중이면 끝날 때까지 기다린다.
 * @returns 막기를 푸는 함수 (꼭 부른다)
 */
export const holdPhotoMigration = async (id: string): Promise<() => void> => {
  held.add(id);
  if (inFlight?.id === id) await inFlight.done.catch(() => undefined);
  return () => {
    held.delete(id);
  };
};

/**
 * 계정 모드: 서버 사진이 없고 기기 사진 파일이 남은 아이템을 한 번에 몇 장씩 서버로 올리고 PATCH imageId.
 * - 파일이 사라졌으면 건너뛰고 기록(gone)한다. 샘플(wearly-sample://)·웹 주소는 대상이 아니다.
 * - 오프라인·하루 한도·서버에 기능 없음이면 이번에는 멈춘다(실패로 세지 않음).
 * - 그 밖의 실패는 아이템별 실패 횟수·시각을 남겨 점점 늦게 다시 시도하고, 여러 번 실패하면 그만둔다.
 * - 성공하면 화면 캐시를 채운 뒤 기기 사진을 지운다 (파일은 다음 시작 때 정리).
 */
export const migratePhotos = async (): Promise<void> => {
  if (migrating || isWeb || !isAccountMode() || imagesKnownUnsupported()) return;
  migrating = true;
  try {
    const s0 = useStore.getState();
    if (s0.authLost) return;
    const candidates = migrationCandidates(s0.items, s0.photos, s0.photoSync, Date.now());
    for (const { id, uri } of candidates) {
      if (!isAccountMode()) break;
      if (held.has(id)) continue;
      let finish = () => {};
      inFlight = { id, done: new Promise<void>((r) => (finish = r)) };
      try {
        if ((await migrateOne(id, uri)) === 'stop') break;
      } finally {
        inFlight = null;
        finish();
      }
    }
  } finally {
    migrating = false;
  }
};

/** 기기 사진 한 장을 서버로 옮긴다. 'stop'이면 이번 묶음을 멈춘다 */
const migrateOne = async (id: string, uri: string): Promise<'stop' | 'next'> => {
  if (!localPhotoExists(uri)) {
    useStore.getState().setPhotoSync(id, { fails: 0, lastAt: Date.now(), gone: true });
    useStore.getState().setPhoto(id, undefined);
    useStore.getState().updateItem(id, { imageUri: undefined });
    console.warn('[photo] 기기 사진 파일이 없어 옮기지 않아요', id, uri);
    return 'next';
  }

  let uploaded;
  try {
    uploaded = await uploadPhoto(uri);
  } catch (e) {
    return handleFailure(id, e, 'upload');
  }

  // 올리는 동안 옷 상세에서 사진을 바꾸거나 뺐으면 예전 사진을 붙이지 않는다 (올린 것은 서버 정리 작업이 지운다)
  if (held.has(id) || useStore.getState().photos[id] !== uri) return 'next';
  let dto;
  try {
    dto = await api.wardrobe.update(id, { imageId: uploaded.image.imageId });
  } catch (e) {
    return handleFailure(id, e, 'attach');
  }

  const st = useStore.getState();
  if (st.photos[id] !== uri) return 'next'; // 그사이 사진이 바뀌었거나 아이템이 지워졌다
  const image = imageFromDto(dto.image) ?? uploaded.image;
  st.setPhotoSync(id, undefined);
  // 화면 캐시를 채웠으면 기기 사진을 지운다. 못 채웠으면 서버 사진이 안 뜰 때를 위해 남겨 둔다
  if (await uploaded.seeded) {
    useStore.getState().setPhoto(id, undefined);
    useStore.getState().updateItem(id, { image, imageUri: undefined });
  } else {
    useStore.getState().updateItem(id, { image });
  }
  return 'next';
};

const handleFailure = (id: string, e: unknown, stage: 'upload' | 'attach'): 'stop' | 'next' => {
  const action = migrationErrorAction(e, stage);
  if (action === 'stopUnsupported' || action === 'stop') return 'stop';
  if (action === 'countFail') {
    const st = useStore.getState();
    st.setPhotoSync(id, afterFailure(st.photoSync[id], Date.now()));
    console.warn('[photo] 기기 사진을 서버로 옮기지 못했어요 (다음에 다시)', id, e);
  }
  return 'next';
};

// ── 시작 · 복귀 ──

let started = false;

/** 앱 시작 때 한 번 (세션 확인 뒤). 화면을 막지 않는다 */
export const startPhotoMaintenance = async (): Promise<void> => {
  if (started || isWeb) return;
  started = true;
  try {
    await localizePhotos();
    cleanupPhotoDir();
    if (isAccountMode()) {
      await waitForAccountRefresh();
      await migratePhotos();
    }
  } catch (e) {
    console.warn('[photo] 시작 정리 실패', e);
  }
};

/** 앱이 다시 활성이 될 때: 사진 주소가 오래됐으면 옷장을 다시 받고, 남은 기기 사진을 옮긴다 */
export const onAppActive = async (): Promise<void> => {
  if (!isAccountMode() || useStore.getState().authLost) return;
  try {
    if (Date.now() - wardrobeSyncedAt() > RESUME_SYNC_MS) await syncWardrobe();
    await migratePhotos();
  } catch (e) {
    console.warn('[photo] 복귀 동기화 실패', e);
  }
};
