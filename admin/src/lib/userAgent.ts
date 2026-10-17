// User-Agent 한 줄을 "iOS · Expo Go"처럼 짧게 요약한다 (doc/16 5장, 로그인 사용자 화면).
// 순수 함수: import가 없어 Node에서 그대로 테스트한다 (tests/userAgent.test.mjs). 정확한 판별이 아니라 관리자가 기기를 알아보는 정도면 된다.

export interface IUaSummary {
  /** iOS · Android · Mac · Windows · Linux · ChromeOS, 모르면 null */
  os: string | null;
  /** Expo Go · Wearly 앱 · Chrome · Safari · Edge · Firefox … 모르면 null */
  agent: string | null;
  /** 표에 보여 줄 한 줄. User-Agent가 없으면 '—' */
  label: string;
}

/** 앞에 있는 것부터 검사한다 (Edge·Whale·삼성 인터넷의 UA에도 Chrome·Safari가 들어 있다) */
const AGENTS: [RegExp, string][] = [
  [/\b(Exponent|ExpoGo|Expo)\//i, 'Expo Go'],
  [/\bWearly\//i, 'Wearly 앱'],
  [/\bokhttp\//i, '앱(okhttp)'],
  [/\bEdg(e|A|iOS)?\//, 'Edge'],
  [/\bWhale\//, 'Whale'],
  [/\bSamsungBrowser\//, '삼성 인터넷'],
  [/\b(OPR|Opera)\//, 'Opera'],
  [/\b(CriOS|Chrome)\//, 'Chrome'],
  [/\b(FxiOS|Firefox)\//, 'Firefox'],
  [/\bVersion\/[\d.]+.*\bSafari\//, 'Safari'],
  [/\bCFNetwork\//, '앱(CFNetwork)'],
  [/\bcurl\//i, 'curl'],
  [/\bPostmanRuntime\//, 'Postman'],
  [/\b(node|undici|node-fetch)\b/i, 'Node'],
  [/\bJava\/|\bApache-HttpClient\//, 'Java'],
];

const OSES: [RegExp, string][] = [
  [/\b(iPhone|iPad|iPod)\b|\biOS\b/, 'iOS'],
  [/\bAndroid\b|\bokhttp\//i, 'Android'],
  [/\bCrOS\b/, 'ChromeOS'],
  [/\bWindows\b/, 'Windows'],
  [/\bMac OS X\b|\bMacintosh\b/, 'Mac'],
  [/\bLinux\b/, 'Linux'],
];

/** React Native(iOS) 기본 UA `앱이름/빌드 CFNetwork/… Darwin/…`: Mac 앱도 같은 모양이지만 이 서비스에서는 iOS 앱으로 본다 */
const IOS_NATIVE = /\bCFNetwork\/.*\bDarwin\//;

const MAX_RAW = 40;

export const summarizeUserAgent = (ua: string | null | undefined): IUaSummary => {
  const raw = (ua ?? '').trim();
  if (!raw) return { os: null, agent: null, label: '—' };

  const agent = AGENTS.find(([re]) => re.test(raw))?.[1] ?? null;
  const os = OSES.find(([re]) => re.test(raw))?.[1] ?? (IOS_NATIVE.test(raw) ? 'iOS' : null);

  if (!os && !agent) return { os, agent, label: raw.length > MAX_RAW ? `${raw.slice(0, MAX_RAW)}…` : raw };
  return { os, agent, label: [os, agent].filter(Boolean).join(' · ') };
};
