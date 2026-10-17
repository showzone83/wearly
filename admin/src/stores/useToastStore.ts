// 저장·삭제 결과를 잠깐 보여 주는 알림 (zustand). 화면에서는 const toast = useToastStore((s) => s.show) 로 쓴다.
// 그리는 쪽은 components/Toaster.tsx.
import { create } from 'zustand';

export type TToastTone = 'success' | 'error';

export interface IToast {
  id: number;
  tone: TToastTone;
  text: string;
}

interface IToastStore {
  toasts: IToast[];
  show: (text: string, tone?: TToastTone) => void;
  dismiss: (id: number) => void;
}

let seq = 0;

export const useToastStore = create<IToastStore>()((set, get) => ({
  toasts: [],
  show: (text, tone = 'success') => {
    const id = ++seq;
    set((s) => ({ toasts: [...s.toasts, { id, tone, text }] }));
    setTimeout(() => get().dismiss(id), tone === 'error' ? 5000 : 2600);
  },
  dismiss: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },
}));
