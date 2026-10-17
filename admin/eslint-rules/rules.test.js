// 로컬 규칙(local/component-order)과 import 그룹 설정(simple-import-sort + IMPORT_GROUPS)의 단위 테스트.
// 실행: npm run test:lint-rules  (실패하면 RuleTester가 예외를 던져 0이 아닌 코드로 끝난다)
import assert from 'node:assert/strict';

import stylistic from '@stylistic/eslint-plugin';
import { Linter, RuleTester } from 'eslint';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import tseslint from 'typescript-eslint';

import { componentOrder } from './component-order.js';
import { IMPORT_GROUPS } from './import-groups.js';

const tester = new RuleTester({
  languageOptions: {
    parser: tseslint.parser,
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
});

// 순서 위반 메시지 하나
const order = (current, previous) => ({
  messageId: 'order',
  data: { name: 'Page', current, previous },
});
const L = {
  ref: 'ref(useRef)',
  state: 'state(useState·useReducer)',
  hooks: 'hooks(그 밖의 훅)',
  effect: 'effect(useEffect·useLayoutEffect)',
  memo: 'memo(useMemo·파생 값)',
  event: 'event(핸들러·함수·useCallback)',
};

tester.run('local/component-order', componentOrder, {
  valid: [
    // 여섯 분류를 순서대로, 분류 사이 빈 줄
    {
      name: '맞는 순서',
      filename: 'Page.tsx',
      code: `
export const Page = ({ id }: { id: string }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const [q, setQ] = useState('');
  const [state, dispatch] = useReducer(reducer, init);

  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const { data } = useQuery({ queryKey: ['x', id], queryFn: load });

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const total = useMemo(() => sum(data), [data]);
  const visible = (data ?? []).filter((x) => x.includes(q));

  const handleSave = () => navigate('/');
  const onPick = useCallback(() => dispatch({ type: 'pick' }), []);

  return <div>{visible.length}{total}{user?.name}{state.n}</div>;
};`,
    },
    // 커스텀 훅도 같은 규칙. 분류할 수 없는 문장(if, 일반 함수 호출)은 순서에 끼지 않는다
    {
      name: '커스텀 훅과 분류 없는 문장',
      filename: 'useThing.ts',
      code: `
export const useThing = (id: string) => {
  const [n, setN] = useState(0);

  const client = useQueryClient();

  if (!id) console.warn('id 없음');
  const label = \`#\${n}\`;

  const bump = () => setN((x) => x + 1);
  return { label, bump, client };
};`,
    },
    // 바로 위 줄에 "순서 예외:" 주석이 있는 문장은 건너뛴다 (TDZ)
    {
      name: '순서 예외 주석',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  // 순서 예외: draft 초깃값이 params를 읽는다
  const [params] = useSearchParams();

  const [draft, setDraft] = useState(() => params.get('q') ?? '');

  const toast = useToastStore((s) => s.show);
  // 순서 예외: 아래 effect의 의존성 배열이 running을 읽는다
  const running = draft !== '';

  useEffect(() => {
    if (running) toast('검색 중');
  }, [running, toast]);

  const reset = () => setDraft('');
  return <button onClick={reset} />;
};`,
    },
    // 대상이 아닌 함수: 소문자 이름의 일반 함수, 식 본문 화살표 함수
    {
      name: '대상 아님',
      filename: 'util.ts',
      code: `
const helper = () => {
  const f = () => 1;
  const [a] = useState(0);
  return f() + a;
};
export const useShort = () => useQuery({ queryKey: ['a'], queryFn: load });`,
    },
    // memo(() => {}) 로 감싼 컴포넌트, React.useState 처럼 네임스페이스 호출
    {
      name: 'memo로 감싼 컴포넌트',
      filename: 'Item.tsx',
      code: `
export const Item = memo(() => {
  const ref = React.useRef(null);

  const [open, setOpen] = React.useState(false);

  return <div ref={ref} onClick={() => setOpen(!open)} />;
});`,
    },
  ],
  invalid: [
    {
      name: 'state가 hooks 뒤',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  const navigate = useNavigate();

  const [q, setQ] = useState('');

  return <a onClick={() => navigate(q)} onChange={setQ} />;
};`,
      errors: [order(L.state, L.hooks)],
    },
    {
      name: 'ref가 state 뒤, effect가 memo 뒤, memo가 event 뒤',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  const [n, setN] = useState(0);

  const ref = useRef(null);

  const double = n * 2;

  useEffect(() => {}, []);

  const inc = () => setN(n + 1);

  const label = useMemo(() => String(double), [double]);
  return <div ref={ref} onClick={inc}>{label}</div>;
};`,
      errors: [order(L.ref, L.state), order(L.effect, L.memo), order(L.memo, L.event)],
    },
    {
      name: 'useCallback(event) 뒤의 useMutation(hooks)',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  const onSave = useCallback(() => {}, []);

  const save = useMutation({ mutationFn: send });
  return <button onClick={() => save.mutate(onSave)} />;
};`,
      errors: [order(L.hooks, L.event)],
    },
    {
      // 빈 줄이 있어도 예외 주석이 문장에 붙어 있지 않으면 예외가 아니다
      name: '떨어진 예외 주석은 무시',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  const user = useAuthStore((s) => s.user);
  // 순서 예외: 떨어져 있음

  const [q] = useState('');

  return <div>{user?.name}{q}</div>;
};`,
      errors: [order(L.state, L.hooks)],
    },
    {
      name: '분류 사이 빈 줄 (자동 수정)',
      filename: 'Page.tsx',
      code: `
export const Page = () => {
  const [q] = useState('');
  // 사용자
  const user = useAuthStore((s) => s.user);
  const label = q + user?.name;
  return <div>{label}</div>;
};`,
      output: `
export const Page = () => {
  const [q] = useState('');

  // 사용자
  const user = useAuthStore((s) => s.user);

  const label = q + user?.name;
  return <div>{label}</div>;
};`,
      errors: [
        { messageId: 'blankLine', data: { name: 'Page', previous: L.state, current: L.hooks } },
        { messageId: 'blankLine', data: { name: 'Page', previous: L.hooks, current: L.memo } },
      ],
    },
  ],
});

tester.run('simple-import-sort/imports (IMPORT_GROUPS)', simpleImportSort.rules.imports, {
  valid: [
    {
      name: '그룹 순서: react → api → component → hooks → 3rd party → local → css',
      filename: 'src/pages/Some.tsx',
      options: [{ groups: IMPORT_GROUPS }],
      code: `import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { FormEvent } from 'react';

import { api } from '@/api';
import type { IAiProvider } from '@/api';

import { App } from '@/App';
import { Button } from '@/components/ui';
import { CodeForm } from '@/pages/codes/CodeForm';

import { useAuthStore } from '@/stores/useAuthStore';

import { useQuery } from '@tanstack/react-query';
import { existsSync } from 'node:fs';
import { Link } from 'react-router';
import { create } from 'zustand';
import type { QueryClient } from '@tanstack/react-query';

import { fmtDate } from '@/lib/format';
import { SHAPES } from '@garment-shapes';
import type { ICodeBook } from '@/lib/queries';

import '@/styles.css';
`,
    },
  ],
  invalid: [],
});

// 정렬 + 쉼표 공백을 eslint.config.js와 같이 함께 --fix 했을 때의 결과
{
  const linter = new Linter({ configType: 'flat' });
  const config = [
    {
      files: ['**/*.tsx'],
      languageOptions: { parser: tseslint.parser },
      plugins: { 'simple-import-sort': simpleImportSort, '@stylistic': stylistic },
      rules: {
        'simple-import-sort/imports': ['error', { groups: IMPORT_GROUPS }],
        '@stylistic/comma-spacing': 'error',
      },
    },
  ];
  const input = `import '@/styles.css';
import { qk } from '@/lib/queries';
import type { IAiProvider } from '@/api';
import { useQuery } from '@tanstack/react-query';
import { useToastStore } from '@/stores/useToastStore';
import { Button } from '@/components/ui';
import { useState, useEffect } from 'react';
import { api } from '@/api';
`;
  const expected = `import { useEffect, useState } from 'react';

import { api } from '@/api';
import type { IAiProvider } from '@/api';

import { Button } from '@/components/ui';

import { useToastStore } from '@/stores/useToastStore';

import { useQuery } from '@tanstack/react-query';

import { qk } from '@/lib/queries';

import '@/styles.css';
`;
  const result = linter.verifyAndFix(input, config, { filename: 'src/pages/Some.tsx' });
  assert.deepEqual(result.messages, [], '고친 뒤에도 남은 오류가 없어야 한다');
  assert.equal(result.output, expected, '섞인 순서를 그룹대로 정렬하고 이름 목록도 알파벳순');
}

console.log('local/component-order, simple-import-sort(IMPORT_GROUPS) 테스트 통과');
