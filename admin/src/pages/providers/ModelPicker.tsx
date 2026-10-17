// 공급자 편집의 모델 칸 (doc/07 4장 모델 목록). 검색할 수 있는 드롭다운 + 맨 아래 "직접 입력…".
// - 저장한 공급자(종류도 그대로)면 GET /admin/ai/providers/{id}/models: 키가 있으면 제공사 목록(LIVE), 없거나 실패하면 내장 목록(CATALOG).
// - 추가 중이거나 종류를 바꾼 중이면 /admin/ai/types의 내장 목록 (그 키로 다른 제공사를 부르지 않게).
// - 서버가 모델 목록 API를 모르면(404) 예전처럼 텍스트 입력만.
// 목록에 없는 ID도 저장할 수 있다 (새 모델·프리뷰). 서버 검증은 문자열 형식만 본다.
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';

import { api, errorMessage } from '@/api';
import type { IProviderModel } from '@/api';

import { Badge } from '@/components/ui';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { fmtRelative } from '@/lib/format';
import {
  filterModels,
  findModel,
  initialMode,
  isUnlisted,
  moveIndex,
  sourceText,
  UNSAVED_SOURCE_TEXT,
} from '@/lib/modelPicker';
import { isUnsupportedApi, qk, useProviderModels } from '@/lib/queries';
import type { TPickerMode } from '@/lib/modelPicker';

interface IProps {
  value: string;
  onChange: (value: string) => void;
  /** 저장한 공급자 ID (수정 중). 추가 중이면 null */
  providerId: string | null;
  /** 저장한 종류 (수정 중). 폼에서 종류를 바꾸면 저장 전까지 내장 목록을 쓴다 */
  savedType: string | null;
  /** 폼에서 고른 종류 */
  type: string;
  /** 그 종류의 내장 목록 (/admin/ai/types). 예전 서버라 없으면 undefined */
  catalog: IProviderModel[] | undefined;
  placeholder?: string;
}

const ModelLabel = ({ model }: { model: IProviderModel }) => {
  return (
    <>
      <span className="model-name">{model.displayName}</span>
      {model.displayName !== model.id ? <span className="model-id">{model.id}</span> : null}
      {model.recommended ? <Badge tone="green">추천</Badge> : null}
    </>
  );
};

