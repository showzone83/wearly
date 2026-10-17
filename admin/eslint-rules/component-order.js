// local/component-order — 컴포넌트·커스텀 훅 본문의 순서를 검사한다 (README "코드 규칙" 5).
//
// 대상: 이름이 대문자로 시작하거나(컴포넌트) use로 시작하는(커스텀 훅) 화살표 함수 중 본문이 블록인 것.
//   const Page = () => { ... }, const useThing = () => { ... }, const Item = memo(() => { ... })
// 본문의 최상위 문장을 다음 분류로 나누고, 앞 분류가 뒤 분류보다 뒤에 나오면 보고한다.
//   ref → state → hooks → effect → memo → event
// 분류할 수 없는 문장(if, 일반 함수 호출, return 등)은 순서 검사에서 빼고 건너뛴다.
// 바로 위 줄의 주석에 "순서 예외:"가 있는 문장도 건너뛴다(TDZ 때문에 앞으로 올린 선언).
// 순서는 주석·TDZ 때문에 옮기는 것이 안전하지 않아 자동 수정하지 않는다.
// 바로 붙은 두 문장의 분류가 다르면 사이에 빈 줄이 있어야 한다(이것만 --fix로 빈 줄을 넣는다).

export const CATEGORIES = ['ref', 'state', 'hooks', 'effect', 'memo', 'event'];

const LABELS = {
  ref: 'ref(useRef)',
  state: 'state(useState·useReducer)',
  hooks: 'hooks(그 밖의 훅)',
  effect: 'effect(useEffect·useLayoutEffect)',
  memo: 'memo(useMemo·파생 값)',
  event: 'event(핸들러·함수·useCallback)',
};

const EXCEPTION_MARK = '순서 예외:';

const HOOK_CATEGORY = {
  useRef: 'ref',
  useState: 'state',
  useReducer: 'state',
  useEffect: 'effect',
  useLayoutEffect: 'effect',
  useInsertionEffect: 'effect',
  useMemo: 'memo',
  useCallback: 'event',
};

const isHookName = (name) => name === 'use' || /^use[A-Z0-9]/.test(name);
const isTargetName = (name) => /^[A-Z]/.test(name) || isHookName(name);

// foo(), React.useState(), a.b.useThing() 의 함수 이름
const calleeName = (callee) => {
  if (callee.type === 'Identifier') return callee.name;
  if (callee.type === 'MemberExpression' && !callee.computed && callee.property.type === 'Identifier')
    return callee.property.name;
  return null;
};

const hookCategory = (name) => HOOK_CATEGORY[name] ?? (isHookName(name) ? 'hooks' : null);

// TS 래퍼(as, satisfies, !, <T>x)와 괄호를 벗긴다
const unwrap = (node) => {
  let current = node;
  while (
    current &&
    (current.type === 'TSAsExpression' ||
      current.type === 'TSSatisfiesExpression' ||
      current.type === 'TSNonNullExpression' ||
      current.type === 'TSTypeAssertion' ||
      current.type === 'ChainExpression')
  ) {
    current = current.expression;
  }
  return current;
};

const isFunction = (node) => node.type === 'ArrowFunctionExpression' || node.type === 'FunctionExpression';

// 식 안에서(중첩 함수 안은 빼고) 처음 나오는 훅 호출의 분류. 없으면 null.
const findHookCategory = (node, visitorKeys) => {
  if (!node || typeof node.type !== 'string' || isFunction(node)) return null;
  if (node.type === 'CallExpression') {
    const name = calleeName(node.callee);
    const category = name ? hookCategory(name) : null;
    if (category) return category;
  }
  for (const key of visitorKeys[node.type] ?? []) {
    const child = node[key];
    const children = Array.isArray(child) ? child : [child];
    for (const item of children) {
      const found = findHookCategory(item, visitorKeys);
      if (found) return found;
    }
  }
  return null;
};

// 식 하나(변수 초깃값 또는 식 문장)의 분류
const classifyExpression = (expression, visitorKeys, isDeclaration) => {
  const node = unwrap(expression);
  if (!node) return isDeclaration ? 'memo' : null;
  if (isFunction(node)) return 'event';
  const hook = findHookCategory(node, visitorKeys);
  if (hook) return hook;
  // 훅이 없는 선언은 렌더마다 계산하는 파생 값, 훅이 없는 식 문장은 분류하지 않는다
  return isDeclaration ? 'memo' : null;
};

