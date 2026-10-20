// 쇼핑몰 캡처로 옷 사진 등록 (doc/24): 고를 수 있는 쇼핑몰과 화면 이동.
// 규칙(목록·이동 제한·자르기 계산)은 mallCaptureRules.ts, 화면은 src/app/item/mall.tsx.
import { router } from 'expo-router';
import { Platform } from 'react-native';

import { allCodes } from './codes';
import { type IMall, mallList } from './mallCaptureRules';

/** 앱 안 웹 화면(react-native-webview)은 웹 체험판에서 쓸 수 없다 */
export const MALL_CAPTURE_SUPPORTED = Platform.OS !== 'web';

/** 고를 수 있는 쇼핑몰 (웹 체험판이면 빈 목록). 코드가 바뀌면 화면이 useCodesVersion()으로 다시 그린다 */
export const availableMalls = (): IMall[] => (MALL_CAPTURE_SUPPORTED ? mallList(allCodes('MALL')) : []);

/** 쇼핑몰 화면 열기. 결과는 useMallCapture에 target 몫으로 들어온다 */
export const openMallCapture = (target: string): void => {
  router.push({ pathname: '/item/mall', params: { for: target } });
};
