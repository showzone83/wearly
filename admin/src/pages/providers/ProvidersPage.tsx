// AI 공급자: 목록(키 출처·사용 가능·오늘 호출), 추가·수정(+ API 키 웹 입력), 사용 켜기/끄기, 우선순위, 시험 분석
import { useState } from 'react';

import { api, errorMessage } from '@/api';
import type { IAiProvider } from '@/api';

import { Badge, Button, Card, Empty, ErrorBox, Loading, PageHeader, Switch } from '@/components/ui';
import { KeySourceBadge } from '@/pages/providers/ApiKeySection';
import { ProviderForm } from '@/pages/providers/ProviderForm';
import { TestPanel } from '@/pages/providers/TestPanel';

import { useToastStore } from '@/stores/useToastStore';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { FALLBACK_TYPES } from '@/lib/providerTypes';
import { qk, useProviders } from '@/lib/queries';

const usableReason = (p: IAiProvider): string => {
  const missing: string[] = [];
  if (!p.enabled) missing.push('사용 꺼짐');
  if (!p.model) missing.push('모델 없음');
  if (!p.keyConfigured) missing.push('키 없음');
  return missing.join(' · ');
};

export const ProvidersPage = () => {
  // 행 객체가 아니라 ID를 들고 있다가 최신 목록에서 찾는다 (키 저장 뒤 패널이 옛 값을 보지 않게)
  const [editingId, setEditingId] = useState<string | 'new' | null>(null);

  const { data, error, isLoading, refetch } = useProviders();
  const typesQuery = useQuery({
    queryKey: qk.providerTypes,
    queryFn: () => api.providers.types(),
    staleTime: 5 * 60_000,
  });
  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  // 순서 예외: 아래 swap의 onSuccess 옵션이 렌더 중에 invalidate를 바로 읽는다
  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: qk.providers });
    void queryClient.invalidateQueries({ queryKey: ['audit'] });
  };

  const toggle = useMutation({
    mutationFn: (p: IAiProvider) => api.providers.update(p.providerId, { enabled: !p.enabled }),
    onSuccess: async (_r, p) => {
      await invalidate();
      toast(`${p.displayName}: ${p.enabled ? '사용 끔' : '사용 켬'}`);
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  const swap = useMutation({
    mutationFn: async ([a, b]: [IAiProvider, IAiProvider]) => {
      // 우선순위 값이 같으면 자리만 바꿔도 순서가 정해지도록 서로 다른 값을 준다
      const pa = a.priority === b.priority ? b.priority + 1 : b.priority;
      await api.providers.update(a.providerId, { priority: pa });
      await api.providers.update(b.providerId, { priority: a.priority });
    },
    onSuccess: invalidate,
    onError: async (e) => {
      toast(`우선순위를 바꾸지 못했습니다: ${errorMessage(e)}`, 'error');
      await invalidate();
    },
  });

  const remove = useMutation({
    mutationFn: (p: IAiProvider) => api.providers.remove(p.providerId),
    onSuccess: async (_r, p) => {
      await invalidate();
      toast(`${p.providerId} 공급자를 삭제했습니다.`);
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  const providers = (data ?? [])
    .slice()
    .sort((a, b) => a.priority - b.priority || a.providerId.localeCompare(b.providerId));
  const nextPriority = providers.length ? Math.max(...providers.map((p) => p.priority)) + 1 : 1;
  const editing = editingId === 'new' ? 'new' : (providers.find((p) => p.providerId === editingId) ?? null);
  const noneUsable = !!data && providers.every((p) => !p.usable);

  const setEditing = (p: IAiProvider | 'new' | null) => setEditingId(p === 'new' || p === null ? p : p.providerId);

  const onDelete = (p: IAiProvider) => {
    if (window.confirm(`${p.displayName}(${p.providerId}) 공급자를 삭제할까요? 분석 기록은 남습니다.`))
      remove.mutate(p);
  };

  return (
    <>
      <PageHeader
        title="AI 공급자"
        desc="사진 분석에 쓸 AI. 사용 중이고 모델·키가 갖춰진 공급자를 우선순위 순으로 시도하고, 실패하면 다음 공급자로 넘어갑니다."
        actions={
          <Button variant="primary" onClick={() => setEditing('new')}>
            + 공급자 추가
          </Button>
        }
      />
      <ErrorBox error={error} onRetry={() => void refetch()} />
      {isLoading ? <Loading /> : null}
      {noneUsable ? (
        <div className="alert alert-warn">
          지금 쓸 수 있는 공급자가 없어 앱의 사진 분석이 <strong>AI_UNAVAILABLE</strong>로 실패합니다. (서버를 목업으로
          띄운 경우는 예외)
        </div>
      ) : null}

      {data ? (
        <Card title="공급자 목록">
          {providers.length === 0 ? (
            <Empty>공급자가 없습니다.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th className="w-order">순서</th>
                    <th className="num">우선순위</th>
                    <th>공급자</th>
                    <th>종류</th>
                    <th>모델</th>
                    <th>API 키</th>
                    <th>사용</th>
                    <th>사용 가능</th>
                    <th className="num">오늘 성공 / 실패</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {providers.map((p, i) => (
                    <tr key={p.providerId}>
                      <td>
                        <div className="order-btns">
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label="위로"
                            disabled={i === 0 || swap.isPending}
                            onClick={() => swap.mutate([p, providers[i - 1]])}
                          >
                            ▲
                          </button>
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label="아래로"
                            disabled={i === providers.length - 1 || swap.isPending}
                            onClick={() => swap.mutate([p, providers[i + 1]])}
                          >
                            ▼
                          </button>
                        </div>
                      </td>
                      <td className="num mono">{p.priority}</td>
                      <td>
                        <button type="button" className="link-btn strong" onClick={() => setEditing(p)}>
                          {p.displayName}
                        </button>
                        <div className="mono muted small">{p.providerId}</div>
                      </td>
                      <td>
                        <Badge tone="ink">{p.type}</Badge>
                      </td>
                      <td className="mono small">{p.model || <span className="muted">—</span>}</td>
                      <td>
                        <KeySourceBadge provider={p} />
                        <button type="button" className="link-btn small" onClick={() => setEditing(p)}>
                          키 관리
                        </button>
                      </td>
                      <td>
                        <Switch
                          checked={p.enabled}
                          disabled={toggle.isPending || (!p.enabled && !p.model)}
                          onChange={() => toggle.mutate(p)}
                          title={!p.model ? '모델 ID를 먼저 입력하세요' : undefined}
                        />
                      </td>
                      <td>
                        {p.usable ? (
                          <Badge tone="green">사용 가능</Badge>
                        ) : (
                          <Badge tone="gray" title={usableReason(p)}>
                            {usableReason(p) || '불가'}
                          </Badge>
                        )}
                      </td>
                      <td className="num">
                        {p.today?.success ?? 0} /{' '}
                        <span className={p.today?.failed ? 'text-danger' : ''}>{p.today?.failed ?? 0}</span>
                      </td>
                      <td className="actions">
                        <Button size="sm" variant="ghost" onClick={() => setEditing(p)}>
                          수정
                        </Button>
                        <Button size="sm" variant="ghost" className="text-danger" onClick={() => onDelete(p)}>
                          삭제
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="muted small mt-sm">
            API 키는 공급자 수정 패널의 'API 키'에서 웹으로 넣을 수 있습니다(서버에 암호화 저장, 다시 볼 수 없음). 웹
            키가 있으면 서버 설정 키보다 먼저 쓰고, 지우면 서버 설정 키로 돌아갑니다. 서버 설정 키를 바꾼 뒤에는 서버를
            다시 시작해야 반영됩니다.
          </p>
        </Card>
      ) : null}

      {data && providers.length ? <TestPanel providers={providers} /> : null}

      {editing ? (
        <ProviderForm
          key={editing === 'new' ? 'new' : editing.providerId}
          original={editing === 'new' ? undefined : editing}
          types={typesQuery.data?.length ? typesQuery.data : FALLBACK_TYPES}
          nextPriority={nextPriority}
          onClose={() => setEditing(null)}
        />
      ) : null}
    </>
  );
};
