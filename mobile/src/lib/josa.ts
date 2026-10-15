/** 받침 유무에 따라 조사를 고른다. josa('니트', '을', '를') → '니트를' */
export function josa(word: string, withFinal: string, withoutFinal: string): string {
  const last = word.charCodeAt(word.length - 1);
  if (last < 0xac00 || last > 0xd7a3) return `${word}${withoutFinal}`;
  const hasFinal = (last - 0xac00) % 28 !== 0;
  return `${word}${hasFinal ? withFinal : withoutFinal}`;
}
