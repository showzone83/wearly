// 저장·삭제 결과를 잠깐 보여 주는 알림 (zustand). 화면에서는 const toast = useToastStore((s) => s.show) 로 쓰고,
// API 오류는 const toastError = useToastStore((s) => s.showError) 로 넘기면 문구(lib/errorText.ts)와 오류 번호를 붙인다.
// 그리는 쪽은 components/Toaster.tsx.
import { errorMessage, isApiError } from '@/api';

import { create } from 'zustand';

import { systemTraceId } from '@/lib/errorText';

export type TToastTone = 'success' | 'error';

export interface IToast {
  id: number;
  tone: TToastTone;
  text: string;
  /** 시스템 오류(5xx)의 전체 traceId. 토스트에 "오류 번호 ab12cd34"로 보이고 누르면 복사한다 */
  traceId?: string | null;
}

interface IToastStore {
  toasts: IToast[];
  show: (text: string, tone?: TToastTone, traceId?: string | null) => void;
  /** 오류를 문구로 바꿔 보여 준다. prefix가 있으면 "prefix: 문구" */
  showError: (e: unknown, prefix?: string) => void;
  dismiss: (id: number) => void;
}

let seq = 0;

const durationOf = (tone: TToastTone, traceId?: string | null): number => {
  if (traceId) return 9000; // 오류 번호를 복사할 시간
  return tone === 'error' ? 5000 : 2600;
};

export const useToastStore = create<IToastStore>()((set, get) => ({
  toasts: [],
  show: (text, tone = 'success', traceId = null) => {
    const id = ++seq;
    set((s) => ({ toasts: [...s.toasts, { id, tone, text, traceId }] }));
    setTimeout(() => get().dismiss(id), durationOf(tone, traceId));
  },
  showError: (e, prefix) => {
    const text = errorMessage(e);
    get().show(prefix ? `${prefix}: ${text}` : text, 'error', isApiError(e) ? systemTraceId(e) : null);
  },
  dismiss: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },
}));
