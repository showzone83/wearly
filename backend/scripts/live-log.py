#!/usr/bin/env python3
"""웨어리 백엔드 실시간 로그 (로컬 개발용).

DB에 새로 쌓이는 AI 분석·로그인·옷 등록·사진 저장·날씨 수집 기록과 서버 로그의 경고·오류를 한 화면에 보여 준다.
백엔드는 AI 분석 성공을 로그로 남기지 않고 tb_ai_analysis_log에만 기록하므로 DB를 1초마다 확인한다.

실행: python3 backend/scripts/live-log.py [서버 로그 파일 경로]
"""
import json
import os
import subprocess
import sys
import time

CONTAINER = os.environ.get('WEARLY_DB_CONTAINER', 'wearly-postgres')
LOG_PATH = sys.argv[1] if len(sys.argv) > 1 else None
POLL_SEC = 1.0

C = {
    'dim': '\033[2m', 'b': '\033[1m', 'r': '\033[31m', 'g': '\033[32m',
    'y': '\033[33m', 'c': '\033[36m', 'm': '\033[35m', 'x': '\033[0m',
}


def psql(sql):
    out = subprocess.run(
        ['docker', 'exec', CONTAINER, 'psql', '-U', 'wearly', '-d', 'wearly', '-tAc', sql],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip())
    text = out.stdout.strip()
    return json.loads(text) if text else None


def max_id(table, col):
    return psql(f'select coalesce(max({col}), 0) from {table}') or 0


def rows(sql):
    return psql(f'select coalesce(json_agg(t), \'[]\') from ({sql}) t') or []


def now():
    return time.strftime('%H:%M:%S')


def line(color, tag, msg):
    print(f"{C['dim']}{now()}{C['x']} {C[color]}{C['b']}{tag:<8}{C['x']} {msg}", flush=True)


def show_ai(r):
    who = r['email'] or f"user {r['user_id']}"
    head = f"{who} · {r['provider']}" + (f" ({r['model']})" if r['model'] else '')
    size = f"{(r['image_bytes'] or 0) / 1024:.0f}KB" if r['image_bytes'] else '-'
    meta = f"{r['latency_ms']}ms · 이미지 {size}"
    if r['input_tokens'] is not None:
        meta += f" · 토큰 입력 {r['input_tokens']} / 출력 {r['output_tokens']}"
    if r['status'] == 'SUCCESS':
        s = r['result'] or {}
        s = s.get('suggestion', s)
        tag_color = 'g' if r['provider'] == 'claude' else 'y'
        line(tag_color, 'AI 분석', f"{head} · {meta}")
        print(f"         → {C['b']}{s.get('name', '?')}{C['x']}  "
              f"{s.get('category')}/{s.get('subType')} · 색 {C['c']}{s.get('color')}{C['x']} · "
              f"{s.get('pattern')} · 계절 {','.join(s.get('seasons') or [])} · "
              f"스타일 {','.join(s.get('styles') or [])} · 격식 {s.get('formality')} · "
              f"확신도 {s.get('confidence')}", flush=True)
        if r['provider'] == 'mock':
            print(f"         {C['y']}※ 목업 결과입니다. 사진 내용과 무관합니다.{C['x']}", flush=True)
    else:
        line('r', 'AI 실패', f"{head} · {meta} · 오류 {r['error_code']}")


