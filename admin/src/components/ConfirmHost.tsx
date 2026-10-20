// 확인 대화상자를 그린다 (브라우저 window.confirm 대신, 이슈 #10). 상태는 stores/useConfirmStore.ts.
// 닫기(✕)·Esc·바깥 클릭은 취소다. danger면 처음 포커스를 취소 버튼에 둬 Enter로 실수로 실행하지 않게 한다.
import { Button, Modal } from '@/components/ui';

import { useConfirmStore } from '@/stores/useConfirmStore';

export const ConfirmHost = () => {
  const current = useConfirmStore((s) => s.queue[0]);
  const settle = useConfirmStore((s) => s.settle);

  if (!current) return null;
  const { id, options } = current;
  const danger = options.tone === 'danger';

  const handleCancel = () => settle(id, false);
  const handleConfirm = () => settle(id, true);

  return (
    <Modal
      key={id}
      title={options.title ?? '확인'}
      onClose={handleCancel}
      footer={
        <>
          <Button autoFocus={danger} onClick={handleCancel}>
            {options.cancelLabel ?? '취소'}
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} autoFocus={!danger} onClick={handleConfirm}>
            {options.confirmLabel ?? '확인'}
          </Button>
        </>
      }
    >
      <div className="confirm-message">{options.message}</div>
    </Modal>
  );
};