export const ModelPicker = ({ value, onChange, providerId, savedType, type, catalog, placeholder }: IProps) => {
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState(-1);
  /** 사용자가 고른 모드. 목록(listKey)이 바뀌면 다시 값에 따라 정한다 */
  const [chosen, setChosen] = useState<{ key: string; mode: TPickerMode } | null>(null);

  // 순서 예외: 모델 목록 쿼리가 저장한 공급자 그대로인지(live)를 읽는다
  const live = !!providerId && savedType === type;

  const modelsQuery = useProviderModels(live ? providerId : null);
  const queryClient = useQueryClient();
  const refresh = useMutation({
    mutationFn: () => api.providers.models(providerId!, true),
    onSuccess: (data) => queryClient.setQueryData(qk.providerModels(providerId!), data),
  });

  // 열려 있을 때 바깥을 누르면 닫는다
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const unsupported = live && isUnsupportedApi(modelsQuery.error);
  const textOnly = unsupported || (!live && catalog === undefined);
  const data = live ? modelsQuery.data : undefined;
  // 저장한 공급자인데 목록을 못 받았으면(네트워크 등) 내장 목록으로
  const models: IProviderModel[] = data?.models ?? catalog ?? [];
  const listKey = live ? `live:${providerId}` : `type:${type}`;
  const mode: TPickerMode = chosen?.key === listKey ? chosen.mode : initialMode(models, value);
  const selected = findModel(models, value);
  const unlisted = isUnlisted(models, value);
  const filtered = filterModels(models, search);
  // 맨 아래 "직접 입력…"까지 센다
  const optionCount = filtered.length + 1;
  const source = !live
    ? UNSAVED_SOURCE_TEXT
    : data
      ? sourceText(data, fmtRelative(data.fetchedAt, modelsQuery.dataUpdatedAt))
      : modelsQuery.isPending
        ? '모델 목록을 불러오는 중…'
        : `내장 목록(목록을 불러오지 못했어요: ${errorMessage(modelsQuery.error)})`;
  const refreshError = refresh.isError ? errorMessage(refresh.error) : null;

  const choose = (id: string) => {
    onChange(id);
    setChosen({ key: listKey, mode: 'select' });
    setOpen(false);
  };

  /** 직접 입력 중에는 값이 목록의 ID와 같아져도 입력칸을 유지한다 (입력 중에 드롭다운으로 바뀌지 않게) */
  const typeManual = (next: string) => {
    onChange(next);
    if (chosen?.key !== listKey || chosen.mode !== 'manual') setChosen({ key: listKey, mode: 'manual' });
  };

  const toManual = () => {
    setChosen({ key: listKey, mode: 'manual' });
    setOpen(false);
  };

  const toSelect = () => {
    setChosen({ key: listKey, mode: 'select' });
    setSearch('');
    setActive(-1);
    setOpen(true);
  };

  const toggle = () => {
    setSearch('');
    setActive(selected ? filterModels(models, '').indexOf(selected) : -1);
    setOpen((o) => !o);
  };

  const pick = (index: number) => {
    if (index < 0) return;
    if (index >= filtered.length) toManual();
    else choose(filtered[index].id);
  };

  const onSearchKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const next = moveIndex(active, e.key === 'ArrowDown' ? 1 : -1, optionCount);
      setActive(next);
      listRef.current?.children[next]?.scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter') {
      // 폼 제출을 막고 강조한 항목(없으면 검색 결과 첫 항목)을 고른다
      e.preventDefault();
      pick(active >= 0 ? active : filtered.length ? 0 : -1);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      setOpen(false);
    }
  };

  if (textOnly) {
    return (
      <input
        className="mono"
        value={value}
        maxLength={100}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    );
  }

  return (
    <div className="model-picker" ref={rootRef}>
      <div className="model-source">
        <span>{source}</span>
        {live ? (
          <button
            type="button"
            className="link-btn"
            onClick={() => refresh.mutate()}
            disabled={refresh.isPending || modelsQuery.isFetching}
            title="서버 캐시를 무시하고 제공사에서 모델 목록을 다시 받습니다"
          >
            {refresh.isPending ? '받는 중…' : '새로 받기'}
          </button>
        ) : null}
      </div>
      {refreshError ? <div className="field-error">새로 받지 못했어요: {refreshError}</div> : null}

      {mode === 'manual' ? (
        <>
          <input
            className="mono"
            value={value}
            maxLength={100}
            onChange={(e) => typeManual(e.target.value)}
            placeholder={placeholder}
            aria-label="모델 ID 직접 입력"
          />
          <div className="model-manual-foot">
            {unlisted ? <Badge tone="beige">목록에 없는 모델</Badge> : null}
            <span className="muted small">
              {models.length === 0
                ? '목록이 비어 있어 모델 ID를 직접 입력합니다.'
                : unlisted
                  ? '새 모델·프리뷰면 그대로 저장할 수 있어요.'
                  : '모델 ID를 직접 입력합니다.'}
            </span>
            {models.length > 0 ? (
              <button type="button" className="link-btn" onClick={toSelect}>
                목록에서 고르기
              </button>
            ) : null}
          </div>
        </>
      ) : (
        <button type="button" className="model-trigger" aria-haspopup="listbox" aria-expanded={open} onClick={toggle}>
          {selected ? (
            <ModelLabel model={selected} />
          ) : unlisted ? (
            <>
              <span className="model-name mono">{value.trim()}</span>
              <Badge tone="beige">목록에 없는 모델</Badge>
            </>
          ) : (
            <span className="model-placeholder">모델을 고르세요</span>
          )}
          <span className="model-chev" aria-hidden>
            ▾
          </span>
        </button>
      )}

      {open && mode === 'select' ? (
        <div className="model-panel">
          <input
            // 사용자가 연 드롭다운이라 바로 검색할 수 있게 검색칸에 초점을 둔다
            autoFocus
            value={search}
            placeholder="모델 이름·ID 검색"
            onChange={(e) => {
              setSearch(e.target.value);
              setActive(-1);
            }}
            onKeyDown={onSearchKey}
            aria-label="모델 검색"
          />
          {filtered.length === 0 ? <div className="model-empty">맞는 모델이 없습니다.</div> : null}
          <ul className="model-list" role="listbox" ref={listRef}>
            {filtered.map((m, i) => (
              <li
                key={m.id}
                role="option"
                aria-selected={m.id === selected?.id}
                className={['model-option', i === active ? 'active' : '', m.id === selected?.id ? 'selected' : ''].join(
                  ' ',
                )}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(m.id)}
              >
                <ModelLabel model={m} />
              </li>
            ))}
            <li
              role="option"
              aria-selected={false}
              className={['model-option', 'model-option-manual', active === filtered.length ? 'active' : ''].join(' ')}
              onMouseEnter={() => setActive(filtered.length)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={toManual}
            >
              직접 입력…
            </li>
          </ul>
        </div>
      ) : null}
    </div>
  );
};
