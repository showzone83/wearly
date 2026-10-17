// 옷 속성 코드 캐시 (doc/06_코드_관리.md 5장).
// - 앱 시작: 기기에 저장한 코드를 먼저 쓰고, 뒤에서 GET /codes로 갱신한다(ETag가 같으면 304로 그대로).
// - 기본 실행에서 한 번도 받지 못했으면(첫 실행 + 서버 불가) _layout이 재시도 화면을 보여 준다.
// - 목업 실행(npm run start:mock)에서만 앱에 넣어 둔 초기값(src/data/codes.seed.json)을 쓴다. 초기값은 저장하지 않는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { CodesResponse } from '@/api/types';
import seedJson from '@/data/codes.seed.json';
import { api } from '@/lib/api';
import { hasCodes, normalizeCodes, setCodes } from '@/lib/codes';
import { MOCK_MODE } from '@/lib/mode';

interface CodesState {
  /** 서버에서 마지막으로 받은 코드 */
  codes: CodesResponse | null;
  /** 다음 요청의 If-None-Match */
  etag: string | null;
  /** 마지막으로 서버와 맞춘 시각 (ISO) */
  syncedAt: string | null;
}

export const useCodes = create<CodesState>()(
  persist(
    (): CodesState => ({ codes: null, etag: null, syncedAt: null }),
    {
      name: 'wearly-codes-v1',
      storage: createJSONStorage(() => AsyncStorage),
    },
  ),
);

const SEED: CodesResponse | null = MOCK_MODE ? normalizeCodes(seedJson) : null;

/** 저장소의 코드(없으면 목업 실행의 초기값)를 조회 색인에 반영한다 */
function apply(codes: CodesResponse | null) {
  setCodes(codes ?? SEED);
}
apply(null);
useCodes.subscribe((s, prev) => {
  if (s.codes !== prev.codes) apply(s.codes);
});

/** 화면이 코드 갱신에 맞춰 다시 그려지도록 구독한다. 반환값은 지금 쓰는 코드의 version */
export function useCodesVersion(): string | null {
  return useCodes((s) => s.codes?.version ?? SEED?.version ?? null);
}

let inflight: Promise<void> | null = null;

/** 서버에서 코드를 받아 저장한다. 실패하면 throw (저장된 코드는 그대로) */
export function refreshCodes(): Promise<void> {
  if (!inflight) {
    inflight = (async () => {
      const s = useCodes.getState();
      // 저장된 코드가 없으면 ETag를 보내지 않는다 (304가 와도 쓸 게 없다)
      const res = await api.codes.list(s.codes ? s.etag : null);
      const syncedAt = new Date().toISOString();
      if (!res.changed) {
        useCodes.setState({ syncedAt });
        return;
      }
      const codes = normalizeCodes(res.codes);
      // version이 같으면 다시 색인하지 않는다 (화면을 괜히 다시 그리지 않게)
      if (s.codes && s.codes.version === codes.version) useCodes.setState({ etag: res.etag, syncedAt });
      else useCodes.setState({ codes, etag: res.etag, syncedAt });
    })().finally(() => {
      inflight = null;
    });
  }
  return inflight;
}

/**
 * 앱 시작 시 코드 준비. 저장소 복원(hydration)이 끝난 뒤 부른다.
 * 쓸 코드가 이미 있으면 바로 true를 돌려주고 뒤에서 갱신한다. 없으면 서버 응답을 기다린다.
 * @returns 화면을 그릴 수 있으면 true, 코드가 하나도 없으면 false (재시도 화면)
 */
export async function bootstrapCodes(): Promise<boolean> {
  apply(useCodes.getState().codes);
  if (hasCodes()) {
    refreshCodes().catch((e) => console.warn('[codes] 코드 갱신 실패 (저장된 코드로 계속)', e));
    return true;
  }
  try {
    await refreshCodes();
  } catch (e) {
    console.warn('[codes] 코드를 받지 못했어요', e);
  }
  return hasCodes();
}