def main():
    try:
        last_ai = max_id('tb_ai_analysis_log', 'ai_analysis_log_id')
        # 7차(doc/16): Refresh 토큰은 Valkey로 옮겼다. 로그인·로그아웃·강제 로그아웃은 tb_auth_event에 남는다
        last_auth = max_id('tb_auth_event', 'auth_event_id')
        last_item = max_id('tb_wardrobe_item', 'item_id')
        # 날씨 수집 실행(doc/17)은 끝난 것만 한 줄로, 실패한 공급자 호출은 따로 보여 준다
        # 시작할 때 도는 중인 실행은 끝나면 보여 주도록, 끝난 실행까지만 건너뛴다
        last_run = psql('select coalesce(max(run_id), 0) from tb_weather_refresh_run where finished_at is not null') or 0
        last_call = max_id('tb_weather_call_log', 'call_id')
        # 사진 저장(doc/18): complete로 READY가 된 사진
        last_image_at = psql("select to_json(coalesce(max(ready_at), now())) from tb_item_image")
    except Exception as e:  # noqa: BLE001
        print(f'DB에 연결하지 못했습니다: {e}\n먼저 docker compose up -d 를 실행하세요.')
        sys.exit(1)

    print(f"{C['b']}웨어리 실시간 로그{C['x']}  {C['dim']}(Ctrl+C로 종료){C['x']}")
    print(f"{C['dim']}DB: {CONTAINER} · 서버 로그: {LOG_PATH or '없음'}{C['x']}")
    print(f"{C['dim']}앱에서 로그인하거나 사진을 분석하면 여기에 바로 나옵니다.{C['x']}\n", flush=True)

    log_file = None
    if LOG_PATH and os.path.exists(LOG_PATH):
        log_file = open(LOG_PATH, encoding='utf-8', errors='replace')
        log_file.seek(0, os.SEEK_END)

    while True:
        try:
            for r in rows(
                'select l.ai_analysis_log_id id, l.user_id, u.email, l.provider, l.model, l.status, '
                'l.latency_ms, l.input_tokens, l.output_tokens, l.image_bytes, l.result, l.error_code '
                f'from tb_ai_analysis_log l left join tb_user u using (user_id) '
                f'where l.ai_analysis_log_id > {last_ai} order by 1'
            ):
                last_ai = r['id']
                show_ai(r)

            for r in rows(
                'select e.auth_event_id id, u.email, e.type, e.client, e.ip from tb_auth_event e '
                f'left join tb_user u using (user_id) where e.auth_event_id > {last_auth} order by 1'
            ):
                last_auth = r['id']
                label = {
                    'LOGIN': '로그인(가입 포함)', 'LOGOUT': '로그아웃',
                    'REFRESH_REUSED': 'Refresh 재사용 감지 → 세션 끊김',
                    'FORCED_LOGOUT': '강제 로그아웃(관리자·정지)',
                }.get(r['type'], r['type'])
                color = 'r' if r['type'] in ('REFRESH_REUSED', 'FORCED_LOGOUT') else 'm'
                line(color, '로그인', f"{r['email']} · {label} · {r['client'] or '-'} · {r['ip'] or '-'}")

            for r in rows(
                'select i.item_id id, u.email, i.name, i.category, i.color from tb_wardrobe_item i '
                f'left join tb_user u using (user_id) where i.item_id > {last_item} order by 1'
            ):
                last_item = r['id']
                line('c', '옷 등록', f"{r['email']} · {r['name']} ({r['category']} · {r['color']})")

            for r in rows(
                'select i.ready_at, u.email, i.width, i.height, i.size_bytes, i.item_id from tb_item_image i '
                f"left join tb_user u using (user_id) where i.ready_at > '{last_image_at}' order by i.ready_at"
            ):
                last_image_at = r['ready_at']
                target = f"옷 {r['item_id']}" if r['item_id'] else '아직 옷에 안 붙음'
                line('c', '사진', f"{r['email']} · {r['width']}×{r['height']} · "
                                  f"{(r['size_bytes'] or 0) / 1024:.0f}KB · {target}")

            for r in rows(
                'select run_id id, trigger_type, region_code, status, skip_reason, success_count, '
                'fail_count, fallback_count, finished_at, '
                'round(extract(epoch from finished_at - started_at)::numeric, 1) sec '
                f'from tb_weather_refresh_run where run_id > {last_run} order by 1'
            ):
                if r['finished_at'] is None:
                    break  # 아직 도는 중이면 끝난 뒤에 보여 준다
                last_run = r['id']
                where = f" · 지역 {r['region_code']}" if r['region_code'] else ''
                skip = f" · 건너뜀 {r['skip_reason']}" if r['skip_reason'] else ''
                color = 'g' if r['status'] == 'SUCCESS' else 'y' if r['status'] == 'SKIPPED' else 'r'
                line(color, '날씨', f"수집 {r['trigger_type']}{where} · {r['status']} · 성공 {r['success_count']} · "
                                    f"실패 {r['fail_count']} · 예비 공급자 {r['fallback_count']} · {r['sec']}초{skip}")

            for r in rows(
                'select call_id id, region_code, provider_code, http_status, error_code, success '
                f'from tb_weather_call_log where call_id > {last_call} order by 1'
            ):
                last_call = r['id']
                if not r['success']:
                    line('r', '날씨 실패', f"{r['provider_code']} · {r['region_code']} · "
                                          f"HTTP {r['http_status'] or '-'} · {r['error_code'] or '-'}")
        except Exception as e:  # noqa: BLE001
            line('r', 'DB 오류', str(e)[:200])

        if log_file:
            # 서버를 다시 띄우며 로그 파일을 비웠으면 처음부터 다시 읽는다
            if os.path.getsize(LOG_PATH) < log_file.tell():
                log_file.seek(0)
            for raw in log_file.readlines():
                # 요청 접근 로그 (서버를 server.tomcat.accesslog 설정으로 띄운 경우)
                if ' ACCESS ' in raw:
                    if '/actuator/health' in raw:
                        continue
                    parts = raw.strip().split(' ACCESS ', 1)[1]
                    status = parts.split('" ', 1)[1].split(' ', 1)[0] if '" ' in parts else ''
                    color = 'r' if status.startswith('5') else 'y' if status.startswith('4') else 'dim'
                    line(color, 'HTTP', parts)
                    continue
                if any(k in raw for k in (' WARN ', ' ERROR ', 'AI ', 'Exception')):
                    color = 'r' if ' ERROR ' in raw or 'Exception' in raw else 'y'
                    msg = raw.split(' : ', 1)[-1].strip()
                    line(color, '서버', msg[:300])

        time.sleep(POLL_SEC)


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\n종료')
