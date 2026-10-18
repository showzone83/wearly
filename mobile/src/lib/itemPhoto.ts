// 옷 상세의 사진 바꾸기·빼기·다시 분석 (이슈 #8). 규칙은 src/lib/photoEditRules.ts, 화면은 src/app/item/[id].tsx.
// - 계정 모드: 새 사진을 올리고(긴 변 1600 JPEG 0.8 → upload-url → PUT → complete) PATCH imageId. 빼기는 PATCH imageId: null.
//   서버는 예전 사진 객체를 커밋 뒤 지운다 (doc/18 7.2). 사진 기능이 없는 예전 서버면 기기에 두고 나중에 옮긴다.
// - 게스트: 기기 사진만 바꾼다 (앱 문서 폴더 photos/{itemId}-{버전}.jpg로 복사하고 예전 파일은 지운다).
// - 아직 서버로 못 옮긴 기기 사진을 바꾸거나 빼면 옮기기 대기 기록(photoSync)도 지운다.
import * as ImagePicker from 'expo-image-picker';
import { Platform } from 'react-native';

import { isApiError } from '@/api/errors';
import type { ItemDraft } from '@/components/ItemForm';
import type { IItemImage } from '@/data/types';
import { useStore } from '@/store/useStore';
import { isAccountMode } from './account';
import { api } from './api';
import { mockAnalyze } from './mockAnalyze';
import { MOCK_MODE } from './mode';
import { canOfferReanalysis, photoVersion, planPhotoChange, type TPhotoChange } from './photoEditRules';
import { IMAGES_UNSUPPORTED } from './photoRules';
import { holdPhotoMigration } from './photoSync';
import { deletePhotoFiles, keepPhotoLocally, photoDir, uploadPhoto } from './photoUpload';
import { draftFromSuggestion, imageFromDto } from './serverItems';

const isWeb = Platform.OS === 'web';

// ── 고르기 ──

export type TPickResult =
  | { kind: 'picked'; uri: string; width?: number; height?: number }
  | { kind: 'canceled' }
  /** 카메라 권한 없음 */
  | { kind: 'denied' };

/** 사진 고르기 (옷 등록과 같은 옵션: 정사각형 자르기, EXIF 없음). 계정 모드는 올릴 때 한 번만 압축한다 */
export const pickItemPhoto = async (source: 'camera' | 'library'): Promise<TPickResult> => {
  if (source === 'camera' && !isWeb) {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) return { kind: 'denied' };
  }
  const quality = isAccountMode() ? 1 : 0.6;
  const opts: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality, exif: false };
  const res = source === 'camera' ? await ImagePicker.launchCameraAsync(opts) : await ImagePicker.launchImageLibraryAsync(opts);
  const asset = res.canceled ? undefined : res.assets[0];
  if (!asset) return { kind: 'canceled' };
  return { kind: 'picked', uri: asset.uri, width: asset.width, height: asset.height };
};

// ── 기기 상태 반영 ──

const photoDirUri = (): string => {
  if (isWeb) return '';
  try {
    return photoDir().uri;
  } catch {
    return '';
  }
};

/** 바꾼(뺀) 사진을 기기 상태에 반영하고 예전 기기 파일을 지운다 */
const applyPhotoChange = (id: string, change: TPhotoChange): void => {
  const st = useStore.getState();
  const item = st.items.find((i) => i.id === id);
  if (!item) return;
  const account = st.authMode === 'account';
  const plan = planPhotoChange(
    { image: item.image, imageUri: item.imageUri, localPhoto: account ? st.photos[id] : undefined },
    change,
    photoDirUri(),
  );
  st.updateItem(id, plan.item);
  if (account) st.setPhoto(id, plan.localPhoto);
  if (plan.clearSync) st.setPhotoSync(id, undefined);
  deletePhotoFiles(plan.deleteFiles);
};

/** 기기 사진으로 둘 때: 앱 문서 폴더에 새 이름으로 복사 (실패하면 고른 주소 그대로) */
const keepNewLocalPhoto = async (uri: string, id: string): Promise<string> =>
  (await keepPhotoLocally(uri, id, photoVersion(Date.now()))) ?? uri;

/** PATCH가 404일 때: 아이템이 정말 없는지 확인한다 (없으면 기기에서도 지운다) */
const itemGone = async (id: string, e: unknown): Promise<boolean> => {
  if (!isApiError(e) || e.status !== 404) return false;
  try {
    await api.wardrobe.get(id);
    return false;
  } catch (inner) {
    if (!(isApiError(inner) && inner.status === 404)) return false;
    useStore.getState().deleteItem(id);
    return true;
  }
};

