import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from '@/App';
import { ConfirmHost } from '@/components/ConfirmHost';
import { Toaster } from '@/components/Toaster';

import { useAuthStore } from '@/stores/useAuthStore';

import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';

import { queryClient } from '@/lib/queryClient';

import '@/styles.css';

// 새로 고침 뒤 저장된 토큰이 있으면 내 정보를 다시 확인한다
void useAuthStore.getState().restore();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
        <Toaster />
        <ConfirmHost />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
