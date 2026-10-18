// 토스트 목록을 화면 구석에 그린다. 상태는 stores/useToastStore.ts.
import { TraceId } from '@/components/ui';

import { useToastStore } from '@/stores/useToastStore';

export const Toaster = () => {
  const toasts = useToastStore((s) => s.toasts);
  return (
    <div className="toasts" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.tone}`}>
          {t.text}
          {t.traceId ? <TraceId traceId={t.traceId} className="toast-trace" /> : null}
        </div>
      ))}
    </div>
  );
};