// ── 바꾸기 · 빼기 ──

export type TPhotoResult =
  /** 서버에 올려 붙였다 */
  | { kind: 'server'; image: IItemImage }
  /** 기기에 두었다 (게스트, 사진 기능이 없는 예전 서버) */
  | { kind: 'local'; uri: string }
  /** 다른 기기에서 지운 옷이라 기기에서도 지웠다 */
  | { kind: 'gone' };

/**
 * 옷 사진을 바꾼다. 실패하면 기기 상태는 그대로다.
 * @throws ApiError (오류 문구는 errorText. 화면은 '다시 시도'를 보여 준다)
 */
export const replaceItemPhoto = async (id: string, uri: string): Promise<TPhotoResult> => {
  if (!isAccountMode()) {
    const local = await keepNewLocalPhoto(uri, id);
    applyPhotoChange(id, { kind: 'local', uri: local });
    return { kind: 'local', uri: local };
  }
  const release = await holdPhotoMigration(id);
  try {
    let image: IItemImage;
    try {
      image = (await uploadPhoto(uri)).image;
    } catch (e) {
      if (!(isApiError(e) && e.code === IMAGES_UNSUPPORTED)) throw e;
      // 예전 서버: 기기에 두고, 서버에 사진 기능이 생기면 옮기기가 올린다
      const local = await keepNewLocalPhoto(uri, id);
      applyPhotoChange(id, { kind: 'local', uri: local });
      return { kind: 'local', uri: local };
    }
    let attached: IItemImage;
    try {
      const dto = await api.wardrobe.update(id, { imageId: image.imageId });
      attached = imageFromDto(dto.image) ?? image;
    } catch (e) {
      if (await itemGone(id, e)) return { kind: 'gone' };
      throw e; // 올린 사진은 아이템에 안 붙어 서버 정리 작업이 하루 뒤 지운다
    }
    applyPhotoChange(id, { kind: 'server', image: attached });
    return { kind: 'server', image: attached };
  } finally {
    release();
  }
};

/**
 * 옷 사진을 뺀다. 서버 사진이 있으면 PATCH imageId: null, 기기에만 있으면 기기에서만 지운다.
 * @returns false면 다른 기기에서 지운 옷이라 기기에서도 지웠다
 */
export const removeItemPhoto = async (id: string): Promise<boolean> => {
  if (!isAccountMode()) {
    applyPhotoChange(id, { kind: 'removed' });
    return true;
  }
  const release = await holdPhotoMigration(id);
  try {
    const item = useStore.getState().items.find((i) => i.id === id);
    if (item?.image) {
      try {
        await api.wardrobe.update(id, { imageId: null });
      } catch (e) {
        if (await itemGone(id, e)) return false;
        throw e;
      }
    }
    applyPhotoChange(id, { kind: 'removed' });
    return true;
  } finally {
    release();
  }
};

// ── AI 다시 분석 ──

/** 사진을 바꾼 뒤 AI 다시 분석을 제안할지 (계정 모드는 서버 사진, 목업 실행은 게스트도) */
export const reanalysisAvailable = (image: IItemImage | undefined): boolean =>
  canOfferReanalysis({ accountMode: isAccountMode(), hasServerImage: !!image?.imageId, mockMode: MOCK_MODE });

export interface IReanalysis {
  /** AI 제안을 폼 값으로 바꾼 것 (종류·세부 종류 짝은 코드 기준으로 맞춤) */
  suggested: ItemDraft;
  /** 오늘 남은 AI 분석 횟수 (서버 분석일 때만) */
  remaining?: number;
}

/**
 * 지금 사진으로 AI 분석을 다시 한다 (사용자가 누를 때만 부른다).
 * 계정 모드는 POST /wardrobe/items/analyze { imageId } (하루 한도에 들어간다), 목업 실행 게스트는 기기 목업 분석.
 * @throws ApiError (AI_LIMIT_EXCEEDED 등. 문구는 errorText)
 */
export const reanalyzeItemPhoto = async (item: { id: string; image?: IItemImage; imageUri?: string }): Promise<IReanalysis> => {
  if (isAccountMode()) {
    if (!item.image) throw new Error('서버 사진이 없어요');
    const res = await api.wardrobe.analyzeImage({ imageId: item.image.imageId });
    return { suggested: draftFromSuggestion(res.suggestion, item.imageUri ?? '', item.image), remaining: res.remainingToday };
  }
  await new Promise((r) => setTimeout(r, 600)); // 목업: 진행 표시가 보이게
  return { suggested: mockAnalyze(item.imageUri ?? item.id) };
};
