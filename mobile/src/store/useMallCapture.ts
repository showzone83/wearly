// 쇼핑몰 캡처 결과를 부른 화면에 넘긴다 (doc/24). 쇼핑몰 화면(item/mall)이 넣고 router.back() 하면,
// 부른 화면(옷 등록·옷 상세)이 다시 보일 때 자기 몫(target)만 꺼낸다. 기기에 저장하지 않는다.
import { create } from 'zustand';

/** 캡처해 자른 사진 (캐시 폴더의 PNG) */
export interface IMallCapture {
  /** 부른 화면: 'new'(옷 등록) 또는 옷 ID(옷 상세 사진 바꾸기) */
  target: string;
  uri: string;
  width: number;
  height: number;
  /** 캡처한 쇼핑몰 (코드 그룹 MALL) */
  mallCode: string;
}

interface IMallCaptureState {
  result: IMallCapture | null;
  put: (result: IMallCapture) => void;
  /** target 몫이면 꺼내고 비운다. 다른 화면 몫이거나 없으면 null */
  take: (target: string) => IMallCapture | null;
}

export const useMallCapture = create<IMallCaptureState>()((set, get) => ({
  result: null,
  put: (result) => set({ result }),
  take: (target) => {
    const r = get().result;
    if (!r || r.target !== target) return null;
    set({ result: null });
    return r;
  },
}));
