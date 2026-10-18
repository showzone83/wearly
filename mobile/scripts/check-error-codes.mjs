// 오류 코드 카탈로그(backend/src/main/resources/error-codes.json)와 앱 문구 표(src/lib/errorText.ts)가 맞는지 확인한다.
// - 카탈로그의 서버 오류 코드·필드 오류 코드가 표(SERVER_TEXT·FIELD_TEXT)에 빠짐없이 있는지
// - 표에 카탈로그에 없는 코드가 남아 있지 않은지 (서버에서 없앤 코드)
// - 앱이 만드는 코드(APP_TEXT)가 서버 코드와 겹치지 않고, 정해진 목록과 같은지
// 실행: npm run check:errors (카탈로그 위치는 인자나 ERROR_CODES 환경 변수로 바꿀 수 있다)
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const here = dirname(fileURLToPath(import.meta.url));
const catalogPath = resolve(process.argv[2] ?? process.env.ERROR_CODES ?? resolve(here, '../../backend/src/main/resources/error-codes.json'));
const tablePath = resolve(here, '../src/lib/errorText.ts');

/** 앱이 만드는 코드 (카탈로그에 없음) */
const APP_CODES = ['UPLOAD_FAILED', 'NETWORK', 'HTTP_ERROR', 'RETIRE_UNSUPPORTED', 'IMAGES_UNSUPPORTED'];

const catalog = JSON.parse(readFileSync(catalogPath, 'utf8'));
const serverCodes = catalog.errorCodes.map((c) => c.code);
const fieldCodes = catalog.fieldErrorCodes.map((c) => c.code);

/** errorText.ts에서 `export const NAME = { ... }` 객체의 키 목록을 읽는다 */
const source = ts.createSourceFile(tablePath, readFileSync(tablePath, 'utf8'), ts.ScriptTarget.Latest, true);
const tableKeys = (name) => {
  let keys = null;
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && node.name.getText(source) === name && node.initializer && ts.isObjectLiteralExpression(node.initializer)) {
      keys = node.initializer.properties.map((p) => {
        if (!p.name) throw new Error(`${name}: 이름 없는 항목 (펼치기 등은 쓰지 않는다)`);
        return ts.isStringLiteral(p.name) ? p.name.text : p.name.getText(source);
      });
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  if (!keys) throw new Error(`${tablePath}에서 ${name} 객체를 찾지 못했어요`);
  return keys;
};

const problems = [];
const compare = (label, expected, actual) => {
  const a = new Set(actual);
  const e = new Set(expected);
  const missing = expected.filter((c) => !a.has(c));
  const extra = actual.filter((c) => !e.has(c));
  const dup = actual.filter((c, i) => actual.indexOf(c) !== i);
  if (missing.length) problems.push(`${label}: 표에 없는 코드 ${missing.join(', ')}`);
  if (extra.length) problems.push(`${label}: 카탈로그에 없는 코드 ${extra.join(', ')}`);
  if (dup.length) problems.push(`${label}: 중복 ${dup.join(', ')}`);
};

const serverTable = tableKeys('SERVER_TEXT');
const fieldTable = tableKeys('FIELD_TEXT');
const appTable = tableKeys('APP_TEXT');
compare('서버 오류 코드(SERVER_TEXT)', serverCodes, serverTable);
compare('필드 오류 코드(FIELD_TEXT)', fieldCodes, fieldTable);
compare('앱 오류 코드(APP_TEXT)', APP_CODES, appTable);
const clash = appTable.filter((c) => serverCodes.includes(c));
if (clash.length) problems.push(`앱 오류 코드가 서버 코드와 겹침: ${clash.join(', ')}`);

if (problems.length) {
  console.error(`오류 코드 표가 카탈로그와 맞지 않아요 (${catalogPath})`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(
  `오류 코드 표 OK: 서버 ${serverCodes.length}개 · 필드 ${fieldCodes.length}개 · 앱 ${appTable.length}개 (${catalogPath.replace(`${resolve(here, '../..')}/`, '')})`,
);
