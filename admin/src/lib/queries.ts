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
  /** 공급자 모델 목록. 키를 저장·삭제하면 allProviderModels로 무효화한다 */
  providerModels: (providerId: string) => ['ai', 'models', providerId] as const,
  allProviderModels: ['ai', 'models'] as const,
  aiLogs: (q: object) => ['ai', 'logs', q] as const,
  aiLog: (id: string) => ['ai', 'log', id] as const,
  users: (q: object) => ['users', q] as const,
  audit: (q: object) => ['audit', q] as const,
  appTheme: ['app', 'theme'] as const,
  /** 'sessions'로 시작하는 키를 무효화하면 목록과 회원별 세션이 함께 다시 읽힌다 */
  sessions: (q: object) => ['sessions', 'list', q] as const,
  userSessions: (userId: string) => ['sessions', 'user', userId] as const,
};

/** 로그인 사용자 화면·회원 세션 패널의 자동 새로 고침 간격 (doc/16 5장) */
const SESSION_REFETCH_MS = 30_000;

/** 30초마다 새로 고치되, 서버가 아직 이 API를 모르면(404) 다시 묻지 않는다 */
export const sessionRefetchInterval = (query: { state: { error: unknown } }): number | false => {
  return isUnsupportedApi(query.state.error) ? false : SESSION_REFETCH_MS;
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

/**
 * 저장한 공급자의 모델 드롭다운 목록 (doc/07 4장). 서버가 10분 캐시하므로 화면은 1분 동안 다시 묻지 않는다.
 * 서버가 이 API를 모르면(404) 다시 시도하지 않는다 → 화면은 직접 입력만.
 */
export const useProviderModels = (providerId: string | null) => {
  return useQuery({
    queryKey: qk.providerModels(providerId ?? ''),
    queryFn: () => api.providers.models(providerId!),
    enabled: !!providerId,
    staleTime: 60_000,
    retry: (count, e) => !isUnsupportedApi(e) && count < 1,
  });
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

/** 강제 로그아웃 대상 (doc/16 4장). who·device는 확인 대화상자·토스트 문구용 */
export type TRevokeTarget =
  | { kind: 'session'; sessionId: string; who: string; device: string }
  | { kind: 'user'; userId: string; who: string; count: number | null };

/** 회원 한 명의 세션과 최근 로그인 이력 (회원 화면의 패널) */
export const useUserSessions = (userId: string) => {
  return useQuery({
    queryKey: qk.userSessions(userId),
    queryFn: () => api.sessions.ofUser(userId),
    refetchInterval: sessionRefetchInterval,
  });
};

/** 세션 하나 또는 회원의 모든 세션 끊기. 성공하면 세션 목록·대시보드·변경 이력을 다시 읽게 한다 */
export const useRevokeSessions = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (target: TRevokeTarget) =>
      target.kind === 'session' ? api.sessions.revoke(target.sessionId) : api.sessions.revokeUser(target.userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sessions'] });
      void queryClient.invalidateQueries({ queryKey: qk.dashboard });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

/** 서버가 이 API를 아직 모른다 (백엔드 구현 전이면 404 NOT_FOUND) */
export const isUnsupportedApi = (e: unknown): boolean => {
  return isApiError(e) && e.status === 404;
};
