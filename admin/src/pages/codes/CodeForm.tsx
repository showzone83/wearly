// 코드 추가·수정 패널. 그룹별 속성(attrs) 폼은 doc/06 3장, 검증 규칙은 doc/07 4장을 따른다.
import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';

import { api, errorMessage, isApiError } from '@/api';
import type { IAdminCode, IAdminCodeGroup, ICodeAttrs, TSlot } from '@/api';

import { ColorChip, GarmentPreview } from '@/components/Garment';
import { Button, Drawer, Field, Notice, Segmented, Switch } from '@/components/ui';

import { useToastStore } from '@/stores/useToastStore';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { codeUsagesText, errorText, errorTextWithTrace, fieldErrorMap, fieldErrorsLine } from '@/lib/errorText';
import { HAS_APP_SHAPES, isKnownShape, lightnessOf, resolveShape, SHAPE_KEYS } from '@/lib/garment';
import { qk } from '@/lib/queries';
import type { ICodeBook } from '@/lib/queries';

const SLOTS: { value: TSlot | ''; label: string }[] = [
  { value: 'TOP', label: '상의 칸 (TOP)' },
  { value: 'BOTTOM', label: '하의 칸 (BOTTOM)' },
  { value: 'DRESS', label: '원피스 칸 (DRESS)' },
  { value: 'OUTER', label: '아우터 칸 (OUTER)' },
  { value: 'SHOES', label: '신발 칸 (SHOES)' },
  { value: 'BAG', label: '가방 칸 (BAG)' },
  { value: '', label: '없음 — 코디에 안 들어감 (null)' },
];

const VOLUME = [
  { value: -1, label: '-1 슬림' },
  { value: 0, label: '0 레귤러' },
  { value: 1, label: '1 볼륨' },
];

const WARMTH = [
  { value: 0, label: '0 얇음' },
  { value: 1, label: '1 보통' },
  { value: 2, label: '2 따뜻' },
  { value: 3, label: '3 아주 따뜻' },
];

/** 속성 폼이 있는 그룹. 나머지는 attrs 없음 (06 3장) */
const ATTR_GROUPS = new Set(['CATEGORY', 'SUB_TYPE', 'COLOR', 'ITEM_STATUS', 'REGION', 'BODY_TYPE']);
const NO_ATTR_GROUPS = new Set(['PATTERN', 'SEASON', 'STYLE', 'AGE_RANGE', 'FIT']);

/** BODY_TYPE attrs.description·hint 최대 길이 (서버 CodeAttrs.TEXT_MAX) */
const BODY_TEXT_MAX = 80;

const CUSTOM_SHAPE = '__custom__';

/** 코드 문자열 규칙: 영문 대문자·숫자·밑줄, COLOR는 영문 소문자·숫자·밑줄. 1~30자 */
const codePattern = (group: string): RegExp => {
  return group === 'COLOR' ? /^[a-z0-9_]{1,30}$/ : /^[A-Z0-9_]{1,30}$/;
};

interface IFormState {
  code: string;
  label: string;
  parent: string;
  sortOrder: string;
  active: boolean;
  attrs: ICodeAttrs;
  /** 알 수 없는 그룹용 attrs JSON 직접 입력 */
  attrsJson: string;
}

const initialAttrs = (group: string): ICodeAttrs => {
  switch (group) {
    case 'CATEGORY':
      return { slot: null };
    case 'SUB_TYPE':
      return { volume: 0, warmth: 1, shape: '' };
    case 'COLOR':
      return { hex: '#888888', neutral: false, light: 0.5, pairs: [] };
    case 'ITEM_STATUS':
      return { recommendable: true };
    case 'REGION':
      return { lat: '', lon: '' };
    case 'BODY_TYPE':
      return { description: '', hint: '' };
    default:
      return {};
  }
};

const KNOWN_FIELDS = [
  'code',
  'label',
  'parent',
  'sortOrder',
  'active',
  'attrs',
  'attrs.slot',
  'attrs.volume',
  'attrs.warmth',
  'attrs.shape',
  'attrs.hex',
  'attrs.neutral',
  'attrs.light',
  'attrs.pairs',
  'attrs.recommendable',
  'attrs.lat',
  'attrs.lon',
  'attrs.description',
  'attrs.hint',
];

