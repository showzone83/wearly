// 앱 설정 > 테마: 전체 사용자의 앱 테마(냥깨비·Closeta)를 미리 보고 바꾼다 (doc/12 3·6장).
// 테마 값과 마스코트 그림은 앱 코드(mobile/src/data)에 있고, 서버는 어떤 테마를 쓸지만 저장한다.
// 백엔드가 아직 이 API를 모르면(404) 안내만 띄우고 미리보기는 그대로 보여 준다.
import { useState } from 'react';

import { MascotSvg } from '@/components/Mascot';
import { ColorSwatches, MiniAppPreview } from '@/components/ThemePreview';
import { Badge, Button, Card, ErrorBox, Loading, Modal, Notice, PageHeader } from '@/components/ui';

import { useToastStore } from '@/stores/useToastStore';

import { isThemeId, MASCOT_POSES, POSE_LABELS, THEME_IDS, themeName, THEMES } from '@/lib/appTheme';
import { fmtDateTime } from '@/lib/format';
import { isUnsupportedApi, useAppTheme, useUpdateAppTheme } from '@/lib/queries';
import type { TThemeId } from '@/lib/appTheme';

const UNSUPPORTED_TEXT = '서버가 아직 테마 전환을 지원하지 않아요';
const APPLY_TEXT = '사용자가 앱을 다시 열거나 돌아오면 반영돼요';

const ThemeCard = ({
  id,
  active,
  disabled,
  disabledReason,
  onPick,
}: {
  id: TThemeId;
  active: boolean;
  disabled: boolean;
  disabledReason?: string;
  onPick: (id: TThemeId) => void;
}) => {
  const theme = THEMES[id];
  return (
    <section className={active ? 'card theme-card theme-card-active' : 'card theme-card'} data-theme-id={id}>
      <div className="theme-card-head">
        <div>
          <h2 className="card-title">
            {theme.name}{' '}
            {active ? (
              <Badge tone="green" title="지금 전체 사용자에게 적용 중">
                ● 현재 적용 중
              </Badge>
            ) : null}
          </h2>
          <div className="muted small mono">{theme.id}</div>
        </div>
        {active ? null : (
          <Button
            variant="primary"
            size="sm"
            disabled={disabled}
            title={disabled ? disabledReason : undefined}
            onClick={() => onPick(id)}
          >
            이 테마로 바꾸기
          </Button>
        )}
      </div>
      <p className="theme-desc">{theme.description}</p>
      <div className="theme-body">
        <MiniAppPreview theme={theme} />
        <div className="theme-colors">
          <div className="theme-sub">색 견본</div>
          <ColorSwatches theme={theme} />
        </div>
      </div>
      <div className="theme-sub">마스코트 · 자세 6개</div>
      <ul className="pose-grid" style={{ background: theme.colors.bg }}>
        {MASCOT_POSES.map((pose) => (
          <li key={pose} className="pose">
            <MascotSvg mascot={theme.mascot} pose={pose} size={76} title={`${theme.name} ${POSE_LABELS[pose]}`} />
            <span className="pose-label">{POSE_LABELS[pose]}</span>
            <span className="pose-key mono">{pose}</span>
          </li>
        ))}
      </ul>
    </section>
  );
};

