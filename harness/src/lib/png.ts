// 테스트용 작은 PNG를 코드로 만든다 (외부 파일 없이).
import { deflateSync } from 'node:zlib';

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf: Uint8Array): number {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type: string, data: Uint8Array): Buffer {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData));
  return Buffer.concat([len, typeAndData, crc]);
}

/** size×size 단색 RGB PNG. 색이 다르면 바이트도 다르다. */
export function solidPng(rgb: [number, number, number], size = 8): Uint8Array<ArrayBuffer> {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // 비트 깊이
  ihdr[9] = 2; // 색 형식: RGB
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  const row = Buffer.alloc(1 + size * 3);
  for (let x = 0; x < size; x++) row.set(rgb, 1 + x * 3);
  const rawRows = Buffer.concat(Array.from({ length: size }, () => row));
  return Uint8Array.from(
    Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk('IHDR', ihdr),
      chunk('IDAT', deflateSync(rawRows)),
      chunk('IEND', new Uint8Array(0)),
    ]),
  );
}

export function pngBlob(rgb: [number, number, number], size = 8): Blob {
  return new Blob([solidPng(rgb, size)], { type: 'image/png' });
}

/** 크기 제한(10MB) 검사용. PNG 서명 뒤를 0으로 채운 bytes 크기의 Blob */
export function oversizedPngBlob(bytes: number): Blob {
  const buf = new Uint8Array(bytes);
  buf.set(solidPng([10, 20, 30]));
  return new Blob([buf], { type: 'image/png' });
}
