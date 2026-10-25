// 앱 대화상자 (components/ui.tsx의 notify·confirm, DialogHost가 그린다). 저장하지 않는다.
import { create } from 'zustand';

import { dismiss, enqueue, type IDialog } from '@/lib/dialogQueue';

interface IDialogState {
  /** 맨 앞이 지금 보이는 대화상자 */
  queue: IDialog[];
  show: (dialog: Omit<IDialog, 'id'>) => void;
  /** 닫는다. ok면 창을 닫은 뒤 onOk를 부른다 (onOk에서 화면을 옮기거나 다른 알림을 띄워도 되게) */
  close: (id: number, ok: boolean) => void;
}

let nextId = 1;

export const useDialogStore = create<IDialogState>((set, get) => ({
  queue: [],
  show: (dialog) => set((s) => ({ queue: enqueue(s.queue, { ...dialog, id: nextId++ }) })),
  close: (id, ok) => {
    const target = get().queue.find((d) => d.id === id);
    set((s) => ({ queue: dismiss(s.queue, id) }));
    if (ok) target?.onOk?.();
  },
}));
