// 시험 분석: 사진 한 장으로 고른 공급자를 동시에 호출하고 결과·지연·토큰을 나란히 비교한다.
import { useEffect, useMemo, useState } from 'react';

import { api, errorMessage, isApiError } from '@/api';
import type { IAiProvider, IAiSuggestion, IProviderTestResult } from '@/api';

import { ColorChip } from '@/components/Garment';
import { Badge, Button, Card, Notice } from '@/components/ui';

import { useQueryClient } from '@tanstack/react-query';

import { errorTextWithTrace } from '@/lib/errorText';
import { fmtBytes, fmtMs, fmtNum } from '@/lib/format';
import { useCodeBook } from '@/lib/queries';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPT = 'image/jpeg,image/png,image/webp,image/heic,image/heif';

type TRunState =
  | { state: 'running'; startedAt: number }
  | { state: 'done'; result: IProviderTestResult; elapsedMs: number }
  | { state: 'error'; code: string; message: string; elapsedMs: number };

/** 비교할 필드와 표시 방법 */
const ROWS: { key: keyof IAiSuggestion; label: string; group?: string; multi?: boolean }[] = [
  { key: 'name', label: '이름' },
  { key: 'category', label: '종류', group: 'CATEGORY' },
  { key: 'subType', label: '세부 종류', group: 'SUB_TYPE' },
  { key: 'color', label: '색상', group: 'COLOR' },
  { key: 'pattern', label: '패턴', group: 'PATTERN' },
  { key: 'seasons', label: '계절', group: 'SEASON', multi: true },
  { key: 'styles', label: '스타일', group: 'STYLE', multi: true },
  { key: 'formality', label: '격식' },
  { key: 'confidence', label: '확신도' },
];

const norm = (v: unknown): string => {
  if (Array.isArray(v)) return [...v].map(String).sort().join(',');
  return v === undefined || v === null ? '' : String(v);
};

