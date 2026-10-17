// 시나리오 정의 형식.

/** 하네스가 직접 띄운 백엔드에서만 확인할 수 있는 조건 */
export type Need =
  /** WEARLY_ACCESS_TTL=PT3S (만료·자동 갱신) */
  | 'shortTtl'
  /** run.sh가 WEARLY_ADMIN_EMAIL·PASSWORD로 만든 관리자 계정 */
  | 'admin';

export interface Ctx {
  /** --external로 실행 중인지 */
  external: boolean;
  /** 결과 표 아래에 남길 참고 메모 (계약상 허용 범위 안에서 서버가 고른 값 등) */
  note(msg: string): void;
}

export interface Scenario {
  group: string;
  name: string;
  needs?: Need[];
  run(ctx: Ctx): Promise<void>;
}
