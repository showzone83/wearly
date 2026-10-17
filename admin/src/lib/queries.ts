// 여러 화면이 함께 쓰는 서버 상태 (TanStack Query)
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { api, type AdminCode, type AdminCodeGroup } from '@/api';

export const qk = {
  dashboard: ['dashboard'] as const,
  codes: ['codes'] as const,
  providers: ['ai', 'providers'] as const,
  providerTypes: ['ai', 'types'] as const,
  aiLogs: (q: object) => ['ai', 'logs', q] as const,
  aiLog: (id: string) => ['ai', 'log', id] as const,
  users: (q: object) => ['users', q] as const,
  audit: (q: object) => ['audit', q] as const,
};

export function useCodes() {
  return useQuery({ queryKey: qk.codes, queryFn: () => api.codes.list() });
}

export function useProviders() {
  return useQuery({ queryKey: qk.providers, queryFn: () => api.providers.list() });
}

export interface CodeBook {
  groups: AdminCodeGroup[];
  group(group: string): AdminCode[];
  find(group: string, code: string | null | undefined): AdminCode | undefined;
  /** 코드 → 라벨. 모르는 코드면 코드 문자열 그대로 */
  label(group: string, code: string | null | undefined): string;
}

/** 코드 목록을 찾아보기 쉬운 모양으로 */
export function useCodeBook(): CodeBook {
  const { data } = useCodes();
  return useMemo(() => {
    const groups = data?.groups ?? [];
    const byGroup = new Map(groups.map((g) => [g.group, g.codes]));
    const find = (group: string, code: string | null | undefined) => (code ? byGroup.get(group)?.find((c) => c.code === code) : undefined);
    return {
      groups,
      group: (group: string) => byGroup.get(group) ?? [],
      find,
      label: (group: string, code: string | null | undefined) => (code ? (find(group, code)?.label ?? code) : '—'),
    };
  }, [data]);
}
