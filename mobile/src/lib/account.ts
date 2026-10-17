// 계정 모드 흐름: 로그인 후 상태 구성, 세션 복구, 서버 동기화, 옷장·프로필 저장.
// 게스트 모드에서는 모든 함수가 기존처럼 기기 안 저장소만 바꾼다.
import type { ItemDto, UserDto } from '@/api/types';
import type { ItemDraft } from '@/components/ItemForm';
import { seedItems } from '@/data/seed';
import type { Profile, WardrobeItem } from '@/data/types';
import { isApiError } from '@/api/errors';
import { useStore, type AccountUser } from '@/store/useStore';
import { api, setAuthLostHandler, tokenStore } from './api';
import { newId } from './context';
import { inputFromDraft, inputFromItem, itemFromDto, profileFromDto, profileToDto } from './serverItems';

export function isAccountMode(): boolean {
  return useStore.getState().authMode === 'account';
}

function toAccountUser(u: UserDto): AccountUser {
  return { id: u.id, email: u.email, name: u.name };
}

// ── 로그인 상태 잃음 ──

/** Refresh 실패: 기기 데이터는 두고 로그인 화면으로 보낸다 (이동은 _layout의 AuthWatcher가 한다) */
setAuthLostHandler(() => {
  const s = useStore.getState();
  if (s.authMode === 'account' && !s.authLost) useStore.setState({ authLost: true });
});

// ── 서버 아이템 합치기 ──

/**
 * 서버 목록을 기기 상태와 합친다.
 * - 사진은 기기에만 있으므로 id별 사진을 붙인다.
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
  const list = profileDto.onboarded ? await api.wardrobe.list() : null;

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
    profile,
    onboarded: profileDto.onboarded,
  });
  if (list) applyServerItems(list.items);
  return profileDto.onboarded ? 'home' : 'onboarding';
}

/** 앱 시작·복귀 때 서버 기준으로 프로필·옷장을 다시 맞춘다. 오프라인이면 기기 데이터로 계속한다 */
export async function refreshAccount(): Promise<void> {
  try {
    const [user, profileDto, list] = await Promise.all([api.users.me(), api.profile.get(), api.wardrobe.list()]);
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
    const list = await api.wardrobe.list();
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
  useStore.setState({ authMode: 'guest', user: null, authLost: false });
}

export async function logout(): Promise<void> {
  await api.auth.logout(); // 서버 호출이 실패해도 토큰은 지운다
  useStore.getState().resetAll();
}

export async function deleteAccount(): Promise<void> {
  await api.users.deleteMe();
  useStore.getState().resetAll();
}

// ── 온보딩 ──

/** 계정 모드 온보딩 마무리: 샘플 옷장 업로드 → 프로필 저장 → 기기 상태 구성 */
export async function finishAccountOnboarding(profile: Profile, withSample: boolean): Promise<void> {
  // 이전 시도가 중간에 실패했을 수 있으니 서버에 이미 옷이 있으면 샘플을 다시 올리지 않는다
  const existing = (await api.wardrobe.list()).items;
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

export async function createItem(draft: ItemDraft): Promise<WardrobeItem> {
  const s = useStore.getState();
  if (s.authMode !== 'account') {
    const item: WardrobeItem = { ...draft, id: newId('item'), wornCount: 0, createdAt: new Date().toISOString() };
    s.addItem(item);
    return item;
  }
  const dto = await api.wardrobe.create(inputFromDraft(draft));
  if (draft.imageUri) s.setPhoto(dto.id, draft.imageUri);
  const item = itemFromDto(dto, draft.imageUri);
  s.addItem(item);
  return item;
}

/** @returns false면 서버에 이미 없는 아이템이라 기기에서도 지웠다 */
export async function saveItem(id: string, draft: ItemDraft): Promise<boolean> {
  const s = useStore.getState();
  if (s.authMode !== 'account') {
    s.updateItem(id, draft);
    return true;
  }
  try {
    const dto = await api.wardrobe.update(id, inputFromDraft(draft));
    s.setPhoto(id, draft.imageUri);
    s.updateItem(id, itemFromDto(dto, draft.imageUri));
    return true;
  } catch (e) {
    if (isApiError(e) && e.code === 'NOT_FOUND') {
      s.deleteItem(id);
      return false;
    }
    throw e;
  }
}

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
