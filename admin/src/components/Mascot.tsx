// 마스코트 그림 미리보기 (doc/12 4장). 앱 Mascot과 같은 파일 매핑(mobile/src/data/mascots)으로
// 디자인 보드에서 잘라 온 PNG(mobile/assets/mascots)를 그린다.
// 투명 그림은 정사각형 칸에 비율대로(contain), 장면 그림(배경 있는 직사각형)은 가로 폭 size에 높이는 비율대로, 둥근 모서리.
import { mascotImage, mascotImageUrl } from '@/lib/appTheme';
import type { TMascotId, TMascotPose } from '@/lib/appTheme';

export const MascotImage = ({
  mascot,
  pose,
  size = 96,
  title,
}: {
  mascot: TMascotId;
  pose: TMascotPose;
  size?: number;
  title?: string;
}) => {
  const img = mascotImage(mascot, pose);
  const url = mascotImageUrl(img.file);
  const label = title ?? `${mascot} ${pose}`;
  if (!url) {
    return (
      <span className="mascot-missing" style={{ width: size, height: size }} title={`${label}: ${img.file} 없음`}>
        ?
      </span>
    );
  }
  const height = img.scene ? Math.round((size * img.height) / img.width) : size;
  return (
    <img
      className={img.scene ? 'mascot-img mascot-scene' : 'mascot-img'}
      src={url}
      width={size}
      height={height}
      alt={label}
      title={label}
      data-mascot={mascot}
      data-pose={pose}
      data-file={img.file}
      data-scene={img.scene ? 'true' : 'false'}
      draggable={false}
    />
  );
};
