// 여러 화면이 함께 쓰는 서버 상태 (TanStack Query)
import { useMemo } from 'react';

import { api, isApiError } from '@/api';
import type { IAdminCode, IAdminCodeGroup } from '@/api';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

export const qk = {
  dashboard: ['dashboard'] as const,
  codes: ['codes'] as const,
  providers: ['ai', 'providers'] as const,
  providerTypes: ['ai', 'types'] as const,
  aiLogs: (q: object) => ['ai', 'logs', q] as const,
  aiLog: (id: string) => ['ai', 'log', id] as const,
  users: (q: object) => ['users', q] as const,
  audit: (q: object) => ['audit', q] as const,
  appTheme: ['app', 'theme'] as const,
};

export const useCodes = () => {
  return useQuery({ queryKey: qk.codes, queryFn: () => api.codes.list() });
};

export const useProviders = () => {
  return useQuery({ queryKey: qk.providers, queryFn: () => api.providers.list() });
};

export interface ICodeBook {
  groups: IAdminCodeGroup[];
  group(group: string): IAdminCode[];
  find(group: string, code: string | null | undefined): IAdminCode | undefined;
  /** 코드 → 라벨. 모르는 코드면 코드 문자열 그대로 */
  label(group: string, code: string | null | undefined): string;
}

/** 코드 목록을 찾아보기 쉬운 모양으로 */
export const useCodeBook = (): ICodeBook => {
  const { data } = useCodes();
  return useMemo(() => {
    const groups = data?.groups ?? [];
    const byGroup = new Map(groups.map((g) => [g.group, g.codes]));
    const find = (group: string, code: string | null | undefined) =>
      code ? byGroup.get(group)?.find((c) => c.code === code) : undefined;
    return {
      groups,
      group: (group: string) => byGroup.get(group) ?? [],
      find,
      label: (group: string, code: string | null | undefined) => (code ? (find(group, code)?.label ?? code) : '—'),
    };
  }, [data]);
};

/** 지금 전체 사용자에게 적용 중인 앱 테마 (doc/12 3장). 백엔드가 아직 지원하지 않으면 404 */
export const useAppTheme = () => {
  return useQuery({ queryKey: qk.appTheme, queryFn: () => api.appSettings.theme() });
};

/** 앱 테마 바꾸기. 성공하면 현재 테마 캐시를 응답으로 바꾸고 변경 이력을 다시 읽게 한다 */
export const useUpdateAppTheme = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (themeId: string) => api.appSettings.setTheme({ themeId }),
    onSuccess: (data) => {
      // 본문 없이(204) 오면 themeId가 비어 있으므로 서버에서 다시 읽는다
      if (data.themeId) queryClient.setQueryData(qk.appTheme, data);
      else void queryClient.invalidateQueries({ queryKey: qk.appTheme });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

/** 서버가 이 API를 아직 모른다 (백엔드 구현 전이면 404 NOT_FOUND) */
export const isUnsupportedApi = (e: unknown): boolean => {
  return isApiError(e) && e.status === 404;
};
