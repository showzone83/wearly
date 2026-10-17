// 로컬 ESLint 규칙 묶음 (flat config 인라인 플러그인 `local`)
import { componentOrder } from './component-order.js';

export default {
  meta: { name: 'local' },
  rules: { 'component-order': componentOrder },
};
