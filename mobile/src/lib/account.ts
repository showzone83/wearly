// 계정 모드 흐름: 로그인 후 상태 구성, 세션 복구, 서버 동기화, 옷장·프로필 저장.
// 게스트 모드에서는 모든 함수가 기존처럼 기기 안 저장소만 바꾼다.
import type { ItemDto, UserDto } from '@/api/types';
import type { ItemDraft } from '@/components/ItemForm';
import { seedItems } from '@/data/seed';
import type { Profile, WardrobeItem } from '@/data/types';
import { ApiError, isApiError } from '@/api/errors';
import { useStore, type AccountUser } from '@/store/useStore';
import { api, setAuthLostHandler, tokenStore } from './api';
import { newId } from './context';
import { purchaseFields } from './itemAttrs';
import { keepPhotoLocally } from './photoUpload';
import { unregisterPushToken } from './planNotify';
import { syncPlans } from './planSync';
import { RETIRE_MEMO_MAX } from './retire';
import {
  imageFromDto,
  inputFromDraft,
  inputFromItem,
  itemFromDto,
  profileFromDto,
  profileToDto,
  retireFromDto,
} from './serverItems';

/** 옷장 동기화 목록: 사용하지 않는 옷도 받아 과거 기록에 보이게 한다 (doc/13 3장) */
const ALL_ITEMS = { retired: 'all' } as const;

export function isAccountMode(): boolean {
  return useStore.getState().authMode === 'account';
}

function toAccountUser(u: UserDto): AccountUser {
  return { id: u.id, email: u.email, name: u.name };
}

// ── 로그인 상태 잃음 ──

/**
 * Refresh 실패·토큰 무효: 기기 데이터는 두고 로그인 화면으로 보낸다 (이동은 _layout의 AuthWatcher가 한다).
 * TOKEN_INVALID는 관리자가 강제 로그아웃했거나 계정이 정지·탈퇴된 경우다 → "다른 곳에서 로그아웃" 안내 (doc/16 6장)
 */
setAuthLostHandler((reason) => {
  const s = useStore.getState();
  if (s.authMode !== 'account' || s.authLost) return;
  useStore.setState({ authLost: true, authLostReason: reason.code === 'TOKEN_INVALID' ? 'elsewhere' : 'expired' });
});

// ── 서버 아이템 합치기 ──

/** 마지막으로 서버 옷장 목록을 받은 시각(ms). 사진 주소(서명 URL) 갱신 간격을 정할 때 쓴다 */
let wardrobeSyncedAtMs = 0;
export const wardrobeSyncedAt = (): number => wardrobeSyncedAtMs;

/**
 * 서버 목록을 기기 상태와 합친다.
 * - 서버 사진(image)은 응답에 있고, 기기에만 있는 사진(photos[id])도 id별로 붙인다.
 * - 착용 기록은 이번 단계에서 기기에만 저장하므로, 착용 횟수·마지막 착용일은 더 큰 쪽을 쓰고
 *   기기 쪽이 앞서 있으면 서버에 다시 올린다.
 */
function applyServerItems(dtos: ItemDto[]) {
  const s = useStore.getState();
  const local = new Map(s.items.map((i) => [i.id, i]));
  const behind: WardrobeItem[] = [];
  const items = dtos.map((dto) => {
    const item = itemFromDto(dto, s.photos[dto.id]);
    const mine = local.get(dto.id);
    if (!mine) return item;
    const wornCount = Math.max(item.wornCount, mine.wornCount);
    const lastWornAt = [item.lastWornAt, mine.lastWornAt].filter(Boolean).sort().pop();
    const merged = { ...item, wornCount, lastWornAt };
    if (wornCount !== item.wornCount || lastWornAt !== item.lastWornAt) behind.push(merged);
    return merged;
  });
  s.syncItems(items);
  wardrobeSyncedAtMs = Date.now();
  if (behind.length) void pushWorn(behind.map((i) => i.id));
}

// ── 로그인 · 세션 ──

