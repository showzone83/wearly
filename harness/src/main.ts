// 시나리오 러너.
//   tsx src/main.ts --base-url http://localhost:18080 [--external] [--only 키워드[,키워드]] [--list]
// 결과를 시나리오별 PASS/FAIL/SKIP·소요 시간 표로 출력하고, 실패가 있으면 종료 코드 1.
import { CheckFailure, describeError, show } from './lib/assert';
import { config } from './lib/config';
import { raw } from './lib/http';
import type { Ctx, Scenario } from './scenario';
import { accountScenarios } from './scenarios/account';
import { aiScenarios } from './scenarios/ai';
import { adminScenarios } from './scenarios/admin';
import { appSettingScenarios } from './scenarios/appSettings';
import { authScenarios } from './scenarios/auth';
import { codeScenarios } from './scenarios/codes';
import { imageScenarios } from './scenarios/images';
import { mfaScenarios } from './scenarios/mfa';
import { planScenarios } from './scenarios/plans';
import { profileScenarios } from './scenarios/profile';
import { retireScenarios } from './scenarios/retire';
import { sessionScenarios } from './scenarios/sessions';
import { tokenScenarios } from './scenarios/tokens';
import { wardrobeScenarios } from './scenarios/wardrobe';
import { weatherScenarios } from './scenarios/weather';

const ALL: Scenario[] = [
  ...authScenarios,
  ...profileScenarios,
  ...wardrobeScenarios,
  ...codeScenarios,
  ...aiScenarios,
  ...tokenScenarios,
  ...accountScenarios,
  ...adminScenarios,
  ...appSettingScenarios,
  ...retireScenarios,
  ...sessionScenarios,
  ...mfaScenarios,
  ...imageScenarios,
  ...weatherScenarios,
  ...planScenarios,
];

type Status = 'PASS' | 'FAIL' | 'SKIP';
interface Result {
  no: number;
  scenario: Scenario;
  status: Status;
  ms: number;
  reason?: string;
  error?: unknown;
  notes: string[];
}

const useColor = process.stdout.isTTY && !process.env.NO_COLOR;
const paint = (code: string) => (s: string) => (useColor ? `\x1b[${code}m${s}\x1b[0m` : s);
const green = paint('32');
const red = paint('31');
const yellow = paint('33');
const dim = paint('2');
const bold = paint('1');

function parseArgs(argv: string[]) {
  const out = { only: [] as string[], list: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--base-url') config.origin = (argv[++i] ?? '').replace(/\/+$/, '');
    else if (a === '--external') config.external = true;
    else if (a === '--only') out.only.push(...(argv[++i] ?? '').split(',').filter(Boolean));
    else if (a === '--list') out.list = true;
    else {
      console.error(`알 수 없는 인자: ${a}`);
      process.exit(2);
    }
  }
  return out;
}

