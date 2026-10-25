// 여러 화면이 함께 쓰는 서버 상태 (TanStack Query)
import { useMemo } from 'react';

import { api, isApiError } from '@/api';
import type {
  IAdminCode,
  IAdminCodeGroup,
  IAppThemeUpdate,
  IWeatherProviderPatch,
  IWeatherRun,
  TWeatherSettingUpdate,
} from '@/api';

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
  /** 내 2단계 인증 상태 (내 계정 화면) */
  myMfa: ['me', 'mfa'] as const,
  /** 내 비밀번호 변경 주기 (화면 위 안내 띠·내 계정 화면, doc/28) */
  myPassword: ['me', 'password'] as const,
  /** 관리자 로그인 기록 */
  authEvents: (q: object) => ['auth-events', q] as const,
  inviteSignups: (q: object) => ['invite-signups', q] as const,
  inviteShares: (q: object) => ['invite-shares', q] as const,
  pairings: (q: object) => ['pairings', q] as const,
  /** 'withdrawals'로 시작하는 키를 무효화하면 목록과 요약이 함께 다시 읽힌다 */
  withdrawals: (q: object) => ['withdrawals', 'list', q] as const,
  withdrawalSummary: (days: number) => ['withdrawals', 'summary', days] as const,
  pairingActions: (id: string) => ['pairing-actions', id] as const,
  audit: (q: object) => ['audit', q] as const,
  appTheme: ['app', 'theme'] as const,
  /** 'sessions'로 시작하는 키를 무효화하면 목록과 회원별 세션이 함께 다시 읽힌다 */
  sessions: (q: object) => ['sessions', 'list', q] as const,
  userSessions: (userId: string) => ['sessions', 'user', userId] as const,
  /** 'weather'로 시작하는 키를 무효화하면 날씨 화면 전체가 다시 읽힌다 */
  weatherSchedule: ['weather', 'schedule'] as const,
  weatherProviders: ['weather', 'providers'] as const,
  weatherRuns: (q: object) => ['weather', 'runs', q] as const,
  weatherRun: (runId: string) => ['weather', 'run', runId] as const,
  weatherCalls: (q: object) => ['weather', 'calls', q] as const,
  weatherSummary: ['weather', 'summary'] as const,
  weatherSnapshots: ['weather', 'snapshots'] as const,
  weatherSnapshot: (region: string) => ['weather', 'snapshot', region] as const,
};

/** 날씨 실행이 도는 동안 상태를 다시 묻는 간격 */
const WEATHER_RUN_POLL_MS = 1_000;

/** 스케줄 설정 + 다음 실행 + 마지막 실행 (doc/17 6장). 실행 중이면 2초마다 다시 읽는다 */
export const useWeatherSchedule = () => {
  return useQuery({
    queryKey: qk.weatherSchedule,
    queryFn: () => api.weather.schedule(),
    refetchInterval: (q) => (q.state.data?.running ? 2_000 : 30_000),
  });
};

export const useWeatherProviders = () => {
  return useQuery({ queryKey: qk.weatherProviders, queryFn: () => api.weather.providers() });
};

/** 실행 하나 (호출 목록 포함). 끝날 때까지 1초마다 다시 읽는다 */
export const useWeatherRun = (runId: string | null) => {
  return useQuery({
    queryKey: qk.weatherRun(runId ?? ''),
    queryFn: () => api.weather.run(runId!),
    enabled: !!runId,
    refetchInterval: (q) => (q.state.data?.run.status === 'RUNNING' ? WEATHER_RUN_POLL_MS : false),
  });
};

export const useUpdateWeatherSetting = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TWeatherSettingUpdate) => api.weather.updateSetting(body),
    onSuccess: (data) => {
      queryClient.setQueryData(qk.weatherSchedule, data);
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

export const useUpdateWeatherProvider = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, patch }: { code: string; patch: IWeatherProviderPatch }) =>
      api.weather.updateProvider(code, patch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['weather'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

export const useResetWeatherProvider = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => api.weather.resetProvider(code),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['weather'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

/** 지금 실행이 이보다 오래 걸리면 기다리기를 그만둔다 (서버 실행은 계속된다) */
const WEATHER_RUN_WAIT_MS = 120_000;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * 지금 실행하고 끝날 때까지 기다린다. 끝난 실행(IWeatherRun)을 돌려주고, 너무 오래 걸리면 마지막으로 본 실행(RUNNING)을 돌려준다.
 * 끝나면 날씨 화면 전체와 변경 이력을 다시 읽게 한다.
 */
export const useWeatherRefresh = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (regionCode?: string): Promise<IWeatherRun> => {
      const { runId } = await api.weather.refresh(regionCode);
      void queryClient.invalidateQueries({ queryKey: qk.weatherSchedule });
      const deadline = Date.now() + WEATHER_RUN_WAIT_MS;
      let run = (await api.weather.run(runId)).run;
      while (run.status === 'RUNNING' && Date.now() < deadline) {
        await sleep(WEATHER_RUN_POLL_MS);
        run = (await api.weather.run(runId)).run;
      }
      return run;
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['weather'] });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
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

/** 앱 테마·계절 모드 바꾸기 (보낸 값만). 성공하면 현재 테마 캐시를 응답으로 바꾸고 변경 이력을 다시 읽게 한다 */
export const useUpdateAppTheme = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: IAppThemeUpdate) => api.appSettings.setTheme(body),
    onSuccess: (data) => {
      // 본문 없이(204) 오면 themeId가 비어 있으므로 서버에서 다시 읽는다
      if (data.themeId) queryClient.setQueryData(qk.appTheme, data);
      else void queryClient.invalidateQueries({ queryKey: qk.appTheme });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
  });
};

/** 내 비밀번호 변경 주기 (doc/28). 예전 서버(404)·오류면 다시 묻지 않는다 (안내 띠는 부가 기능) */
export const useMyPasswordStatus = () => {
  return useQuery({
    queryKey: qk.myPassword,
    queryFn: () => api.me.passwordStatus(),
    retry: false,
    staleTime: 5 * 60_000,
  });
};

/** 비밀번호 변경 알림 연장. 성공하면 상태 캐시를 응답으로 바꾸고 변경 이력을 다시 읽게 한다 */
export const usePostponePassword = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.me.postponePassword(),
    onSuccess: (data) => {
      queryClient.setQueryData(qk.myPassword, data);
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      void queryClient.invalidateQueries({ queryKey: ['auth-events'] });
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