export const AppThemePage = () => {
  const [target, setTarget] = useState<TThemeId | null>(null);

  const { data, error, isLoading, isFetching, refetch } = useAppTheme();
  const update = useUpdateAppTheme();
  const toast = useToastStore((s) => s.show);

  const unsupported = isUnsupportedApi(error);
  const current = data?.themeId || null;
  const unknownCurrent = !!current && !isThemeId(current);
  const pickDisabled = unsupported || isLoading || update.isPending;

  const openConfirm = (id: TThemeId) => {
    update.reset();
    setTarget(id);
  };
  const closeConfirm = () => {
    if (!update.isPending) setTarget(null);
  };
  const confirm = () => {
    if (!target) return;
    const name = THEMES[target].name;
    update.mutate(target, {
      onSuccess: () => {
        setTarget(null);
        toast(`앱 테마를 ${name} 테마로 바꿨어요. ${APPLY_TEXT}.`);
      },
      onError: (e) => {
        // 조회는 됐는데 변경만 404인 경우 (서버 배포 중 등): 대화상자를 닫고 알린다. 그 밖의 오류는 대화상자 안에 보인다
        if (!isUnsupportedApi(e)) return;
        setTarget(null);
        toast(`${UNSUPPORTED_TEXT}.`, 'error');
      },
    });
  };

  return (
    <>
      <PageHeader
        title="앱 테마"
        desc={`앱 설정 · 전체 사용자의 앱 화면 테마를 고릅니다. 바꾸면 ${APPLY_TEXT}.`}
        actions={
          <Button onClick={() => void refetch()} disabled={isFetching}>
            새로 고침
          </Button>
        }
      />
      {unsupported ? (
        <Notice tone="warn">
          <div>
            <strong>{UNSUPPORTED_TEXT}.</strong>
            <div className="small">
              백엔드에 <span className="mono">GET·PUT /api/v1/admin/app/theme</span>이 아직 없어요(404). 아래 미리보기는
              볼 수 있고, 백엔드가 생기면 이 화면에서 바로 바꿀 수 있어요.
            </div>
          </div>
        </Notice>
      ) : (
        <ErrorBox error={error} onRetry={() => void refetch()} />
      )}
      {isLoading ? <Loading /> : null}
      {data ? (
        <Card className={isFetching ? 'theme-status is-fetching' : 'theme-status'}>
          <div className="theme-status-row">
            <div>
              <div className="field-label">현재 적용 중</div>
              <div className="theme-status-name">
                {themeName(current)} <span className="muted small mono">{current ?? ''}</span>
              </div>
            </div>
            <div>
              <div className="field-label">마지막 변경</div>
              <div>
                {data.updatedAt ? fmtDateTime(data.updatedAt) : <span className="muted">초기값 (바꾼 적 없음)</span>}
              </div>
            </div>
            <div>
              <div className="field-label">바꾼 관리자</div>
              <div>{data.updatedBy ?? <span className="muted">—</span>}</div>
            </div>
          </div>
        </Card>
      ) : null}
      {unknownCurrent ? (
        <Notice tone="warn">
          서버에 저장된 테마 '{current}'를 이 사이트가 모릅니다. 앱은 기기에 저장된 테마나 기본 테마(냥깨비)로 보여요.
          아래에서 테마를 다시 골라 주세요.
        </Notice>
      ) : null}
      <div className="theme-grid">
        {THEME_IDS.map((id) => (
          <ThemeCard
            key={id}
            id={id}
            active={!!data && current === id}
            disabled={pickDisabled}
            disabledReason={unsupported ? UNSUPPORTED_TEXT : undefined}
            onPick={openConfirm}
          />
        ))}
      </div>
      {target ? (
        <Modal
          title="앱 테마 바꾸기"
          onClose={closeConfirm}
          footer={
            <>
              <Button onClick={closeConfirm} disabled={update.isPending}>
                취소
              </Button>
              <Button variant="primary" busy={update.isPending} onClick={confirm}>
                {THEMES[target].name} 테마로 바꾸기
              </Button>
            </>
          }
        >
          <p className="mt-xs">전체 사용자의 앱 테마를 바꿔요.</p>
          <div className="theme-change">
            <span>{current ? themeName(current) : '알 수 없음'}</span>
            <span className="muted" aria-hidden>
              →
            </span>
            <strong>{THEMES[target].name}</strong>
          </div>
          <p className="muted small">
            {APPLY_TEXT}. 화면 색과 마스코트만 바뀌고 옷장·코디 같은 데이터와 기능은 그대로예요.
          </p>
          {update.error && !isUnsupportedApi(update.error) ? <ErrorBox error={update.error} /> : null}
        </Modal>
      ) : null}
    </>
  );
};