const baseField = (field: string): string => {
  return field.replace(/\[\d+\]$/, '').replace(/\.\d+$/, '');
};

export const CodeForm = ({
  group,
  original,
  book,
  onClose,
  onDeactivateInstead,
}: {
  group: IAdminCodeGroup;
  /** 없으면 추가 */
  original?: IAdminCode;
  book: ICodeBook;
  onClose: () => void;
  onDeactivateInstead?: (code: IAdminCode) => void;
}) => {
  // 순서 예외: form 상태의 초깃값이 그룹 코드(g)를 읽는다
  const g = group.group;
  // 순서 예외: form 상태의 초깃값이 다음 순서값(maxOrder)을 읽는다
  const maxOrder = Math.max(0, ...group.codes.map((c) => c.sortOrder));

  const [form, setForm] = useState<IFormState>(() => ({
    code: original?.code ?? '',
    label: original?.label ?? '',
    parent: original?.parent ?? '',
    sortOrder: String(original?.sortOrder ?? maxOrder + 10),
    active: original?.active ?? true,
    attrs: { ...initialAttrs(g), ...(original?.attrs ?? {}) },
    attrsJson: JSON.stringify(original?.attrs ?? {}, null, 2),
  }));
  const [errors, setErrors] = useState<Map<string, string>>(new Map());
  const [formError, setFormError] = useState<string | null>(null);
  const [inUse, setInUse] = useState<{ usages: string } | null>(null);

  const queryClient = useQueryClient();
  const toast = useToastStore((s) => s.show);
  const save = useMutation({
    mutationFn: async () => {
      const attrs = buildAttrs();
      const sortOrder = form.sortOrder.trim() ? Number(form.sortOrder) : undefined;
      if (!original) {
        return api.codes.create(g, {
          code: form.code,
          label: form.label.trim(),
          parent: g === 'SUB_TYPE' ? form.parent : undefined,
          sortOrder,
          active: form.active,
          attrs,
        });
      }
      // 바뀐 필드만 보낸다
      const patch: Record<string, unknown> = {};
      if (form.label.trim() !== original.label) patch.label = form.label.trim();
      if (g === 'SUB_TYPE' && form.parent !== (original.parent ?? '')) patch.parent = form.parent;
      if (sortOrder !== undefined && sortOrder !== original.sortOrder) patch.sortOrder = sortOrder;
      if (form.active !== original.active) patch.active = form.active;
      if (JSON.stringify(attrs) !== JSON.stringify(original.attrs ?? {})) patch.attrs = attrs;
      if (Object.keys(patch).length === 0) return 'unchanged' as const;
      return api.codes.update(g, original.code, patch);
    },
    onSuccess: async (result) => {
      if (result === 'unchanged') {
        toast('바뀐 내용이 없습니다.');
        onClose();
        return;
      }
      await queryClient.invalidateQueries({ queryKey: qk.codes });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(isEdit ? `${form.label} 코드를 저장했습니다.` : `${form.code} 코드를 추가했습니다.`);
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) {
        if (e.code === 'CODE_EXISTS') {
          setErrors(new Map([['code', errorText(e)]]));
          setFormError(null);
          return;
        }
        if (e.fieldErrors.length) {
          // 서버 fieldErrors를 입력칸별로 (code·params → 문구). `attrs.pairs[0]`처럼 하위 경로도 `attrs.pairs`에 붙인다
          setErrors(fieldErrorMap(e.fieldErrors, baseField));
          const unknown = e.fieldErrors.filter((f) => !KNOWN_FIELDS.includes(baseField(f.field)));
          setFormError(unknown.length ? fieldErrorsLine(unknown) : null);
          return;
        }
      }
      setFormError(isApiError(e) ? errorTextWithTrace(e) : errorMessage(e));
    },
  });

  const remove = useMutation({
    mutationFn: () => api.codes.remove(g, original!.code),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: qk.codes });
      void queryClient.invalidateQueries({ queryKey: ['audit'] });
      toast(`${original!.code} 코드를 삭제했습니다.`);
      onClose();
    },
    onError: (e) => {
      // usages(옷·프로필·세부 종류 수)를 안내에 넣는다
      if (isApiError(e) && e.code === 'CODE_IN_USE') setInUse({ usages: codeUsagesText(e.params.usages) });
      else setFormError(isApiError(e) ? errorTextWithTrace(e) : errorMessage(e));
    },
  });

  const isEdit = !!original;
  const categories = book.group('CATEGORY');
  const colors = book.group('COLOR');

  const set = <K extends keyof IFormState>(key: K, value: IFormState[K]) => setForm((f) => ({ ...f, [key]: value }));
  const setAttr = (key: string, value: unknown) => setForm((f) => ({ ...f, attrs: { ...f.attrs, [key]: value } }));
  const err = (field: string) => errors.get(field);

  // ───── 검증 ─────
  const clientValidate = (): Map<string, string> => {
    const e = new Map<string, string>();
    if (!isEdit && !codePattern(g).test(form.code)) {
      e.set(
        'code',
        g === 'COLOR' ? '영문 소문자·숫자·밑줄 1~30자로 입력하세요.' : '영문 대문자·숫자·밑줄 1~30자로 입력하세요.',
      );
    }
    if (!form.label.trim()) e.set('label', '라벨을 입력하세요.');
    else if (form.label.trim().length > 50) e.set('label', '라벨은 50자 이하로 입력하세요.');
    if (form.sortOrder.trim() && !/^-?\d+$/.test(form.sortOrder.trim())) e.set('sortOrder', '정수로 입력하세요.');
    if (g === 'SUB_TYPE') {
      if (!form.parent) e.set('parent', '상위 종류를 고르세요.');
      const shape = String(form.attrs.shape ?? '').trim();
      if (!shape) e.set('attrs.shape', '모양 키를 고르거나 입력하세요.');
      else if (shape.length > 30) e.set('attrs.shape', '모양 키는 30자 이하입니다.');
    }
    if (g === 'COLOR') {
      if (!/^#[0-9A-Fa-f]{6}$/.test(String(form.attrs.hex ?? ''))) e.set('attrs.hex', '#RRGGBB 형식으로 입력하세요.');
      const light = Number(form.attrs.light);
      if (!(light >= 0 && light <= 1)) e.set('attrs.light', '0~1 사이 값이어야 합니다.');
    }
    if (g === 'REGION') {
      const lat = String(form.attrs.lat ?? '').trim();
      const lon = String(form.attrs.lon ?? '').trim();
      if (!lat || !(Number(lat) >= -90 && Number(lat) <= 90)) e.set('attrs.lat', '위도는 -90~90 숫자입니다.');
      if (!lon || !(Number(lon) >= -180 && Number(lon) <= 180)) e.set('attrs.lon', '경도는 -180~180 숫자입니다.');
    }
    if (g === 'BODY_TYPE') {
      for (const key of ['description', 'hint'] as const) {
        const v = String(form.attrs[key] ?? '').trim();
        if (!v) e.set(`attrs.${key}`, key === 'description' ? '설명을 입력하세요.' : '한 줄 특징을 입력하세요.');
        else if (v.length > BODY_TEXT_MAX) e.set(`attrs.${key}`, `${BODY_TEXT_MAX}자 이하로 입력하세요.`);
      }
    }
    if (!ATTR_GROUPS.has(g) && !NO_ATTR_GROUPS.has(g)) {
      try {
        const parsed: unknown = JSON.parse(form.attrsJson || '{}');
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed))
          e.set('attrs', 'JSON 객체여야 합니다.');
      } catch {
        e.set('attrs', 'JSON 형식이 아닙니다.');
      }
    }
    return e;
  };

  const buildAttrs = (): ICodeAttrs => {
    if (NO_ATTR_GROUPS.has(g)) return { ...(original?.attrs ?? {}) };
    if (!ATTR_GROUPS.has(g)) return JSON.parse(form.attrsJson || '{}') as ICodeAttrs;
    const a: ICodeAttrs = { ...form.attrs };
    if (g === 'SUB_TYPE') {
      a.shape = String(a.shape ?? '').trim();
      a.volume = Number(a.volume);
      a.warmth = Number(a.warmth);
    }
    if (g === 'COLOR') {
      a.hex = String(a.hex).toUpperCase();
      a.light = Math.round(Number(a.light) * 100) / 100;
      a.pairs = (a.pairs ?? []).filter((p) => p !== (original?.code ?? form.code));
    }
    if (g === 'CATEGORY') a.slot = a.slot || null;
    if (g === 'REGION') {
      a.lat = Number(a.lat);
      a.lon = Number(a.lon);
    }
    if (g === 'BODY_TYPE') {
      a.description = String(a.description ?? '').trim();
      a.hint = String(a.hint ?? '').trim();
    }
    return a;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const v = clientValidate();
    setErrors(v);
    setFormError(null);
    if (v.size === 0) save.mutate();
  };

  const onDelete = () => {
    if (!original) return;
    if (!window.confirm(`${original.label}(${original.code}) 코드를 삭제할까요? 되돌릴 수 없습니다.`)) return;
    setInUse(null);
    remove.mutate();
  };

  return (
    <Drawer
      title={isEdit ? `${group.name} 수정 · ${original!.code}` : `${group.name} 추가`}
      onClose={onClose}
      wide={g === 'SUB_TYPE' || g === 'COLOR'}
      footer={
        <>
          {isEdit ? (
            <Button variant="danger" onClick={onDelete} busy={remove.isPending} className="mr-auto">
              삭제
            </Button>
          ) : (
            <span className="mr-auto" />
          )}
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" type="submit" form="code-form" busy={save.isPending}>
            {isEdit ? '저장' : '추가'}
          </Button>
        </>
      }
    >
      <form id="code-form" onSubmit={submit} noValidate className="form">
        {formError ? <Notice tone="error">{formError}</Notice> : null}
        {inUse && original ? (
          <Notice tone="warn">
            <div>
              이 코드를 쓰는 곳이 있어 삭제할 수 없습니다{inUse.usages ? ` (${inUse.usages})` : ''}. 대신{' '}
              <strong>비활성화</strong>하면 새로 고를 수 없게 되고, 이미 저장된 값의 이름은 그대로 보입니다.
            </div>
            {original.active && onDeactivateInstead ? (
              <Button size="sm" variant="primary" onClick={() => onDeactivateInstead(original)}>
                비활성화하기
              </Button>
            ) : null}
          </Notice>
        ) : null}

        <div className="grid-2">
          <Field
            label="코드"
            required
            error={err('code')}
            hint={
              isEdit
                ? '코드 문자열은 바꿀 수 없습니다.'
                : g === 'COLOR'
                  ? '영문 소문자·숫자·밑줄'
                  : '영문 대문자·숫자·밑줄'
            }
          >
            <input
              className="mono"
              value={form.code}
              disabled={isEdit}
              maxLength={30}
              onChange={(e) => set('code', g === 'COLOR' ? e.target.value.toLowerCase() : e.target.value.toUpperCase())}
              placeholder={g === 'COLOR' ? '예: olive' : '예: TRACK_JACKET'}
            />
          </Field>
          <Field label="라벨 (한국어 이름)" required error={err('label')}>
            <input
              value={form.label}
              maxLength={50}
              onChange={(e) => set('label', e.target.value)}
              placeholder="화면에 보일 이름"
            />
          </Field>
        </div>

        {g === 'SUB_TYPE' ? (
          <Field label="상위 종류" required error={err('parent')}>
            <select value={form.parent} onChange={(e) => set('parent', e.target.value)}>
              <option value="">— 고르세요 —</option>
              {categories.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label} ({c.code}){c.active ? '' : ' · 비활성'}
                </option>
              ))}
            </select>
          </Field>
        ) : null}

        <div className="grid-2">
          <Field
            label="순서값"
            error={err('sortOrder')}
            hint="작을수록 앞. 목록의 위·아래 버튼으로도 바꿀 수 있습니다."
          >
            <input inputMode="numeric" value={form.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} />
          </Field>
          <Field
            label="사용 여부"
            error={err('active')}
            hint={
              g === 'CATEGORY' && !form.active
                ? '이 종류의 세부 종류도 새로 고를 수 없게 됩니다.'
                : '끄면 새로 고를 수 없고, 저장된 값의 이름은 그대로 보입니다.'
            }
          >
            <Switch
              checked={form.active}
              onChange={(v) => set('active', v)}
              label={form.active ? '사용' : '사용 안 함'}
            />
          </Field>
        </div>

        <h3 className="section-title">속성</h3>
        {g === 'CATEGORY' ? <CategoryAttrs attrs={form.attrs} setAttr={setAttr} err={err} /> : null}
        {g === 'SUB_TYPE' ? (
          <SubTypeAttrs
            attrs={form.attrs}
            setAttr={setAttr}
            err={err}
            parent={form.parent}
            parentLabel={book.label('CATEGORY', form.parent)}
            colors={colors}
          />
        ) : null}
        {g === 'COLOR' ? (
          <ColorAttrs
            attrs={form.attrs}
            setAttr={setAttr}
            err={err}
            self={original?.code ?? form.code}
            colors={colors}
          />
        ) : null}
        {g === 'ITEM_STATUS' ? (
          <Field
            label="코디 추천에 넣기"
            error={err('attrs.recommendable')}
            hint="끄면 이 상태의 옷은 추천 후보에서 빠집니다 (세탁·수선 중 등)."
          >
            <Switch
              checked={!!form.attrs.recommendable}
              onChange={(v) => setAttr('recommendable', v)}
              label={form.attrs.recommendable ? '추천에 넣음' : '추천에서 뺌'}
            />
          </Field>
        ) : null}
        {g === 'REGION' ? (
          <>
            <div className="grid-2">
              <Field label="위도 (lat)" required error={err('attrs.lat')} hint="-90~90. 예: 서울 37.5665">
                <input
                  className="mono"
                  inputMode="decimal"
                  value={String(form.attrs.lat ?? '')}
                  onChange={(e) => setAttr('lat', e.target.value)}
                />
              </Field>
              <Field label="경도 (lon)" required error={err('attrs.lon')} hint="-180~180. 예: 서울 126.978">
                <input
                  className="mono"
                  inputMode="decimal"
                  value={String(form.attrs.lon ?? '')}
                  onChange={(e) => setAttr('lon', e.target.value)}
                />
              </Field>
            </div>
            <p className="muted small">
              날씨는 이 좌표로 받습니다. 활성 지역만 주기적으로 받고, 지역을 늘리면 공급자 호출 수도 늘어납니다 (날씨 &gt;
              스케줄의 예상 월 호출 수).
            </p>
          </>
        ) : null}
        {g === 'BODY_TYPE' ? (
          <>
            <Field
              label="설명 (description)"
              required
              error={err('attrs.description')}
              hint={`앱 체형 고르기 카드의 설명입니다. ${BODY_TEXT_MAX}자 이하.`}
            >
              <input
                value={String(form.attrs.description ?? '')}
                maxLength={BODY_TEXT_MAX}
                onChange={(e) => setAttr('description', e.target.value)}
                placeholder="예: 탄탄하고 입체적인 몸. 심플한 정핏이 잘 어울려요"
              />
            </Field>
            <Field
              label="한 줄 특징 (hint)"
              required
              error={err('attrs.hint')}
              hint={`회원이 자기 체형을 알아보도록 돕는 특징입니다. ${BODY_TEXT_MAX}자 이하.`}
            >
              <input
                value={String(form.attrs.hint ?? '')}
                maxLength={BODY_TEXT_MAX}
                onChange={(e) => setAttr('hint', e.target.value)}
                placeholder="예: 상체에 볼륨이 있고 허리 위치가 높은 편이에요"
              />
            </Field>
          </>
        ) : null}
        {NO_ATTR_GROUPS.has(g) ? <p className="muted">이 그룹은 속성이 없습니다.</p> : null}
        {!ATTR_GROUPS.has(g) && !NO_ATTR_GROUPS.has(g) ? (
          <Field label="attrs (JSON)" error={err('attrs')} hint="이 사이트가 모르는 그룹이라 JSON으로 직접 입력합니다.">
            <textarea
              className="mono"
              rows={6}
              value={form.attrsJson}
              onChange={(e) => set('attrsJson', e.target.value)}
            />
          </Field>
        ) : null}
        {err('attrs') && ATTR_GROUPS.has(g) ? <div className="field-error">{err('attrs')}</div> : null}

        {isEdit ? (
          <p className="muted small">
            이 코드를 쓰는 아이템: <strong>{original!.usageCount.toLocaleString('ko-KR')}개</strong>
          </p>
        ) : null}
      </form>
    </Drawer>
  );
};

