import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import { isApiError } from '@/api';
import { App } from '@/App';
import { AuthProvider } from '@/auth/AuthContext';
import { ToastProvider } from '@/components/toast';
import '@/styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      // 인증·권한·검증 오류는 다시 해 봐야 같다
      retry: (count, e) => count < 1 && !(isApiError(e) && e.status >= 400 && e.status < 500),
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <App />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
