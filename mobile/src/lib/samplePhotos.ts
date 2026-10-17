// 샘플 옷장의 옷 사진: 디자인 보드 옷장 격자에서 잘라 온 사진(mobile/assets/samples/*.jpg)을 앱에 묶어 둔다.
//
// 기기 사진처럼 item.imageUri(계정 모드는 photos[id])에 붙이되, 실제 파일 주소 대신
// 'wearly-sample://<키>' 같은 고정 주소를 저장하고 그릴 때 번들 이미지로 바꾼다.
// Asset.fromModule(...).uri는 개발 중엔 Metro 주소(PC의 IP), 출시 빌드에선 앱 설치 경로가 들어가
// 저장해 두면 IP가 바뀌거나 앱을 업데이트할 때 깨진다. expo-image는 require() 결과(숫자)를 source로 바로 받는다.
import type { ImageSource } from 'expo-image';

export const SAMPLE_PHOTO_SCHEME = 'wearly-sample://';

// Metro는 정적 require만 묶으므로 파일마다 한 줄씩 적는다
const SAMPLE_PHOTOS = {
  nyang_cream_knit: require('../../assets/samples/nyang_cream_knit.jpg'),
  nyang_stripe_knit: require('../../assets/samples/nyang_stripe_knit.jpg'),
  nyang_olive_jacket: require('../../assets/samples/nyang_olive_jacket.jpg'),
  nyang_jeans: require('../../assets/samples/nyang_jeans.jpg'),
  nyang_black_skirt: require('../../assets/samples/nyang_black_skirt.jpg'),
  nyang_beige_coat: require('../../assets/samples/nyang_beige_coat.jpg'),
  nyang_beige_bag: require('../../assets/samples/nyang_beige_bag.jpg'),
  nyang_beige_sneakers: require('../../assets/samples/nyang_beige_sneakers.jpg'),
  nyang_white_sneakers: require('../../assets/samples/nyang_white_sneakers.jpg'),
  closeta_cable_knit: require('../../assets/samples/closeta_cable_knit.jpg'),
  closeta_lightblue_shirt: require('../../assets/samples/closeta_lightblue_shirt.jpg'),
  closeta_cream_coat: require('../../assets/samples/closeta_cream_coat.jpg'),
  closeta_jeans_light: require('../../assets/samples/closeta_jeans_light.jpg'),
  closeta_cream_skirt: require('../../assets/samples/closeta_cream_skirt.jpg'),
  closeta_stripe_tee: require('../../assets/samples/closeta_stripe_tee.jpg'),
  closeta_knit_shorts_set: require('../../assets/samples/closeta_knit_shorts_set.jpg'),
  closeta_brown_skirt: require('../../assets/samples/closeta_brown_skirt.jpg'),
  closeta_gray_dress: require('../../assets/samples/closeta_gray_dress.jpg'),
} satisfies Record<string, number>;

export type TSamplePhoto = keyof typeof SAMPLE_PHOTOS;

/** 샘플 사진 키 → 저장할 고정 주소 */
export const samplePhotoUri = (key: TSamplePhoto): string => `${SAMPLE_PHOTO_SCHEME}${key}`;

/**
 * 저장된 사진 주소 → expo-image source. 샘플 주소면 번들 이미지, 아니면 기기 사진 uri.
 * 모르는 샘플 키(앱 버전이 바뀌어 사진이 빠진 경우 등)면 undefined → 일러스트로 그린다.
 */
export const photoSource = (uri: string | undefined): ImageSource | number | undefined => {
  if (!uri) return undefined;
  if (!uri.startsWith(SAMPLE_PHOTO_SCHEME)) return { uri };
  const key = uri.slice(SAMPLE_PHOTO_SCHEME.length);
  return Object.prototype.hasOwnProperty.call(SAMPLE_PHOTOS, key) ? SAMPLE_PHOTOS[key as TSamplePhoto] : undefined;
};
