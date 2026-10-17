// 마스코트 그림 (doc/12 4장). 디자인 보드에서 잘라 온 PNG를 그대로 그린다.
// 어떤 파일을 쓸지는 data/mascots(순수 TS 매핑), 어떤 마스코트를 쓸지는 현재 테마가 정한다.
import { Image } from 'expo-image';

import { mascotImage, type TMascotId, type TMascotPose } from '@/data/mascots';
import { useTheme } from '@/store/useThemeStore';
import { MASCOT_SOURCES } from './mascotSources';

interface IMascotProps {
  pose: TMascotPose;
  /** 투명 그림: 정사각형 칸 한 변. 장면 그림: 가로 폭(높이는 그림 비율대로) */
  size?: number;
  /** 장면 그림(배경 있는 직사각형)일 때만 size 대신 쓸 가로 폭. 빈 화면처럼 그림을 크게 보여 줄 자리에서 쓴다 */
  sceneWidth?: number;
  /** 지정하지 않으면 현재 테마의 마스코트 */
  mascot?: TMascotId;
}

export const Mascot = ({ pose, size = 120, sceneWidth, mascot }: IMascotProps) => {
  const theme = useTheme();

  const img = mascotImage(mascot ?? theme.mascot, pose);
  const source = MASCOT_SOURCES[img.file];

  if (img.scene) {
    const width = sceneWidth ?? size;
    return (
      <Image
        source={source}
        style={{ width, height: Math.round((width * img.height) / img.width), borderRadius: theme.radius.lg }}
        contentFit="cover"
      />
    );
  }
  return <Image source={source} style={{ width: size, height: size }} contentFit="contain" />;
};

/** 현재 테마에서 이 자세가 장면 그림(배경 있는 직사각형)인지. 화면이 그림 둘레 상자를 그릴지 정할 때 쓴다 */
export const useMascotIsScene = (pose: TMascotPose, mascot?: TMascotId): boolean => {
  const themeMascot = useTheme().mascot;
  return mascotImage(mascot ?? themeMascot, pose).scene;
};
