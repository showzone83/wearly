// AI 공급자 추가·수정 패널. 수정일 때는 위에 "API 키" 영역(웹 입력, ApiKeySection)을 함께 보여 준다.
// 아래 폼은 키 값이 아니라 키 환경변수 '이름'만 다룬다.
import { useState } from 'react';
import type { FormEvent } from 'react';

import { api, isApiError } from '@/api';
import type { IAiProvider, IProviderOptionSpec, IProviderTypeSpec } from '@/api';

import { Badge, Button, Drawer, Field, Notice, Switch } from '@/components/ui';
import { ApiKeySection } from '@/pages/providers/ApiKeySection';

import { useToastStore } from '@/stores/useToastStore';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { DEFAULT_KEY_ENV, FALLBACK_TYPES } from '@/lib/providerTypes';
import { qk } from '@/lib/queries';

interface IFormState {
  providerId: string;
  type: string;
  displayName: string;
  model: string;
  apiKeyEnv: string;
  priority: string;
  enabled: boolean;
  /** 옵션 설명이 있는 키의 입력값 (문자열로 들고 있다가 저장할 때 타입을 맞춘다) */
  optionValues: Record<string, string>;
  /** 설명에 없는 나머지 옵션 (JSON) */
  extraJson: string;
}

const isNumericType = (t?: string) => {
  return t === 'number' || t === 'integer' || t === 'int' || t === 'float' || t === 'double';
};

const splitOptions = (options: Record<string, unknown> | null | undefined, specs: IProviderOptionSpec[]) => {
  const values: Record<string, string> = {};
  const extra: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(options ?? {})) {
    if (specs.some((s) => s.key === k) && (typeof v !== 'object' || v === null))
      values[k] = v === null ? '' : String(v);
    else extra[k] = v;
  }
  return { values, extraJson: Object.keys(extra).length ? JSON.stringify(extra, null, 2) : '' };
};

