// import 그룹 정의 (README "코드 규칙" 6, simple-import-sort의 groups 옵션). 그룹은 import 경로가 실제로 가리키는 위치로 정한다.
//    내부 모듈은 모두 '@/' 별칭으로 가져오므로(상대 경로 금지) 경로 문자열만으로 위치를 알 수 있다.
//    각 그룹은 [값 import, type import] 두 칸이라 같은 그룹 안에서 import type이 값 import 뒤에 온다.
//    simple-import-sort는 type import 경로 끝에 \u0000을, 부수 효과 import(import './x.css') 앞에 \u0000을 붙여 비교한다.
//    한 import가 여러 정규식에 맞으면 가장 길게 맞은 것, 길이가 같으면 앞 그룹이 이긴다.
const END = '(?=/|\\u0000|$)'; // 경로 조각 경계
// 앱(mobile/src/data)의 순수 TS 데이터 별칭: 옷 일러스트 모양, 디자인 테마 값, 마스코트 그림
const APP_DATA = `@(?:garment-shapes|app-themes|app-mascots)${END}`;
const importGroup = (prefix) => [`${prefix}[^\\u0000]*$`, `${prefix}[^\\u0000]*\\u0000$`];
export const IMPORT_GROUPS = [
  importGroup(`^react(?:-dom)?${END}`), // ① react: react, react-dom, react/*, react-dom/*
  importGroup(`^@/api${END}`), // ② api: src/api/**
  importGroup(`^@/(?:components|pages|App)${END}`), // ③ component: src/components/**, src/pages/**, src/App
  importGroup(`^@/(?:hooks|stores)${END}`), // ④ hooks: src/hooks/**, zustand 스토어 src/stores/**
  importGroup(`^(?!@/|${APP_DATA})@?\\w`), // ⑤ 3rd party: react를 뺀 외부 패키지 (node: 포함)
  importGroup(`^(?:@/|${APP_DATA})`), // ⑥ local: 위에 안 든 내부 모듈(src/lib/**, 타입·유틸 등), 앱 데이터 별칭
  ['^\\u0000.*\\.css$'], // ⑦ css
];
