import js from '@eslint/js';
import stylistic from '@stylistic/eslint-plugin';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

import { IMPORT_GROUPS } from './eslint-rules/import-groups.js';
import local from './eslint-rules/index.js';

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
  },
  // 코드 규칙 (README "코드 규칙" 절)
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { local, 'simple-import-sort': simpleImportSort, '@stylistic': stylistic },
    rules: {
      // 1. 함수는 화살표 함수로. 클래스 메서드·생성자, getter/setter, 제너레이터는 화살표로 쓸 수 없어 뺀다.
      //    그 밖에 불가피한 곳(this 바인딩, 함수 오버로드 등)은 eslint-disable-next-line에 이유를 적는다.
      'no-restricted-syntax': [
        'error',
        {
          selector: 'FunctionDeclaration:not([generator=true])',
          message: '화살표 함수를 쓰세요. 불가피하면 eslint-disable에 이유를 적으세요.',
        },
        {
          selector:
            "FunctionExpression:not(MethodDefinition > FunctionExpression):not(Property[kind!='init'] > FunctionExpression):not([generator=true])",
          message: '화살표 함수를 쓰세요. 불가피하면 eslint-disable에 이유를 적으세요.',
        },
        {
          // React.createContext(...)처럼 네임스페이스로 부르는 경우
          selector: "MemberExpression[object.name='React'][property.name=/^(createContext|useContext)$/]",
          message: 'Context 대신 zustand 스토어를 쓰세요 (src/stores).',
        },
        {
          // 6. 타입은 import { a, type B } 대신 import type { B }로 따로 가져온다 (그룹 안에서 값 import 뒤에 정렬되게)
          selector: "ImportSpecifier[importKind='type']",
          message: "타입은 별도의 'import type { … }' 문으로 가져오세요.",
        },
      ],
      'prefer-arrow-callback': 'error',
      // 4. 상태 공유는 zustand 스토어(src/stores)로 한다. React Context는 쓰지 않는다.
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'react',
              importNames: ['createContext', 'useContext'],
              message: 'Context 대신 zustand 스토어를 쓰세요 (src/stores).',
            },
          ],
          // 6. 내부 모듈은 '@/' 별칭으로 가져온다. 상대 경로를 쓰면 import 그룹을 위치로 나눌 수 없다.
          patterns: [
            {
              regex: '^\\.\\.?/',
              message: "내부 모듈은 '@/…' 별칭으로 가져오세요 (import 그룹을 위치로 나누기 위해).",
            },
          ],
        },
      ],
      // 7. 브라우저 기본 창(alert·confirm·prompt)은 쓰지 않는다 (이슈 #10). 확인은 useConfirmStore, 안내는 useToastStore
      'no-restricted-globals': [
        'error',
        ...['alert', 'confirm', 'prompt'].map((name) => ({
          name,
          message: '브라우저 기본 창 대신 useConfirmStore(확인)나 useToastStore(안내)를 쓰세요.',
        })),
      ],
      'no-restricted-properties': [
        'error',
        ...['window', 'globalThis', 'self'].flatMap((object) =>
          ['alert', 'confirm', 'prompt'].map((property) => ({
            object,
            property,
            message: '브라우저 기본 창 대신 useConfirmStore(확인)나 useToastStore(안내)를 쓰세요.',
          })),
        ),
      ],
      // 2·3. 인터페이스 이름은 I로, 타입 별칭 이름은 T로 시작한다 (IProps, TKeySource)
      '@typescript-eslint/naming-convention': [
        'error',
        { selector: 'interface', format: ['PascalCase'], prefix: ['I'] },
        { selector: 'typeAlias', format: ['PascalCase'], prefix: ['T'] },
      ],
      // 5. 컴포넌트·커스텀 훅 본문 순서: ref → state → hooks → effect → memo → event (eslint-rules/component-order.js)
      'local/component-order': 'error',
      // 6. import 순서: react → api → component → hooks → 3rd party → local → css (npm run lint -- --fix로 정렬)
      'simple-import-sort/imports': ['error', { groups: IMPORT_GROUPS }],
      // simple-import-sort가 이름 순서를 바꾸면 쉼표 뒤 공백이 빠질 수 있어(포매터 없음) 함께 --fix로 맞춘다
      '@stylistic/comma-spacing': 'error',
    },
  },
]);
