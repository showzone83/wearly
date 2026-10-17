// 사이트 전체가 함께 쓰는 React Query 캐시. 로그아웃·인증 상실 때 스토어(useAuthStore)가 컴포넌트 밖에서 비운다.
import { QueryClient } from '@tanstack/react-query';

import { isApiError } from '@/api';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // 인증·권한·검증 오류는 다시 해 봐야 같다
      retry: (count, e) => count < 1 && !(isApiError(e) && e.status >= 400 && e.status < 500),
    },
  },
});
