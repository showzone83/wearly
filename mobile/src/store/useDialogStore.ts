// 앱 대화상자 (components/ui.tsx의 notify·confirm, DialogHost가 그린다). 저장하지 않는다.
import { create } from 'zustand';

import { dismiss, enqueue, type IDialog, type TDialogChoice } from '@/lib/dialogQueue';

interface IDialogState {
  /** 맨 앞이 지금 보이는 대화상자 */
  queue: IDialog[];
  show: (dialog: Omit<IDialog, 'id'>) => void;
  /**
   * 닫는다. 확인 버튼이면 onOk, 취소 버튼이면 onCancel을 창을 닫은 뒤 부른다 (거기서 화면을 옮기거나 다른 알림을 띄워도 되게).
   * 뒤로 가기·바깥 누르기(dismiss)는 아무것도 부르지 않는다
   */
  close: (id: number, choice: TDialogChoice) => void;
}

let nextId = 1;

export const useDialogStore = create<IDialogState>((set, get) => ({
  queue: [],
  show: (dialog) => set((s) => ({ queue: enqueue(s.queue, { ...dialog, id: nextId++ }) })),
  close: (id, choice) => {
    const target = get().queue.find((d) => d.id === id);
    set((s) => ({ queue: dismiss(s.queue, id) }));
    if (choice === 'ok') target?.onOk?.();
    else if (choice === 'cancel') target?.onCancel?.();
  },
}));