/**
 * 로그인·가입 직후 서버 상태로 기기 상태를 맞춘다.
 * 다른 계정(또는 게스트)이 쓰던 기기 데이터는 지운다. 같은 계정으로 다시 로그인하면 코디·기록을 유지한다.
 * @returns 다음에 갈 화면
 */
export async function enterAccount(user: UserDto): Promise<'home' | 'onboarding'> {
  const profileDto = await api.profile.get();
  const list = profileDto.onboarded ? await api.wardrobe.list(ALL_ITEMS) : null;

  const s = useStore.getState();
  const sameUser = s.authMode === 'account' && s.user?.id === user.id && s.onboarded;
  if (!sameUser || !profileDto.onboarded) s.resetAll();

  // 가입 직후 프로필은 기본값이라 이름이 비어 있거나 기본 이름일 수 있다. 가입 때 쓴 이름을 우선한다
  const profile = profileFromDto(profileDto);
  if (!profileDto.onboarded) profile.name = user.name;

  useStore.setState({
    authMode: 'account',
    user: toAccountUser(user),
    authLost: false,
    authLostReason: null,
    profile,
    onboarded: profileDto.onboarded,
  });
  if (list) applyServerItems(list.items);
  // 예정 코디: 기기 예정을 올리고 서버 예정과 합친다 (화면을 막지 않는다)
  if (profileDto.onboarded) void syncPlans({ force: true });
  return profileDto.onboarded ? 'home' : 'onboarding';
}

/** 진행 중인 refreshAccount. 사진 옮기기가 옷장 동기화가 끝난 뒤 시작하도록 기다린다 */
let refreshing: Promise<void> | null = null;

/** 진행 중인 계정 동기화가 있으면 끝날 때까지 기다린다 (실패해도 resolve) */
export const waitForAccountRefresh = (): Promise<void> => refreshing ?? Promise.resolve();