export const TestPanel = ({ providers }: { providers: IAiProvider[] }) => {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(providers.filter((p) => p.usable || (p.model && p.keyConfigured)).map((p) => p.providerId)),
  );
  const [runs, setRuns] = useState<Record<string, TRunState>>({});
  const [, setTick] = useState(0);

  const book = useCodeBook();
  const queryClient = useQueryClient();
  // 순서 예외: 아래 effect의 의존성 배열이 running을 읽는다
  const running = Object.values(runs).some((r) => r.state === 'running');

  // 미리보기 URL 정리
  useEffect(() => {
    if (!file) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // 진행 중인 동안 경과 시간을 다시 그린다
  useEffect(() => {
    if (!running) return;
    const t = setInterval(() => setTick((n) => n + 1), 250);
    return () => clearInterval(t);
  }, [running]);

  const columns = providers.filter((p) => runs[p.providerId]);
  const okSuggestions = columns
    .map((p) => runs[p.providerId])
    .filter(
      (r): r is Extract<TRunState, { state: 'done' }> => r.state === 'done' && r.result.ok && !!r.result.suggestion,
    )
    .map((r) => r.result.suggestion!);
  const differs = useMemo(() => {
    const out = new Set<string>();
    for (const row of ROWS) {
      if (row.key === 'name' || row.key === 'confidence') continue;
      if (new Set(okSuggestions.map((s) => norm(s[row.key]))).size > 1) out.add(row.key as string);
    }
    return out;
  }, [okSuggestions]);

  const pickFile = (f: File | undefined) => {
    setFileError(null);
    if (!f) return;
    if (f.size > MAX_BYTES) {
      setFileError(`사진이 너무 큽니다 (${fmtBytes(f.size)}). 10MB 이하만 올릴 수 있습니다.`);
      return;
    }
    setFile(f);
    setRuns({});
  };

  const toggle = (id: string) =>
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });

  const runAll = async () => {
    if (!file) return;
    const ids = providers.filter((p) => selected.has(p.providerId)).map((p) => p.providerId);
    const startedAt = performance.now();
    setRuns(Object.fromEntries(ids.map((id) => [id, { state: 'running', startedAt } as TRunState])));
    await Promise.all(
      ids.map(async (id) => {
        try {
          const result = await api.providers.test(id, file);
          setRuns((r) => ({ ...r, [id]: { state: 'done', result, elapsedMs: performance.now() - startedAt } }));
        } catch (e) {
          setRuns((r) => ({
            ...r,
            [id]: {
              state: 'error',
              code: isApiError(e) ? e.code : 'ERROR',
              message: isApiError(e) ? errorTextWithTrace(e) : errorMessage(e),
              elapsedMs: performance.now() - startedAt,
            },
          }));
        }
      }),
    );
    // 오늘 호출 수·기록이 바뀐다
    void queryClient.invalidateQueries({ queryKey: ['ai'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const show = (row: (typeof ROWS)[number], s: IAiSuggestion) => {
    const v = s[row.key];
    if (v === undefined || v === null) return <span className="muted">—</span>;
    if (row.key === 'confidence' && typeof v === 'number') return `${Math.round(v * 100)}%`;
    if (row.multi && Array.isArray(v)) return v.map((x) => book.label(row.group!, String(x))).join(', ') || '—';
    if (row.group === 'COLOR') {
      const c = book.find('COLOR', String(v));
      return (
        <span className="row gap-xs">
          <ColorChip hex={c?.attrs.hex} size={12} /> {book.label('COLOR', String(v))}
        </span>
      );
    }
    if (row.group) {
      const known = book.find(row.group, String(v));
      return (
        <>
          {book.label(row.group, String(v))} <span className="mono muted small">{String(v)}</span>
          {!known ? <Badge tone="red">모르는 코드</Badge> : null}
        </>
      );
    }
    return String(v);
  };

  return (
    <Card
      title="시험 분석"
      actions={<span className="muted small">일일 한도에 세지 않고, 기록은 ADMIN_TEST로 남습니다.</span>}
    >
      <div className="test-setup">
        <label className={`dropzone ${preview ? 'has-image' : ''}`}>
          <input type="file" accept={ACCEPT} onChange={(e) => pickFile(e.target.files?.[0])} />
          {preview ? (
            <img src={preview} alt="시험 사진" />
          ) : (
            <span>
              사진 한 장 고르기
              <br />
              <span className="muted small">JPEG·PNG·WebP·HEIC, 10MB 이하</span>
            </span>
          )}
        </label>
        <div className="test-options">
          {file ? (
            <div className="small">
              <strong>{file.name}</strong> <span className="muted">· {fmtBytes(file.size)}</span>
            </div>
          ) : null}
          {fileError ? <Notice tone="error">{fileError}</Notice> : null}
          <div className="field-label mt-sm">호출할 공급자</div>
          <div className="check-list">
            {providers.map((p) => (
              <label key={p.providerId} className="check">
                <input type="checkbox" checked={selected.has(p.providerId)} onChange={() => toggle(p.providerId)} />
                <span>
                  {p.displayName} <span className="mono muted small">{p.model || '모델 없음'}</span>
                </span>
                {!p.model ? (
                  <Badge tone="red">모델 없음</Badge>
                ) : !p.keyConfigured ? (
                  <Badge tone="red">키 없음</Badge>
                ) : !p.enabled ? (
                  <Badge tone="gray">사용 안 함</Badge>
                ) : null}
              </label>
            ))}
          </div>
          <Button
            variant="primary"
            onClick={() => void runAll()}
            disabled={!file || selected.size === 0}
            busy={running}
            className="mt-sm"
          >
            {running ? '분석 중…' : `${selected.size}개 공급자로 동시에 분석`}
          </Button>
        </div>
      </div>

      {columns.length ? (
        <div className="table-wrap mt-md">
          <table className="table compare">
            <thead>
              <tr>
                <th />
                {columns.map((p) => (
                  <th key={p.providerId}>
                    {p.displayName}
                    <div className="mono muted small">{p.providerId}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <th>결과</th>
                {columns.map((p) => {
                  const r = runs[p.providerId];
                  if (r.state === 'running')
                    return (
                      <td key={p.providerId}>
                        <span className="spinner" /> {fmtMs(performance.now() - r.startedAt)}
                      </td>
                    );
                  if (r.state === 'error')
                    return (
                      <td key={p.providerId}>
                        <Badge tone="red">요청 실패</Badge> <span className="mono small">{r.code}</span>
                        <div className="small text-danger">{r.message}</div>
                      </td>
                    );
                  return (
                    <td key={p.providerId}>
                      {r.result.ok ? <Badge tone="green">성공</Badge> : <Badge tone="red">실패</Badge>}
                      {!r.result.ok ? (
                        <div className="small">
                          <span className="mono">{r.result.errorCode ?? ''}</span>{' '}
                          <span className="text-danger">{r.result.errorMessage ?? ''}</span>
                        </div>
                      ) : null}
                    </td>
                  );
                })}
              </tr>
              <tr>
                <th>모델</th>
                {columns.map((p) => {
                  const r = runs[p.providerId];
                  return (
                    <td key={p.providerId} className="mono small">
                      {r.state === 'done' ? (r.result.model ?? '—') : (p.model ?? '—')}
                    </td>
                  );
                })}
              </tr>
              <tr>
                <th>지연</th>
                {columns.map((p) => {
                  const r = runs[p.providerId];
                  return (
                    <td key={p.providerId}>
                      {r.state === 'done' ? (
                        <>
                          <strong>{fmtMs(r.result.latencyMs)}</strong>{' '}
                          <span className="muted small">(왕복 {fmtMs(r.elapsedMs)})</span>
                        </>
                      ) : r.state === 'error' ? (
                        <span className="muted small">왕복 {fmtMs(r.elapsedMs)}</span>
                      ) : (
                        '…'
                      )}
                    </td>
                  );
                })}
              </tr>
              <tr>
                <th>토큰 (입력 / 출력)</th>
                {columns.map((p) => {
                  const r = runs[p.providerId];
                  return (
                    <td key={p.providerId}>
                      {r.state === 'done' ? `${fmtNum(r.result.inputTokens)} / ${fmtNum(r.result.outputTokens)}` : '—'}
                    </td>
                  );
                })}
              </tr>
              {ROWS.map((row) => (
                <tr key={row.key as string} className={differs.has(row.key as string) ? 'row-diff' : ''}>
                  <th>
                    {row.label}
                    {differs.has(row.key as string) ? (
                      <span className="diff-mark" title="공급자마다 결과가 다릅니다">
                        다름
                      </span>
                    ) : null}
                  </th>
                  {columns.map((p) => {
                    const r = runs[p.providerId];
                    return (
                      <td key={p.providerId}>
                        {r.state === 'done' && r.result.suggestion ? (
                          show(row, r.result.suggestion)
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </Card>
  );
};
