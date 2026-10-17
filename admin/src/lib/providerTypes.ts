import type { ProviderTypeSpec } from '@/api';

/** `/admin/ai/types`를 못 받았을 때 쓰는 기본 목록 (07 3장) */
export const FALLBACK_TYPES: ProviderTypeSpec[] = [
  { type: 'CLAUDE', label: 'Anthropic Claude', defaultApiKeyEnv: 'ANTHROPIC_API_KEY', options: [{ key: 'effort', type: 'string', description: '추론 노력 (예: low·medium·high)' }] },
  {
    type: 'OPENAI',
    label: 'OpenAI',
    defaultApiKeyEnv: 'OPENAI_API_KEY',
    options: [
      { key: 'temperature', type: 'number', description: '0~2' },
      { key: 'maxTokens', type: 'integer', description: '최대 출력 토큰' },
      { key: 'baseUrl', type: 'string', description: 'API 주소 (비우면 기본)' },
    ],
  },
  {
    type: 'GEMINI',
    label: 'Google Gemini',
    defaultApiKeyEnv: 'GEMINI_API_KEY',
    options: [
      { key: 'temperature', type: 'number', description: '0~2' },
      { key: 'maxTokens', type: 'integer', description: '최대 출력 토큰' },
      { key: 'baseUrl', type: 'string', description: 'API 주소 (비우면 기본)' },
    ],
  },
];

export const DEFAULT_KEY_ENV: Record<string, string> = { CLAUDE: 'ANTHROPIC_API_KEY', OPENAI: 'OPENAI_API_KEY', GEMINI: 'GEMINI_API_KEY' };