export const ProviderForm = ({
  original,
  types,
  nextPriority,
  onClose,
}: {
  original?: IAiProvider;
  types: IProviderTypeSpec[];
  nextPriority: number;
  onClose: () => void;
}) => {
  // 순서 예외: form 상태의 초깃값이 쓸 수 있는 공급자 종류(typeList)를 읽는다
  const typeList = types.length ? types : FALLBACK_TYPES;

  const [form, setForm] = useState<IFormState>(() => {
    const type = original?.type ?? typeList[0]?.type ?? 'CLAUDE';
    const specs = typeList.find((t) => t.type === type)?.options ?? [];
    const { values, extraJson } = splitOptions(original?.options, specs);
    return {
      providerId: original?.providerId ?? '',
      type,
      displayName: original?.displayName ?? '',
      model: original?.model ?? '',
      apiKeyEnv: original?.apiKeyEnv ?? typeList[0]?.defaultApiKeyEnv ?? DEFAULT_KEY_ENV[type] ?? '',
      priority: String(original?.priority ?? nextPriority),
      enabled: original?.enabled ?? false,
      optionValues: values,
      extraJson,
    };
  });
  const [errors, setErrors] = useState<Map<string, string>>(new Map());
  const [formError, setFormError] = useState<string | null>(null);

  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  const save = useMutation({
    mutationFn: async () => {
      const body = {
        type: form.type,
        displayName: form.displayName.trim(),
        model: form.model.trim() || null,
        apiKeyEnv: form.apiKeyEnv.trim(),
        enabled: form.enabled,
        priority: Number(form.priority),
        options: buildOptions(),
      };
      if (!original) return api.providers.create({ providerId: form.providerId, ...body });
      const patch: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(body)) {
        const before = (original as unknown as Record<string, unknown>)[k];
        if (JSON.stringify(v) !== JSON.stringify(before ?? (k === 'options' ? {} : null))) patch[k] = v;
      }
      if (Object.keys(patch).length === 0) return 'unchanged' as const;
      return api.providers.update(original.providerId, patch);
    },
    onSuccess: async (r) => {
      if (r !== 'unchanged') {
        await queryClient.invalidateQueries({ queryKey: qk.providers });
        void queryClient.invalidateQueries({ queryKey: ['audit'] });
        toast(isEdit ? `${form.displayName} 공급자를 저장했습니다.` : `${form.providerId} 공급자를 추가했습니다.`);
      }
      onClose();
    },
    onError: (e) => {
      if (isApiError(e) && e.fieldErrors.length) {
        const m = new Map<string, string>();
        e.fieldErrors.forEach((f) => m.set(f.field, f.message));
        setErrors(m);
        const known = ['providerId', 'type', 'displayName', 'model', 'apiKeyEnv', 'priority', 'enabled', 'options'];
        const rest = e.fieldErrors.filter((f) => !known.includes(f.field) && !f.field.startsWith('options.'));
        setFormError(rest.length ? rest.map((f) => `${f.field}: ${f.message}`).join(' / ') : null);
      } else if (isApiError(e) && e.status === 409) {
        setErrors(new Map([['providerId', '이미 있는 공급자 ID입니다.']]));
      } else {
        setFormError(e instanceof Error ? e.message : '저장하지 못했습니다.');
      }
    },
  });

  const isEdit = !!original;
  const spec = typeList.find((t) => t.type === form.type);
  const specs = spec?.options ?? [];

  const set = <K extends keyof IFormState>(key: K, value: IFormState[K]) => setForm((f) => ({ ...f, [key]: value }));
  const err = (f: string) => errors.get(f);

  const changeType = (type: string) => {
    const nextSpec = typeList.find((t) => t.type === type);
    setForm((f) => {
      const prevDefault = typeList.find((t) => t.type === f.type)?.defaultApiKeyEnv ?? DEFAULT_KEY_ENV[f.type];
      // 키 환경변수를 손대지 않았으면 새 종류의 기본값으로 바꾼다
      const apiKeyEnv =
        !f.apiKeyEnv || f.apiKeyEnv === prevDefault
          ? (nextSpec?.defaultApiKeyEnv ?? DEFAULT_KEY_ENV[type] ?? f.apiKeyEnv)
          : f.apiKeyEnv;
      return { ...f, type, apiKeyEnv };
    });
  };

  const buildOptions = (): Record<string, unknown> => {
    const out: Record<string, unknown> = form.extraJson.trim()
      ? (JSON.parse(form.extraJson) as Record<string, unknown>)
      : {};
    for (const s of specs) {
      const raw = (form.optionValues[s.key] ?? '').trim();
      if (raw === '') continue;
      if (isNumericType(s.type)) out[s.key] = Number(raw);
      else if (s.type === 'boolean') out[s.key] = raw === 'true';
      else out[s.key] = raw;
    }
    return out;
  };

  const validate = (): Map<string, string> => {
    const e = new Map<string, string>();
    if (!isEdit) {
      if (!/^[a-z0-9][a-z0-9_-]{0,29}$/.test(form.providerId))
        e.set('providerId', '영문 소문자·숫자·-·_ 1~30자로 입력하세요.');
      if (form.providerId === 'mock') e.set('providerId', "'mock'은 서버 목업 실행용 이름이라 쓸 수 없습니다.");
    }
    if (!form.displayName.trim()) e.set('displayName', '화면 이름을 입력하세요.');
    if (!form.apiKeyEnv.trim()) e.set('apiKeyEnv', '키가 들어 있는 환경변수 이름을 입력하세요.');
    else if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(form.apiKeyEnv.trim()))
      e.set('apiKeyEnv', '환경변수 이름 형식(영문·숫자·밑줄)이 아닙니다. 키 값이 아니라 이름을 넣으세요.');
    if (!/^-?\d+$/.test(form.priority.trim())) e.set('priority', '정수로 입력하세요.');
    if (form.enabled && !form.model.trim()) e.set('model', '사용하려면 모델 ID가 필요합니다.');
    for (const s of specs) {
      const raw = (form.optionValues[s.key] ?? '').trim();
      if (raw && isNumericType(s.type) && Number.isNaN(Number(raw))) e.set(`options.${s.key}`, '숫자로 입력하세요.');
    }
    if (form.extraJson.trim()) {
      try {
        const p: unknown = JSON.parse(form.extraJson);
        if (typeof p !== 'object' || p === null || Array.isArray(p)) e.set('options', 'JSON 객체여야 합니다.');
      } catch {
        e.set('options', 'JSON 형식이 아닙니다.');
      }
    }
    return e;
  };

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const v = validate();
    setErrors(v);
    setFormError(null);
    if (v.size === 0) save.mutate();
  };

  return (
    <Drawer
      title={isEdit ? `AI 공급자 수정 · ${original!.providerId}` : 'AI 공급자 추가'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" type="submit" form="provider-form" busy={save.isPending}>
            {isEdit ? '저장' : '추가'}
          </Button>
        </>
      }
    >
      {isEdit ? <ApiKeySection providerId={original!.providerId} fallback={original!} /> : null}
      <form id="provider-form" className="form" onSubmit={submit} noValidate>
        {formError ? <Notice tone="error">{formError}</Notice> : null}
        <div className="grid-2">
          <Field
            label="공급자 ID"
            required
            error={err('providerId')}
            hint={isEdit ? 'ID는 바꿀 수 없습니다.' : '예: openai, gemini-flash'}
          >
            <input
              className="mono"
              value={form.providerId}
              disabled={isEdit}
              maxLength={30}
              onChange={(e) => set('providerId', e.target.value.toLowerCase())}
            />
          </Field>
          <Field label="종류" required error={err('type')}>
            <select value={form.type} onChange={(e) => changeType(e.target.value)}>
              {typeList.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label ? `${t.label} (${t.type})` : t.type}
                </option>
              ))}
            </select>
          </Field>
        </div>
        {spec?.description ? <p className="muted small">{spec.description}</p> : null}
        <Field label="화면 이름" required error={err('displayName')}>
          <input
            value={form.displayName}
            maxLength={50}
            onChange={(e) => set('displayName', e.target.value)}
            placeholder="예: OpenAI GPT"
          />
        </Field>
        <Field label="모델 ID" error={err('model')} hint="각 사 문서의 모델 ID를 그대로 입력합니다. 사용하려면 필수.">
          <input
            className="mono"
            value={form.model}
            maxLength={100}
            onChange={(e) => set('model', e.target.value)}
            placeholder={form.type === 'CLAUDE' ? 'claude-opus-5-5' : ''}
          />
        </Field>
        <Field
          label="키 환경변수 이름"
          required
          error={err('apiKeyEnv')}
          hint={
            isEdit
              ? "키 값이 아니라 서버 환경변수의 '이름'입니다. 웹에서 넣은 키가 없을 때 이 환경변수의 키를 씁니다. 키 값은 위 'API 키'에서 넣으세요."
              : "키 값이 아니라 서버 환경변수의 '이름'입니다. 키 값은 공급자를 추가한 뒤 수정 화면의 'API 키'에서 넣을 수 있어요."
          }
        >
          <div className="row gap-sm">
            <input
              className="mono grow"
              value={form.apiKeyEnv}
              maxLength={100}
              onChange={(e) => set('apiKeyEnv', e.target.value)}
            />
            {isEdit ? (
              original!.apiKeyEnv !== form.apiKeyEnv ? (
                <Badge tone="gray">저장 후 확인</Badge>
              ) : original!.keySource === 'ENV' ? (
                <Badge tone="green">이 변수 사용 중</Badge>
              ) : original!.keySource === 'DB' ? (
                <Badge tone="gray" title="웹에서 넣은 키를 먼저 씁니다">
                  웹 키 사용 중
                </Badge>
              ) : (
                <Badge tone="red">값 없음</Badge>
              )
            ) : null}
          </div>
        </Field>
        <div className="grid-2">
          <Field label="우선순위" required error={err('priority')} hint="작을수록 먼저 씁니다.">
            <input inputMode="numeric" value={form.priority} onChange={(e) => set('priority', e.target.value)} />
          </Field>
          <Field
            label="사용"
            error={err('enabled')}
            hint={!form.model.trim() ? '모델 ID를 넣어야 켤 수 있습니다.' : undefined}
          >
            <Switch
              checked={form.enabled}
              onChange={(v) => set('enabled', v)}
              disabled={!form.model.trim() && !form.enabled}
              label={form.enabled ? '사용' : '사용 안 함'}
            />
          </Field>
        </div>

        <h3 className="section-title">옵션</h3>
        {specs.length === 0 ? (
          <p className="muted small">이 종류에 설명된 옵션이 없습니다. 필요하면 아래 JSON에 넣으세요.</p>
        ) : null}
        <div className="grid-2">
          {specs.map((s) => (
            <Field
              key={s.key}
              label={<span className="mono">{s.key}</span>}
              error={err(`options.${s.key}`)}
              hint={[s.description, s.default !== undefined ? `기본 ${String(s.default)}` : '']
                .filter(Boolean)
                .join(' · ')}
            >
              {s.values?.length || s.type === 'boolean' ? (
                <select
                  value={form.optionValues[s.key] ?? ''}
                  onChange={(e) => set('optionValues', { ...form.optionValues, [s.key]: e.target.value })}
                >
                  <option value="">(기본값)</option>
                  {(s.values ?? ['true', 'false']).map((v) => (
                    <option key={v} value={v}>
                      {v}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  className="mono"
                  inputMode={isNumericType(s.type) ? 'decimal' : undefined}
                  value={form.optionValues[s.key] ?? ''}
                  placeholder="(기본값)"
                  onChange={(e) => set('optionValues', { ...form.optionValues, [s.key]: e.target.value })}
                />
              )}
            </Field>
          ))}
        </div>
        <Field
          label="기타 옵션 (JSON)"
          error={err('options')}
          hint="위에 없는 옵션만. 위 입력칸과 겹치면 위 값이 이깁니다."
        >
          <textarea
            className="mono"
            rows={3}
            value={form.extraJson}
            onChange={(e) => set('extraJson', e.target.value)}
            placeholder="{}"
          />
        </Field>
      </form>
    </Drawer>
  );
};
