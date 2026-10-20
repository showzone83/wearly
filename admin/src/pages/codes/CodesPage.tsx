// 코드 관리: 그룹 탭 → 표(코드·라벨·상위·순서·사용 여부·사용 수) → 추가·수정 패널
import { useMemo, useState } from 'react';

import { api } from '@/api';
import type { IAdminCode, IAdminCodeGroup } from '@/api';

import { ColorChip, GarmentPreview } from '@/components/Garment';
import { Badge, Button, Card, Empty, ErrorBox, Loading, Notice, PageHeader, Switch } from '@/components/ui';
import { CodeForm } from '@/pages/codes/CodeForm';

import { useConfirmStore } from '@/stores/useConfirmStore';
import { useToastStore } from '@/stores/useToastStore';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { HAS_APP_SHAPES } from '@/lib/garment';
import { qk, useCodeBook, useCodes } from '@/lib/queries';

/** 표시 순서: sortOrder, 같으면 받은 순서 */
const ordered = (codes: IAdminCode[]): IAdminCode[] => {
  return codes
    .map((c, i) => ({ c, i }))
    .sort((a, b) => a.c.sortOrder - b.c.sortOrder || a.i - b.i)
    .map((x) => x.c);
};

const VOLUME_LABEL: Record<string, string> = { '-1': '슬림', '0': '레귤러', '1': '볼륨' };