// ───────── 그룹별 속성 폼 ─────────

interface IAttrProps {
  attrs: ICodeAttrs;
  setAttr: (key: string, value: unknown) => void;
  err: (field: string) => string | undefined;
}

const CategoryAttrs = ({ attrs, setAttr, err }: IAttrProps) => {
  return (
    <Field label="코디 칸 (slot)" error={err('attrs.slot')} hint="추천 코디에서 이 종류가 들어갈 칸입니다.">
      <select value={attrs.slot ?? ''} onChange={(e) => setAttr('slot', e.target.value || null)}>
        {SLOTS.map((s) => (
          <option key={s.value} value={s.value}>
            {s.label}
          </option>
        ))}
      </select>
    </Field>
  );
};

const SubTypeAttrs = ({
  attrs,
  setAttr,
  err,
  parent,
  parentLabel,
  colors,
}: IAttrProps & { parent: string; parentLabel: string; colors: IAdminCode[] }) => {
  // 순서 예외: custom 상태의 초깃값이 모양 키(shape)를 읽는다
  const shape = String(attrs.shape ?? '');
  // 순서 예외: previewColor 상태의 초깃값이 쓸 수 있는 색 목록(activeColors)을 읽는다
  const activeColors = colors.filter((c) => c.active && typeof c.attrs.hex === 'string');

  const [custom, setCustom] = useState(() => !!shape && !isKnownShape(shape));
  const [previewColor, setPreviewColor] = useState(
    () => (activeColors.find((c) => c.code === 'navy') ?? activeColors[0])?.code ?? '',
  );

  const pc = colors.find((c) => c.code === previewColor);
  const known = isKnownShape(shape);
  /** 실제로 그려지는 모양 (모르는 키면 상위 종류의 기본 모양, 그것도 없으면 TEE) */
  const drawnKey = resolveShape(shape, parent);

  return (
    <div className="subtype-attrs">
      <div className="subtype-fields">
        <Field label="볼륨 (volume)" error={err('attrs.volume')} hint="실루엣 진단에 씁니다.">
          <Segmented value={Number(attrs.volume ?? 0)} options={VOLUME} onChange={(v) => setAttr('volume', v)} />
        </Field>
        <Field label="보온 (warmth)" error={err('attrs.warmth')} hint="날씨 적합 점수에 씁니다.">
          <Segmented value={Number(attrs.warmth ?? 0)} options={WARMTH} onChange={(v) => setAttr('warmth', v)} />
        </Field>
        <Field
          label="일러스트 모양 (shape)"
          required
          error={err('attrs.shape')}
          hint={
            !HAS_APP_SHAPES
              ? '앱 일러스트 데이터를 찾지 못해 모양 키를 직접 입력합니다.'
              : custom
                ? '앱이 모르는 키면 상위 종류의 기본 모양으로 그립니다.'
                : '앱이 아는 모양 목록입니다.'
          }
        >
          {HAS_APP_SHAPES ? (
            <select
              className="mono"
              value={custom ? CUSTOM_SHAPE : known ? shape : ''}
              onChange={(e) => {
                if (e.target.value === CUSTOM_SHAPE) {
                  setCustom(true);
                } else {
                  setCustom(false);
                  setAttr('shape', e.target.value);
                }
              }}
            >
              <option value="">— 고르세요 —</option>
              {SHAPE_KEYS.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
              <option value={CUSTOM_SHAPE}>직접 입력…</option>
            </select>
          ) : null}
          {custom || !HAS_APP_SHAPES ? (
            <input
              className="mono mt-xs"
              value={shape}
              maxLength={30}
              placeholder="예: TRACK_JACKET"
              onChange={(e) => setAttr('shape', e.target.value.toUpperCase())}
            />
          ) : null}
        </Field>
      </div>

      <div className="garment-preview">
        <div className="garment-canvas">
          <GarmentPreview shape={shape} category={parent} hex={pc?.attrs.hex} light={pc?.attrs.light} size={150} />
        </div>
        <div className="garment-caption">
          {!HAS_APP_SHAPES ? (
            <span className="text-danger">앱 일러스트 데이터 없음 (임시 사각형)</span>
          ) : known ? (
            <span className="mono">{shape}</span>
          ) : (
            <span>
              {shape ? <span className="mono">{shape}</span> : '모양 미지정'} →{' '}
              {parent ? `${parentLabel} 기본 모양` : '기본 모양'}
              <span className="mono"> ({drawnKey})</span>
            </span>
          )}
        </div>
        <div className="chip-row" aria-label="미리보기 색">
          {activeColors.map((c) => (
            <button
              type="button"
              key={c.code}
              className={`chip-btn ${c.code === previewColor ? 'on' : ''}`}
              title={`${c.label} (${c.code})`}
              onClick={() => setPreviewColor(c.code)}
            >
              <ColorChip hex={c.attrs.hex} size={14} />
            </button>
          ))}
        </div>
        <div className="muted small">미리보기 색은 저장되지 않습니다.</div>
      </div>
    </div>
  );
};

const ColorAttrs = ({ attrs, setAttr, err, self, colors }: IAttrProps & { self: string; colors: IAdminCode[] }) => {
  const hex = String(attrs.hex ?? '');
  const validHex = /^#[0-9A-Fa-f]{6}$/.test(hex);
  const light = Number(attrs.light ?? 0);
  const pairs = useMemo(() => new Set(attrs.pairs ?? []), [attrs.pairs]);
  const suggested = lightnessOf(hex);
  const others = colors.filter((c) => c.code !== self);

  const toggle = (code: string) => {
    const next = new Set(pairs);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    // 원래 순서(코드 목록 순서)를 지킨다
    setAttr(
      'pairs',
      others
        .map((c) => c.code)
        .filter((c) => next.has(c))
        .concat([...next].filter((c) => !others.some((o) => o.code === c))),
    );
  };

  return (
    <>
      <div className="grid-2">
        <Field label="표시 색 (hex)" required error={err('attrs.hex')}>
          <div className="row gap-sm">
            <input
              type="color"
              className="color-input"
              value={validHex ? hex.toLowerCase() : '#888888'}
              onChange={(e) => setAttr('hex', e.target.value.toUpperCase())}
            />
            <input
              className="mono"
              value={hex}
              maxLength={7}
              onChange={(e) => setAttr('hex', e.target.value.trim())}
              placeholder="#RRGGBB"
            />
          </div>
        </Field>
        <Field
          label="무채색·뉴트럴 (neutral)"
          error={err('attrs.neutral')}
          hint="뉴트럴 색은 대부분의 색과 어울린다고 봅니다."
        >
          <Switch
            checked={!!attrs.neutral}
            onChange={(v) => setAttr('neutral', v)}
            label={attrs.neutral ? '뉴트럴' : '유채색'}
          />
        </Field>
      </div>
      <Field
        label={`밝기 (light) — ${light.toFixed(2)}`}
        error={err('attrs.light')}
        hint={
          suggested !== null ? (
            <>
              0 어두움 ~ 1 밝음. hex로 계산한 값 {suggested.toFixed(2)}{' '}
              <button type="button" className="link-btn" onClick={() => setAttr('light', suggested)}>
                적용
              </button>
            </>
          ) : (
            '0 어두움 ~ 1 밝음'
          )
        }
      >
        <div className="row gap-sm">
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={light}
            onChange={(e) => setAttr('light', Number(e.target.value))}
            className="grow"
          />
          <span className="light-swatch" style={{ background: validHex ? hex : undefined }}>
            <span style={{ color: light > 0.6 ? '#111' : '#fff' }}>Aa</span>
          </span>
        </div>
      </Field>
      <Field
        label={`어울리는 색 (pairs) — ${pairs.size}개`}
        error={err('attrs.pairs')}
        hint="저장하면 상대 색의 pairs에도 양방향으로 반영됩니다. 흐린 칩은 비활성 색입니다."
      >
        <div className="pair-grid">
          {others.map((c) => (
            <button
              type="button"
              key={c.code}
              className={`pair-chip ${pairs.has(c.code) ? 'on' : ''} ${c.active ? '' : 'inactive'}`}
              aria-pressed={pairs.has(c.code)}
              onClick={() => toggle(c.code)}
              title={c.code}
            >
              <ColorChip hex={c.attrs.hex} size={14} />
              {c.label}
            </button>
          ))}
        </div>
        <div className="row gap-xs mt-xs">
          <button
            type="button"
            className="link-btn"
            onClick={() =>
              setAttr(
                'pairs',
                others.filter((c) => c.active).map((c) => c.code),
              )
            }
          >
            모두 선택
          </button>
          <span className="muted">·</span>
          <button type="button" className="link-btn" onClick={() => setAttr('pairs', [])}>
            모두 해제
          </button>
        </div>
      </Field>
    </>
  );
};
