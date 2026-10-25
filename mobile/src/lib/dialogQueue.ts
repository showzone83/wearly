// 앱 대화상자 대기열 (순수 함수, store/useDialogStore.ts). 맨 앞 하나만 보이고 닫으면 다음 것이 보인다.
// 알림(notify)·확인(confirm) 모두 앱이 직접 그린다 — 시스템 Alert·브라우저 alert/confirm은 쓰지 않는다.

export type TDialogKind = 'notify' | 'confirm';

export interface IDialog {
  id: number;
  kind: TDialogKind;
  title: string;
  message?: string;
  /** 확인 버튼 글자 */
  okLabel: string;
  /** confirm의 확인 버튼을 위험한 동작 색으로 그린다 (기본 true. 탈퇴 취소처럼 되돌리는 동작은 false) */
  danger?: boolean;
  /** confirm에서 확인을 눌렀을 때. 창을 닫은 뒤에 부른다 */
  onOk?: () => void;
}

/** 같은 내용이 이미 줄의 맨 끝에 있으면(연타) 그대로 둔다 */
export const enqueue = (queue: readonly IDialog[], dialog: IDialog): IDialog[] => {
  const last = queue[queue.length - 1];
  if (
    last &&
    last.kind === dialog.kind &&
    last.title === dialog.title &&
    (last.message ?? '') === (dialog.message ?? '') &&
    last.okLabel === dialog.okLabel
  ) {
    return [...queue];
  }
  return [...queue, dialog];
};

/** id인 대화상자를 뺀다. 없으면 그대로 */
export const dismiss = (queue: readonly IDialog[], id: number): IDialog[] => queue.filter((d) => d.id !== id);
