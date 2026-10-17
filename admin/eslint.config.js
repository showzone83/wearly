import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import { defineConfig, globalIgnores } from 'eslint/config';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat.recommended, reactRefresh.configs.vite],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
  },
  // 코드 규칙 (README "코드 규칙" 절)
  {
    files: ['**/*.{ts,tsx}'],
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
          selector: "FunctionExpression:not(MethodDefinition > FunctionExpression):not(Property[kind!='init'] > FunctionExpression):not([generator=true])",
          message: '화살표 함수를 쓰세요. 불가피하면 eslint-disable에 이유를 적으세요.',
        },
        {
          // React.createContext(...)처럼 네임스페이스로 부르는 경우
          selector: "MemberExpression[object.name='React'][property.name=/^(createContext|useContext)$/]",
          message: 'Context 대신 zustand 스토어를 쓰세요 (src/stores).',
        },
      ],
      'prefer-arrow-callback': 'error',
      // 4. 상태 공유는 zustand 스토어(src/stores)로 한다. React Context는 쓰지 않는다.
      'no-restricted-imports': [
        'error',
        {
          paths: [{ name: 'react', importNames: ['createContext', 'useContext'], message: 'Context 대신 zustand 스토어를 쓰세요 (src/stores).' }],
        },
      ],
      // 2·3. 인터페이스 이름은 I로, 타입 별칭 이름은 T로 시작한다 (IProps, TKeySource)
      '@typescript-eslint/naming-convention': [
        'error',
        { selector: 'interface', format: ['PascalCase'], prefix: ['I'] },
        { selector: 'typeAlias', format: ['PascalCase'], prefix: ['T'] },
      ],
    },
  },
]);