/** 앱 시작·복귀 때 서버 기준으로 프로필·옷장을 다시 맞춘다. 오프라인이면 기기 데이터로 계속한다 */
export function refreshAccount(): Promise<void> {
  if (!refreshing) {
    refreshing = doRefreshAccount().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function doRefreshAccount(): Promise<void> {
  try {
    const [user, profileDto, list] = await Promise.all([api.users.me(), api.profile.get(), api.wardrobe.list(ALL_ITEMS)]);
    const s = useStore.getState();
    if (s.authMode !== 'account' || s.user?.id !== user.id) return;
    useStore.setState({ user: toAccountUser(user), profile: profileFromDto(profileDto) });
    applyServerItems(list.items);
  } catch (e) {
    console.warn('[account] 서버 동기화 실패', e);
  }
}

/**
 * 옷장만 서버 목록으로 다시 맞춘다 (AI 코디 추천 후보에 기기에 없는 옷이 섞였을 때 등).
 * @returns 맞췄으면 true. 게스트 모드이거나 실패하면 false
 */
export async function syncWardrobe(): Promise<boolean> {
  if (!isAccountMode()) return false;
  try {
    const list = await api.wardrobe.list(ALL_ITEMS);
    if (!isAccountMode()) return false;
    applyServerItems(list.items);
    return true;
  } catch (e) {
    console.warn('[account] 옷장 동기화 실패', e);
    return false;
  }
}

/**
 * 앱 시작 시 세션 복구. _layout이 화면을 그리기 전에 기다린다.
 * - 계정 모드: 토큰이 없으면 로그인 화면으로, 있으면 백그라운드로 /users/me와 옷장을 동기화
 * - 새 설치인데 Keychain에 토큰이 남아 있으면(iOS 재설치) 그 계정으로 복구
 */
export async function bootstrapSession(): Promise<void> {
  const s = useStore.getState();
  const tokens = await tokenStore.get();

  if (s.authMode === 'account') {
    if (!tokens) {
      if (!s.authLost) useStore.setState({ authLost: true });
      return;
    }
    if (!s.authLost) void refreshAccount();
    return;
  }

  if (!tokens) return;
  if (s.onboarded) {
    // 게스트로 쓰고 있는 기기: 게스트 데이터를 우선하고 남은 토큰은 지운다
    await tokenStore.clear();
    return;
  }
  try {
    await enterAccount(await api.users.me());
  } catch (e) {
    console.warn('[account] 세션 복구 실패', e);
  }
}

/** 로그인이 풀린 뒤 게스트로 계속 쓰기: 기기 데이터는 그대로 둔다 */
export function continueAsGuest() {
  useStore.setState({ authMode: 'guest', user: null, authLost: false, authLostReason: null });
}

export async function logout(): Promise<void> {
  await unregisterPushToken(); // 이 기기로 D-1 푸시가 더 오지 않게 (실패해도 계속)
  await api.auth.logout(); // 서버 호출이 실패해도 토큰은 지운다
  useStore.getState().resetAll();
}

export async function deleteAccount(): Promise<void> {
  await api.users.deleteMe(); // 서버의 푸시 토큰도 함께 지워진다
  await unregisterPushToken(false);
  useStore.getState().resetAll();
}

// ── 온보딩 ──

/** 계정 모드 온보딩 마무리: 샘플 옷장 업로드 → 프로필 저장 → 기기 상태 구성 */
export async function finishAccountOnboarding(profile: Profile, withSample: boolean): Promise<void> {
  // 이전 시도가 중간에 실패했을 수 있으니 서버에 이미 옷이 있으면 샘플을 다시 올리지 않는다
  const existing = (await api.wardrobe.list(ALL_ITEMS)).items;
  let items: WardrobeItem[];
  let seeded = false;
  if (existing.length) {
    items = existing.map((d) => itemFromDto(d));
  } else if (withSample) {
    const seed = seedItems();
    const created = (await api.wardrobe.bulkCreate(seed.map(inputFromItem))).items;
    // 서버는 사진을 받지 않는다. 응답은 요청 순서대로라(doc/05) 같은 자리의 샘플 사진을 서버 id에 붙인다
    const s = useStore.getState();
    items = created.map((d, i) => {
      const photo = seed[i]?.imageUri;
      if (photo) s.setPhoto(d.id, photo);
      return itemFromDto(d, photo);
    });
    seeded = true;
  } else {
    items = [];
  }
  const saved = await api.profile.put(profileToDto(profile, true));
  const s = useStore.getState();
  // 샘플 기록(착용·예정일)은 서버가 준 id로 만든다
  s.completeOnboarding(profileFromDto(saved), seeded, items);
  if (s.user) useStore.setState({ user: { ...s.user, name: saved.name } });
  // 샘플 예정(서버 아이템 id로 만든 것)을 올리고, 다른 기기에서 만든 예정이 있으면 받는다
  void syncPlans({ force: true });
}

// ── 프로필 ──

/** 프로필 저장. 계정 모드는 서버에 먼저 저장하고 응답으로 기기 상태를 맞춘다 */
export async function saveProfile(next: Profile): Promise<void> {
  const s = useStore.getState();
  if (s.authMode !== 'account') {
    s.updateProfile(next);
    return;
  }
  const saved = await api.profile.put(profileToDto(next, true));
  s.updateProfile(profileFromDto(saved));
  if (s.user) useStore.setState({ user: { ...s.user, name: saved.name } });
}

// ── 옷장 ──

/** 올려 둔 사진을 붙이지 못한 오류 (정리 작업으로 지워졌거나 다른 아이템에 붙음 등) */
const isImageAttachError = (e: unknown): boolean =>
  isApiError(e) && (e.code.startsWith('IMAGE_') || e.code === 'NOT_FOUND');

/**
 * 옷 등록. 계정 모드에서 draft.image(올려 둔 서버 사진)가 있으면 imageId를 함께 보낸다.
 * 서버 사진이 붙지 않으면 고른 사진을 기기에 두고(앱 문서 폴더), 나중에 사진 옮기기가 다시 올린다.
 */
export async function createItem(draft: ItemDraft): Promise<WardrobeItem> {
  const s = useStore.getState();
  const { image, ...form } = draft;
  if (s.authMode !== 'account') {
    const id = newId('item');
    const imageUri = await keepPhotoLocally(form.imageUri, id);
    const createdAt = new Date().toISOString();
    // 구입일을 모르면 등록일(오늘, 기기 날짜)을 넣고 estimated (서버와 같은 규칙)
    const item: WardrobeItem = { ...form, ...purchaseFields(form.purchasedAt, createdAt), imageUri, id, wornCount: 0, createdAt };
    s.addItem(item);
    return item;
  }
  let dto: ItemDto;
  if (image) {
    try {
      dto = await api.wardrobe.create({ ...inputFromDraft(form), imageId: image.imageId });
    } catch (e) {
      if (!isImageAttachError(e)) throw e;
      console.warn('[account] 올린 사진을 붙이지 못해 사진 없이 등록하고 기기에 둬요', e);
      dto = await api.wardrobe.create(inputFromDraft(form));
    }
  } else {
    dto = await api.wardrobe.create(inputFromDraft(form));
  }
  // 서버 사진이 붙었으면 기기에는 두지 않는다 (화면 캐시는 올릴 때 채웠다)
  const local = imageFromDto(dto.image) ? undefined : await keepPhotoLocally(form.imageUri, dto.id);
  if (local) useStore.getState().setPhoto(dto.id, local);
  const item = itemFromDto(dto, local);
  useStore.getState().addItem(item);
  return item;
}

/** @returns false면 서버에 이미 없는 아이템이라 기기에서도 지웠다 */
export async function saveItem(id: string, draft: ItemDraft): Promise<boolean> {
  const s = useStore.getState();
  const current = s.items.find((i) => i.id === id);
  const { image, ...form } = draft;
  const photoChanged = form.imageUri !== current?.imageUri;
  if (s.authMode !== 'account') {
    const imageUri = photoChanged ? await keepPhotoLocally(form.imageUri, id) : form.imageUri;
    // 모름이면 등록일로 되돌린다 (서버 PATCH purchasedAt: null과 같다)
    const purchase = purchaseFields(form.purchasedAt, current?.createdAt ?? new Date().toISOString());
    useStore.getState().updateItem(id, { ...form, ...purchase, imageUri });
    return true;
  }
  try {
    const input = inputFromDraft(form);
    // 새로 올린 사진일 때만 imageId를 보낸다. 보내지 않으면 서버 사진은 그대로다
    // (폼을 연 뒤 사진 옮기기로 서버 사진이 생겼어도 지우지 않도록 null은 보내지 않는다)
    if (image && image.imageId !== current?.image?.imageId) input.imageId = image.imageId;
    const dto = await api.wardrobe.update(id, input);
    let local: string | undefined = useStore.getState().photos[id];
    if (input.imageId && imageFromDto(dto.image)) {
      local = undefined;
      useStore.getState().setPhoto(id, undefined);
    } else if (photoChanged) {
      local = await keepPhotoLocally(form.imageUri, id);
      useStore.getState().setPhoto(id, local);
    }
    useStore.getState().updateItem(id, itemFromDto(dto, local));
    return true;
  } catch (e) {
    if (isApiError(e) && e.code === 'NOT_FOUND') {
      s.deleteItem(id);
      return false;
    }
    throw e;
  }
}

/** 완전 삭제 (doc/13): 서버에서 지우고 기기의 코디·예정일에서도 뺀다 */
export async function removeItem(id: string): Promise<void> {
  const s = useStore.getState();
  if (s.authMode === 'account') {
    try {
      await api.wardrobe.remove(id);
    } catch (e) {
      if (!(isApiError(e) && e.code === 'NOT_FOUND')) throw e; // 이미 없으면 지운 것으로 본다
    }
  }
  s.deleteItem(id);
}

/** 착용 기록 후 바뀐 착용 횟수·마지막 착용일을 서버에 올린다. 실패하면 다음 동기화 때 다시 올린다 */
export async function pushWorn(ids: string[]): Promise<void> {
  const s = useStore.getState();
  if (s.authMode !== 'account') return;
  await Promise.all(
    ids.map(async (id) => {
      const item = useStore.getState().items.find((i) => i.id === id);
      if (!item) return;
      try {
        await api.wardrobe.update(id, { wornCount: item.wornCount, lastWornAt: item.lastWornAt ?? null });
      } catch (e) {
        console.warn('[account] 착용 기록 동기화 실패', id, e);
      }
    }),
  );
}

// ── 사용하지 않는 옷 (doc/13) ──

/** 서버에 사용 중지 API가 아직 없을 때 (예전 서버). 화면 문구는 src/lib/errorText.ts의 APP_TEXT */
export const RETIRE_UNSUPPORTED = 'RETIRE_UNSUPPORTED';

const unsupported = (status: number) =>
  new ApiError(status, RETIRE_UNSUPPORTED, "서버에 아직 '사용하지 않는 옷' 기능이 없어요. 서버를 최신으로 올린 뒤 다시 시도해 주세요.");

/**
 * 사용 중지·다시 사용 요청이 404·405·501로 실패했을 때 까닭을 가린다.
 * 아이템 조회가 되면 서버에 API가 없는 것(unsupported), 조회도 404면 다른 기기에서 지운 옷(gone)이다.
 */
const classifyMissing = async (id: string, e: unknown): Promise<'unsupported' | 'gone' | null> => {
  if (!isApiError(e) || ![404, 405, 501].includes(e.status)) return null;
  if (e.status !== 404) return 'unsupported';
  try {
    await api.wardrobe.get(id);
    return 'unsupported';
  } catch (inner) {
    return isApiError(inner) && inner.status === 404 ? 'gone' : null;
  }
};

export interface IRetireInput {
  /** RETIRE_REASON 코드 */
  reason: string;
  memo?: string;
}

/**
 * 사용하지 않는 옷으로 옮긴다. 이미 사용 중지면 이유·메모만 바꾼다.
 * 계정 모드는 서버에 먼저 반영하고, 게스트는 기기에서만 바꾼다.
 * @returns false면 서버에 이미 없는 아이템이라 기기에서도 지웠다
 * @throws ApiError. 서버에 기능이 없으면 code가 RETIRE_UNSUPPORTED
 */
export const retireItem = async (id: string, input: IRetireInput): Promise<boolean> => {
  const s = useStore.getState();
  const memo = input.memo?.trim().slice(0, RETIRE_MEMO_MAX) || undefined;
  if (s.authMode !== 'account') {
    const prev = s.items.find((i) => i.id === id);
    s.updateItem(id, {
      retiredAt: prev?.retiredAt ?? new Date().toISOString(),
      retireReason: input.reason,
      retireMemo: memo,
    });
    return true;
  }
  let dto: ItemDto;
  try {
    dto = await api.wardrobe.retire(id, { reason: input.reason, memo });
  } catch (e) {
    const why = await classifyMissing(id, e);
    if (why === 'unsupported') throw unsupported(isApiError(e) ? e.status : 404);
    if (why === 'gone') {
      useStore.getState().deleteItem(id);
      return false;
    }
    throw e;
  }
  // 예전 서버가 다른 경로로 200을 줬다면 사용 중지 필드가 없다
  if (!dto?.retiredAt) throw unsupported(200);
  useStore.getState().updateItem(id, retireFromDto(dto));
  return true;
};

/**
 * 다시 사용한다 (사용 중지 해제). 원래 보유 상태(status)로 돌아온다.
 * @returns false면 서버에 이미 없는 아이템이라 기기에서도 지웠다
 * @throws ApiError. 서버에 기능이 없으면 code가 RETIRE_UNSUPPORTED
 */
export const restoreItem = async (id: string): Promise<boolean> => {
  const s = useStore.getState();
  const cleared = { retiredAt: undefined, retireReason: undefined, retireMemo: undefined };
  if (s.authMode !== 'account') {
    s.updateItem(id, cleared);
    return true;
  }
  let dto: ItemDto;
  try {
    dto = await api.wardrobe.restore(id);
  } catch (e) {
    const why = await classifyMissing(id, e);
    if (why === 'unsupported') throw unsupported(isApiError(e) ? e.status : 404);
    if (why === 'gone') {
      useStore.getState().deleteItem(id);
      return false;
    }
    throw e;
  }
  if (dto?.retiredAt) throw unsupported(200);
  useStore.getState().updateItem(id, dto ? retireFromDto(dto) : cleared);
  return true;
};