const classifyStatement = (statement, visitorKeys) => {
  if (statement.type === 'VariableDeclaration') {
    // 선언자가 여러 개면 가장 뒤 분류를 쓴다
    let result = null;
    for (const declarator of statement.declarations) {
      const category = classifyExpression(declarator.init, visitorKeys, true);
      if (result == null || CATEGORIES.indexOf(category) > CATEGORIES.indexOf(result)) result = category;
    }
    return result;
  }
  if (statement.type === 'ExpressionStatement') {
    return classifyExpression(statement.expression, visitorKeys, false);
  }
  return null;
};

// 문장 바로 위 줄(빈 줄 없이 붙은 주석 묶음)에 "순서 예외:" 주석이 있는가
const hasExceptionComment = (statement, sourceCode) => {
  const comments = sourceCode.getCommentsBefore(statement);
  let line = statement.loc.start.line;
  for (let i = comments.length - 1; i >= 0; i -= 1) {
    const comment = comments[i];
    if (comment.loc.end.line < line - 1) break;
    if (comment.value.trim().startsWith(EXCEPTION_MARK)) return true;
    line = comment.loc.start.line;
  }
  return false;
};

// 문장 바로 위에 빈 줄 없이 붙은 주석 묶음의 첫 줄 (주석이 없으면 문장의 첫 줄)
const leadingStart = (statement, sourceCode) => {
  const comments = sourceCode.getCommentsBefore(statement);
  let line = statement.loc.start.line;
  for (let i = comments.length - 1; i >= 0; i -= 1) {
    if (comments[i].loc.end.line < line - 1) break;
    line = comments[i].loc.start.line;
  }
  return line;
};

// 이 화살표 함수의 이름 (const Name = () => {}, const Name = memo(() => {}))
const targetName = (fn) => {
  let node = fn.parent;
  if (node?.type === 'CallExpression' && node.arguments[0] === fn) node = node.parent;
  if (node?.type === 'VariableDeclarator' && node.id.type === 'Identifier') return node.id.name;
  return null;
};

/** @type {import('eslint').Rule.RuleModule} */
export const componentOrder = {
  meta: {
    type: 'suggestion',
    docs: {
      description: '컴포넌트·커스텀 훅 본문을 ref → state → hooks → effect → memo → event 순서로 쓴다',
    },
    schema: [],
    messages: {
      order:
        '{{name}}: {{current}}는 {{previous}}보다 앞에 둡니다 (순서: ref → state → hooks → effect → memo → event). TDZ 때문에 앞에 둬야 하면 바로 위에 "// 순서 예외: <이유>" 주석을 다세요.',
      blankLine: '{{name}}: {{previous}}와 {{current}} 사이에 빈 줄을 하나 두세요.',
    },
    fixable: 'whitespace',
  },
  create: (context) => {
    const sourceCode = context.sourceCode;
    const visitorKeys = sourceCode.visitorKeys;

    return {
      ArrowFunctionExpression: (fn) => {
        if (fn.body.type !== 'BlockStatement') return;
        const name = targetName(fn);
        if (!name || !isTargetName(name)) return;

        let maxIndex = -1;
        let prev = null; // 바로 앞 문장 { statement, category }. 분류 없음·예외면 category는 null
        for (const statement of fn.body.body) {
          const raw = classifyStatement(statement, visitorKeys);
          const category = raw != null && !hasExceptionComment(statement, sourceCode) ? raw : null;
          const before = prev;
          prev = { statement, category };
          if (category == null) continue;

          // 분류가 바뀌는 곳에는 빈 줄 (앞 문장 끝 줄과 이 문장·붙은 주석의 첫 줄 사이)
          if (before?.category != null && before.category !== category) {
            if (leadingStart(statement, sourceCode) - before.statement.loc.end.line < 2) {
              const endLine = before.statement.loc.end.line;
              context.report({
                node: statement,
                messageId: 'blankLine',
                data: { name, previous: LABELS[before.category], current: LABELS[category] },
                fix: (fixer) => {
                  const lineStart = sourceCode.getIndexFromLoc({ line: endLine + 1, column: 0 });
                  return fixer.insertTextBeforeRange([lineStart, lineStart], '\n');
                },
              });
            }
          }

          const index = CATEGORIES.indexOf(category);
          if (index < maxIndex) {
            context.report({
              node: statement,
              messageId: 'order',
              data: { name, current: LABELS[category], previous: LABELS[CATEGORIES[maxIndex]] },
            });
          } else {
            maxIndex = index;
          }
        }
      },
    };
  },
};
