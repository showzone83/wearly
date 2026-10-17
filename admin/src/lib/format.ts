const dateTimeFmt = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

const dateFmt = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** ISO 시각 → 한국 시각 `2026. 10. 17. 14:03:22` */
export const fmtDateTime = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : dateTimeFmt.format(d);
};

export const fmtDate = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : dateFmt.format(d);
};

export const fmtNum = (n: number | null | undefined): string => {
  return n === null || n === undefined || Number.isNaN(n) ? '—' : n.toLocaleString('ko-KR');
};

export const fmtMs = (ms: number | null | undefined): string => {
  if (ms === null || ms === undefined) return '—';
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}초` : `${Math.round(ms)}ms`;
};

export const fmtBytes = (b: number | null | undefined): string => {
  if (b === null || b === undefined) return '—';
  if (b < 1024) return `${b}B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)}KB`;
  return `${(b / 1024 / 1024).toFixed(1)}MB`;
};

export const fmtPercent = (part: number, total: number): string => {
  return total > 0 ? `${Math.round((part / total) * 100)}%` : '—';
};

/** 오늘 날짜 (Asia/Seoul) YYYY-MM-DD */
export const todaySeoul = (offsetDays = 0): string => {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(d);
};

/**
 * ISO 시각 → "방금 전", "3분 전", "2시간 전", "5일 전". 30일이 넘으면 날짜.
 * now는 기준 시각(ms). 렌더 중에 Date.now()를 부르지 않도록 호출하는 쪽이 넘긴다(쿼리의 dataUpdatedAt 등).
 */
export const fmtRelative = (iso: string | null | undefined, now: number): string => {
  if (!iso) return '—';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const sec = Math.floor((now - t) / 1000);
  if (sec < 60) return '방금 전';
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day <= 30) return `${day}일 전`;
  return fmtDate(iso);
};