export const CodesPage = () => {
  const [q, setQ] = useState('');
  const [parentFilter, setParentFilter] = useState('');
  const [hideInactive, setHideInactive] = useState(false);
  const [editing, setEditing] = useState<IAdminCode | 'new' | null>(null);

  const [params, setParams] = useSearchParams();
  const { data, error, isLoading, refetch } = useCodes();
  const book = useCodeBook();
  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  const toastError = useToastStore((s) => s.showError);
  const confirm = useConfirmStore((s) => s.ask);
  const reorder = useMutation({
    mutationFn: (codes: string[]) => api.codes.reorder(g, codes),
    onMutate: async (codes) => {
      // 화면을 먼저 바꾸고, 실패하면 되돌린다
      await queryClient.cancelQueries({ queryKey: qk.codes });
      const prev = queryClient.getQueryData(qk.codes);
      queryClient.setQueryData(qk.codes, (old: typeof data) =>
        old
          ? {
              ...old,
              groups: old.groups.map((gr) =>
                gr.group !== g
                  ? gr
                  : { ...gr, codes: gr.codes.map((c) => ({ ...c, sortOrder: (codes.indexOf(c.code) + 1) * 10 })) },
              ),
            }
          : old,
      );
      return { prev };
    },
    onError: (e, _v, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(qk.codes, ctx.prev);
      toastError(e, '순서를 바꾸지 못했습니다');
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: qk.codes });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });

  const toggleActive = useMutation({
    mutationFn: (c: IAdminCode) => api.codes.update(g, c.code, { active: !c.active }),
    onSuccess: async (_r, c) => {
      await queryClient.invalidateQueries({ queryKey: qk.codes });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(`${c.label}: ${c.active ? '비활성화' : '사용'}로 바꿨습니다.`);
    },
    onError: (e) => toastError(e),
  });

  const groups = data?.groups ?? [];
  const activeGroup: IAdminCodeGroup | undefined = groups.find((g) => g.group === params.get('group')) ?? groups[0];
  const g = activeGroup?.group ?? '';
  const all = useMemo(() => (activeGroup ? ordered(activeGroup.codes) : []), [activeGroup]);
  const filtering = q.trim() !== '';
  const visible = all.filter((c) => {
    if (hideInactive && !c.active) return false;
    if (g === 'SUB_TYPE' && parentFilter && c.parent !== parentFilter) return false;
    if (filtering) {
      const s = q.trim().toLowerCase();
      return c.code.toLowerCase().includes(s) || c.label.toLowerCase().includes(s);
    }
    return true;
  });
  const canReorder = !filtering && !reorder.isPending;
  const categories = book.group('CATEGORY');

  const selectGroup = (group: string) => {
    setParams({ group }, { replace: true });
    setQ('');
    setParentFilter('');
    setEditing(null);
  };

  /** 보이는 목록 안에서 이웃과 자리를 바꾸고, 그룹 전체 순서를 보낸다 */
  const move = (code: IAdminCode, dir: -1 | 1) => {
    const vi = visible.indexOf(code);
    const neighbor = visible[vi + dir];
    if (!neighbor) return;
    const next = all.map((c) => c.code);
    const a = next.indexOf(code.code);
    const b = next.indexOf(neighbor.code);
    [next[a], next[b]] = [next[b], next[a]];
    reorder.mutate(next);
  };

  const onToggle = async (c: IAdminCode) => {
    if (c.active && g === 'CATEGORY') {
      const children = book.group('SUB_TYPE').filter((s) => s.parent === c.code && s.active).length;
      const ok = await confirm({
        title: '종류 비활성화',
        message: `${c.label} 종류를 비활성화하면 하위 세부 종류 ${children}개도 새로 고를 수 없게 됩니다. 계속할까요?`,
        confirmLabel: '비활성화',
        tone: 'danger',
      });
      if (!ok) return;
    }
    toggleActive.mutate(c);
  };

  const childCount = (cat: string) => book.group('SUB_TYPE').filter((s) => s.parent === cat).length;

  return (
    <>
      <PageHeader
        title="코드 관리"
        desc={
          <>
            옷 등록 화면의 값과 추천 엔진 속성. 바꾸면 앱이 다음에 코드를 받을 때 반영됩니다.
            {data?.version ? <span className="mono muted"> · version {data.version}</span> : null}
          </>
        }
        actions={
          activeGroup ? (
            <Button variant="primary" onClick={() => setEditing('new')}>
              + {activeGroup.name} 추가
            </Button>
          ) : null
        }
      />
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {!HAS_APP_SHAPES && data ? (
        <Notice tone="warn">
          앱 일러스트 데이터(mobile/src/data/garmentShapes.ts)를 찾지 못해 옷 미리보기를 임시 사각형으로 그립니다.
        </Notice>
      ) : null}

      {activeGroup ? (
        <>
          <div className="tabs" role="tablist">
            {groups.map((gr) => (
              <button
                type="button"
                role="tab"
                key={gr.group}
                aria-selected={gr.group === g}
                className={gr.group === g ? 'tab on' : 'tab'}
                onClick={() => selectGroup(gr.group)}
              >
                {gr.name}
                <span className="tab-count">{gr.codes.length}</span>
              </button>
            ))}
          </div>

          <Card>
            <div className="toolbar">
              <div>
                <strong>{activeGroup.name}</strong> <span className="mono muted">{activeGroup.group}</span>
                {activeGroup.description ? <div className="muted small">{activeGroup.description}</div> : null}
              </div>
              <div className="row gap-sm wrap">
                {g === 'SUB_TYPE' ? (
                  <select value={parentFilter} onChange={(e) => setParentFilter(e.target.value)} aria-label="상위 종류">
                    <option value="">모든 상위 종류</option>
                    {categories.map((c) => (
                      <option key={c.code} value={c.code}>
                        {c.label} ({childCount(c.code)})
                      </option>
                    ))}
                  </select>
                ) : null}
                <input
                  type="search"
                  placeholder="코드·라벨 검색"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  className="w-search"
                />
                <Switch checked={hideInactive} onChange={setHideInactive} label="비활성 숨기기" />
              </div>
            </div>
            {filtering ? <p className="muted small">검색 중에는 순서를 바꿀 수 없습니다.</p> : null}

            {visible.length === 0 ? (
              <Empty>코드가 없습니다.</Empty>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th className="w-order">순서</th>
                      {g === 'SUB_TYPE' || g === 'COLOR' ? <th className="w-preview" /> : null}
                      <th>코드</th>
                      <th>라벨</th>
                      {g === 'SUB_TYPE' ? <th>상위</th> : null}
                      <th>속성</th>
                      <th className="num">순서값</th>
                      <th>사용</th>
                      <th className="num">사용 수</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((c, i) => (
                      <tr key={c.code} className={c.active ? '' : 'row-inactive'}>
                        <td>
                          <div className="order-btns">
                            <button
                              type="button"
                              className="icon-btn"
                              aria-label="위로"
                              disabled={!canReorder || i === 0}
                              onClick={() => move(c, -1)}
                            >
                              ▲
                            </button>
                            <button
                              type="button"
                              className="icon-btn"
                              aria-label="아래로"
                              disabled={!canReorder || i === visible.length - 1}
                              onClick={() => move(c, 1)}
                            >
                              ▼
                            </button>
                          </div>
                        </td>
                        {g === 'SUB_TYPE' ? (
                          <td>
                            <GarmentPreview shape={c.attrs.shape} category={c.parent} hex="#3C4250" size={34} />
                          </td>
                        ) : g === 'COLOR' ? (
                          <td>
                            <ColorChip hex={c.attrs.hex} size={22} />
                          </td>
                        ) : null}
                        <td className="mono">{c.code}</td>
                        <td>
                          <button type="button" className="link-btn strong" onClick={() => setEditing(c)}>
                            {c.label}
                          </button>
                        </td>
                        {g === 'SUB_TYPE' ? (
                          <td>
                            {book.label('CATEGORY', c.parent)}
                            {book.find('CATEGORY', c.parent)?.active === false ? (
                              <Badge tone="red" title="상위 종류가 비활성이라 새로 고를 수 없습니다">
                                상위 비활성
                              </Badge>
                            ) : null}
                          </td>
                        ) : null}
                        <td className="attrs-cell">
                          <AttrSummary group={g} code={c} label={(code) => book.label('COLOR', code)} />
                        </td>
                        <td className="num mono">{c.sortOrder}</td>
                        <td>
                          <Switch
                            checked={c.active}
                            disabled={toggleActive.isPending}
                            onChange={() => void onToggle(c)}
                            title={c.active ? '끄면 새로 고를 수 없게 됩니다' : '다시 사용'}
                          />
                        </td>
                        <td className="num">{c.usageCount.toLocaleString('ko-KR')}</td>
                        <td className="actions">
                          <Button size="sm" variant="ghost" onClick={() => setEditing(c)}>
                            수정
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      ) : null}

      {editing && activeGroup ? (
        <CodeForm
          key={editing === 'new' ? 'new' : editing.code}
          group={activeGroup}
          original={editing === 'new' ? undefined : editing}
          book={book}
          onClose={() => setEditing(null)}
          onDeactivateInstead={(c) => {
            toggleActive.mutate(c);
            setEditing(null);
          }}
        />
      ) : null}
    </>
  );
};

/** 주소의 호스트만 (형식이 틀리면 그대로) */
const urlHost = (url: string): string => {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
};

const AttrSummary = ({ group, code, label }: { group: string; code: IAdminCode; label: (color: string) => string }) => {
  const a = code.attrs ?? {};
  switch (group) {
    case 'CATEGORY':
      return a.slot ? <Badge tone="ink">{a.slot}</Badge> : <span className="muted">코디 제외</span>;
    case 'SUB_TYPE':
      return (
        <span className="small">
          {VOLUME_LABEL[String(a.volume)] ?? a.volume} · 보온 {String(a.warmth ?? '—')} ·{' '}
          <span className="mono">{a.shape ?? '—'}</span>
        </span>
      );
    case 'COLOR':
      return (
        <span className="small">
          <span className="mono">{a.hex}</span> · 밝기 {typeof a.light === 'number' ? a.light.toFixed(2) : '—'}
          {a.neutral ? ' · 뉴트럴' : ''} ·{' '}
          <span title={(a.pairs ?? []).map(label).join(', ')}>어울림 {(a.pairs ?? []).length}</span>
        </span>
      );
    case 'ITEM_STATUS':
      return a.recommendable ? <Badge tone="green">추천 후보</Badge> : <Badge tone="gray">추천 제외</Badge>;
    case 'BODY_TYPE':
      return (
        <span className="small" title={typeof a.hint === 'string' ? a.hint : undefined}>
          {typeof a.description === 'string' && a.description ? a.description : '—'}
        </span>
      );
    case 'MALL':
      return typeof a.url === 'string' && a.url ? (
        <a className="mono small" href={a.url} target="_blank" rel="noopener noreferrer" title={a.url}>
          {urlHost(a.url)}
        </a>
      ) : (
        <span className="muted">—</span>
      );
    default:
      return Object.keys(a).length ? (
        <span className="mono small">{JSON.stringify(a)}</span>
      ) : (
        <span className="muted">—</span>
      );
  }
};
