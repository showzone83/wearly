// 브라우저 기본 확인창(window.confirm) 대신 쓰는 확인 대화상자 (zustand, 이슈 #10).
// 화면에서는 const confirm = useConfirmStore((s) => s.ask) 로 받아 if (!(await confirm({ message }))) return; 처럼 쓴다.
// 한 번에 하나만 보이고, 여러 번 부르면 순서대로 뜬다. 그리는 쪽은 components/ConfirmHost.tsx.
import type { ReactNode } from 'react';

import { create } from 'zustand';

export type TConfirmTone = 'primary' | 'danger';

export interface IConfirmOptions {
  /** 기본 "확인" */
  title?: string;
  /** 본문. 문자열의 줄바꿈(\n)은 그대로 보인다 */
  message: ReactNode;
  /** 기본 "확인" */
  confirmLabel?: string;
  /** 기본 "취소" */
  cancelLabel?: string;
  /** danger면 빨간 확인 버튼, 처음 포커스는 취소 버튼 (Enter로 실수로 지우지 않게) */
  tone?: TConfirmTone;
}

export interface IConfirmRequest {
  id: number;
  options: IConfirmOptions;
  resolve: (ok: boolean) => void;
}

interface IConfirmStore {
  queue: IConfirmRequest[];
  /** 확인하면 true, 취소·닫기·Esc·바깥 클릭이면 false */
  ask: (options: IConfirmOptions) => Promise<boolean>;
  settle: (id: number, ok: boolean) => void;
}

let seq = 0;

export const useConfirmStore = create<IConfirmStore>()((set, get) => ({
  queue: [],
  ask: (options) =>
    new Promise<boolean>((resolve) => {
      const id = ++seq;
      set((s) => ({ queue: [...s.queue, { id, options, resolve }] }));
    }),
  settle: (id, ok) => {
    const req = get().queue.find((r) => r.id === id);
    if (!req) return;
    set((s) => ({ queue: s.queue.filter((r) => r.id !== id) }));
    req.resolve(ok);
  },
}));
