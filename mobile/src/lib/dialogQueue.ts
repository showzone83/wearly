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
  /** confirm의 취소 버튼 글자 (기본 "취소"). 두 갈래 선택(바꾸기 / 90일 연장 등)에 쓴다 */
  cancelLabel?: string;
  /** confirm에서 취소 버튼을 눌렀을 때만 부른다 (뒤로 가기·바깥 누르기는 부르지 않는다). 창을 닫은 뒤에 부른다 */
  onCancel?: () => void;
}

/** 창을 닫은 방법: 확인 버튼 · 취소 버튼 · 뒤로 가기·바깥 누르기 */
export type TDialogChoice = 'ok' | 'cancel' | 'dismiss';

/** 같은 내용이 이미 줄의 맨 끝에 있으면(연타) 그대로 둔다 */
export const enqueue = (queue: readonly IDialog[], dialog: IDialog): IDialog[] => {
  const last = queue[queue.length - 1];
  if (
    last &&
    last.kind === dialog.kind &&
    last.title === dialog.title &&
    (last.message ?? '') === (dialog.message ?? '') &&
    last.okLabel === dialog.okLabel &&
    last.cancelLabel === dialog.cancelLabel
  ) {
    return [...queue];
  }
  return [...queue, dialog];
};

/** id인 대화상자를 뺀다. 없으면 그대로 */
export const dismiss = (queue: readonly IDialog[], id: number): IDialog[] => queue.filter((d) => d.id !== id);