/** 한글 등 전각 문자는 터미널에서 두 칸을 차지한다 */
function width(s: string): number {
  let w = 0;
  for (const ch of s.replace(/\x1b\[[0-9;]*m/g, '')) {
    const c = ch.codePointAt(0)!;
    const wide =
      (c >= 0x1100 && c <= 0x115f) ||
      (c >= 0x2e80 && c <= 0xa4cf) ||
      (c >= 0xac00 && c <= 0xd7a3) ||
      (c >= 0xf900 && c <= 0xfaff) ||
      (c >= 0xfe30 && c <= 0xfe4f) ||
      (c >= 0xff00 && c <= 0xff60) ||
      (c >= 0xffe0 && c <= 0xffe6);
    w += wide ? 2 : 1;
  }
  return w;
}
const pad = (s: string, n: number) => s + ' '.repeat(Math.max(0, n - width(s)));
const padStart = (s: string, n: number) => ' '.repeat(Math.max(0, n - width(s))) + s;

function fmtMs(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${Math.round(ms)}ms`;
}

async function runOne(no: number, sc: Scenario, skipReason?: string): Promise<Result> {
  const notes: string[] = [];
  if (skipReason) return { no, scenario: sc, status: 'SKIP', ms: 0, reason: skipReason, notes };
  const ctx: Ctx = { external: config.external, note: (m) => notes.push(m) };
  const started = performance.now();
  try {
    await sc.run(ctx);
    return { no, scenario: sc, status: 'PASS', ms: performance.now() - started, notes };
  } catch (error) {
    return { no, scenario: sc, status: 'FAIL', ms: performance.now() - started, error, notes };
  }
}

function printTable(results: Result[]): void {
  const rows = results.map((r) => ({
    no: String(r.no),
    group: r.scenario.group,
    name: r.scenario.name,
    status: r.status,
    ms: r.status === 'SKIP' ? '-' : fmtMs(r.ms),
  }));
  const wNo = Math.max(2, ...rows.map((r) => width(r.no)));
  const wGroup = Math.max(4, ...rows.map((r) => width(r.group)));
  const wName = Math.max(8, ...rows.map((r) => width(r.name)));
  const wMs = Math.max(4, ...rows.map((r) => width(r.ms)));
  const line = `${'-'.repeat(wNo)}-+-${'-'.repeat(4)}-+-${'-'.repeat(wGroup)}-+-${'-'.repeat(wName)}-+-${'-'.repeat(wMs)}`;
  console.log('');
  console.log(bold(`${pad('#', wNo)} | 결과 | ${pad('분류', wGroup)} | ${pad('시나리오', wName)} | ${padStart('시간', wMs)}`));
  console.log(line);
  for (const r of rows) {
    const st = r.status === 'PASS' ? green('PASS') : r.status === 'FAIL' ? red('FAIL') : yellow('SKIP');
    console.log(`${padStart(r.no, wNo)} | ${st} | ${pad(r.group, wGroup)} | ${pad(r.name, wName)} | ${padStart(r.ms, wMs)}`);
  }
  console.log(line);
}

function printDetails(results: Result[]): void {
  const failures = results.filter((r) => r.status === 'FAIL');
  if (failures.length) {
    console.log('');
    console.log(bold(red('실패 상세')));
    for (const r of failures) {
      console.log('');
      console.log(red(`✗ #${r.no} [${r.scenario.group}] ${r.scenario.name}`));
      const e = r.error;
      if (e instanceof CheckFailure) {
        console.log(`  검사: ${e.message}`);
        if (e.values) {
          console.log(`  기대: ${show(e.values.expected).replace(/\n/g, '\n        ')}`);
          console.log(`  실제: ${show(e.values.actual).replace(/\n/g, '\n        ')}`);
        }
      } else {
        console.log(`  예상하지 못한 오류: ${describeError(e)}`);
        const stack = e instanceof Error ? e.stack?.split('\n').slice(1, 6).join('\n') : undefined;
        if (stack) console.log(dim(stack));
      }
    }
  }
  const skipped = results.filter((r) => r.status === 'SKIP');
  if (skipped.length) {
    console.log('');
    console.log(yellow('건너뜀'));
    for (const r of skipped) console.log(`  #${r.no} ${r.scenario.name}: ${r.reason}`);
  }
  const noted = results.filter((r) => r.notes.length);
  if (noted.length) {
    console.log('');
    console.log(dim('참고'));
    for (const r of noted) for (const n of r.notes) console.log(dim(`  #${r.no} ${n}`));
  }
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const selected = ALL.map((s, i) => ({ s, no: i + 1 })).filter(
    ({ s }) => args.only.length === 0 || args.only.some((k) => `${s.group} ${s.name}`.toLowerCase().includes(k.toLowerCase())),
  );
  if (args.list) {
    for (const { s, no } of selected) console.log(`${padStart(String(no), 2)}. [${s.group}] ${s.name}${s.needs ? dim(` (필요: ${s.needs.join(',')})`) : ''}`);
    return;
  }
  if (selected.length === 0) {
    console.error(`--only ${args.only.join(',')} 에 맞는 시나리오가 없습니다. --list로 목록을 보세요.`);
    process.exit(2);
  }

  console.log(bold(`Wearly 연동 하네스 → ${config.origin}${config.external ? ' (외부 서버)' : ''}`));
  console.log(dim(`시나리오 ${selected.length}개 실행`));

  // 서버에 닿지 않으면 나머지는 의미가 없으니 모두 건너뛴다
  let unreachable: string | undefined;
  try {
    await raw('GET', '/actuator/health');
  } catch (e) {
    unreachable = `서버에 연결할 수 없음 (${describeError(e)})`;
  }

  const results: Result[] = [];
  for (const { s, no } of selected) {
    let skip: string | undefined = unreachable;
    if (!skip && config.external && s.needs?.includes('shortTtl')) {
      skip = '--external: Access TTL이 3초라는 보장이 없어 건너뜀';
    }
    if (!skip && config.external && s.needs?.includes('admin')) {
      skip = '--external: 하네스 관리자 계정이 없어 건너뜀';
    }
    const r = await runOne(no, s, skip);
    results.push(r);
    const mark = r.status === 'PASS' ? green('.') : r.status === 'FAIL' ? red('F') : yellow('s');
    process.stdout.write(mark);
  }
  process.stdout.write('\n');

  printTable(results);
  printDetails(results);

  const pass = results.filter((r) => r.status === 'PASS').length;
  const failCount = results.filter((r) => r.status === 'FAIL').length;
  const skipCount = results.filter((r) => r.status === 'SKIP').length;
  const total = results.reduce((a, r) => a + r.ms, 0);
  console.log('');
  const summary = `통과 ${pass} · 실패 ${failCount} · 건너뜀 ${skipCount} · 총 ${fmtMs(total)}`;
  console.log(failCount ? bold(red(summary)) : bold(green(summary)));
  if (unreachable) console.log(red(unreachable));
  process.exitCode = failCount > 0 || unreachable ? 1 : 0;
}

main().catch((e) => {
  console.error(red(`하네스 자체 오류: ${describeError(e)}`));
  if (e instanceof Error && e.stack) console.error(dim(e.stack));
  process.exit(1);
});
