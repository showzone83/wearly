// AI 공급자: 목록(키 설정·사용 가능·오늘 호출), 추가·수정, 사용 켜기/끄기, 우선순위, 시험 분석
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { api, errorMessage, type AiProvider } from '@/api';
import { useToast } from '@/components/toast';
import { Badge, Button, Card, Empty, ErrorBox, Loading, PageHeader, Switch } from '@/components/ui';
import { FALLBACK_TYPES } from '@/lib/providerTypes';
import { qk, useProviders } from '@/lib/queries';

import { ProviderForm } from './ProviderForm';
import { TestPanel } from './TestPanel';

function usableReason(p: AiProvider): string {
  const missing: string[] = [];
  if (!p.enabled) missing.push('사용 꺼짐');
  if (!p.model) missing.push('모델 없음');
  if (!p.keyConfigured) missing.push(`${p.apiKeyEnv} 값 없음`);
  return missing.join(' · ');
}

export function ProvidersPage() {
  const { data, error, isLoading, refetch } = useProviders();
  const typesQuery = useQuery({ queryKey: qk.providerTypes, queryFn: () => api.providers.types(), staleTime: 5 * 60_000 });
  const queryClient = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<AiProvider | 'new' | null>(null);

  const providers = (data ?? []).slice().sort((a, b) => a.priority - b.priority || a.providerId.localeCompare(b.providerId));
  const nextPriority = providers.length ? Math.max(...providers.map((p) => p.priority)) + 1 : 1;

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: qk.providers });
    void queryClient.invalidateQueries({ queryKey: ['audit'] });
  };

  const toggle = useMutation({
    mutationFn: (p: AiProvider) => api.providers.update(p.providerId, { enabled: !p.enabled }),
    onSuccess: async (_r, p) => {
      await invalidate();
      toast(`${p.displayName}: ${p.enabled ? '사용 끔' : '사용 켬'}`);
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  const swap = useMutation({
    mutationFn: async ([a, b]: [AiProvider, AiProvider]) => {
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
    mutationFn: (p: AiProvider) => api.providers.remove(p.providerId),
    onSuccess: async (_r, p) => {
      await invalidate();
      toast(`${p.providerId} 공급자를 삭제했습니다.`);
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  const onDelete = (p: AiProvider) => {
    if (window.confirm(`${p.displayName}(${p.providerId}) 공급자를 삭제할까요? 분석 기록은 남습니다.`)) remove.mutate(p);
  };

  const noneUsable = !!data && providers.every((p) => !p.usable);

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
          지금 쓸 수 있는 공급자가 없어 앱의 사진 분석이 <strong>AI_UNAVAILABLE</strong>로 실패합니다. (서버를 목업으로 띄운 경우는 예외)
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
                    <th>키 환경변수</th>
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
                          <button type="button" className="icon-btn" aria-label="위로" disabled={i === 0 || swap.isPending} onClick={() => swap.mutate([p, providers[i - 1]])}>
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
                        <span className="mono small">{p.apiKeyEnv}</span>{' '}
                        {p.keyConfigured ? <Badge tone="green">설정됨</Badge> : <Badge tone="red">없음</Badge>}
                      </td>
                      <td>
                        <Switch
                          checked={p.enabled}
                          disabled={toggle.isPending || (!p.enabled && !p.model)}
                          onChange={() => toggle.mutate(p)}
                          title={!p.model ? '모델 ID를 먼저 입력하세요' : undefined}
                        />
                      </td>
                      <td>{p.usable ? <Badge tone="green">사용 가능</Badge> : <Badge tone="gray" title={usableReason(p)}>{usableReason(p) || '불가'}</Badge>}</td>
                      <td className="num">
                        {p.today?.success ?? 0} / <span className={p.today?.failed ? 'text-danger' : ''}>{p.today?.failed ?? 0}</span>
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
          <p className="muted small mt-sm">API 키 값은 서버 환경변수(backend/.env)에만 둡니다. 키를 바꾼 뒤에는 서버를 다시 시작해야 '설정됨'으로 바뀝니다.</p>
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
}
