#!/usr/bin/env python3
"""전국 시·군·구 자료(korea-districts.json) 만들기 (19 문서, 장소 검색).

서버는 실행 중에 이 자료만 읽고 외부를 부르지 않는다. 이 스크립트는 사람이 가끔(행정구역이 바뀔 때) 돌려
결과 JSON을 커밋한다. 테스트에서 실행하지 않는다.

- 이름 목록: 아래 DISTRICTS (2026-10 현행 행정구역. 강원특별자치도·전북특별자치도, 군위군 대구 편입(2023-07),
  부천시 일반구 부활(2024-01), 화성시 일반구 4개(2026-02), 인천 제물포구·영종구·검단구(2026-07) 반영).
- 좌표: Open-Meteo 지오코딩(키 없음)으로 하나씩 찾는다. 결과 중 대한민국이고 admin1(시·도)이 맞으며
  · 이름이 그 시·군·구 이름이거나
  · admin2(일반구는 admin2=시, admin3=구)가 그 시·군·구인
  곳만 받는다. 산·고개 같은 높은 지형은 받지 않는다. 행정 중심(PPLA·PPLA2·PPLA3…)·인구 있는 곳을 먼저 고른다.
  admin 이름은 줄기 이름("계양")·로마자 일반구("Wansan-gu")·옛 이름(인천 중구 → 제물포구·영종구)도 받는다.
- 찾는 순서: 정식 이름("해운대구") → 줄기 이름("해운대") → 힌트 지명(HINTS: 시·군은 로마자 이름, 구는 구청이 있는
  동 이름 등. 결과의 admin으로 검증하므로 틀린 힌트는 그냥 실패한다). 그래도 없으면 이번 실행에서 받은 모든 응답 중
  그 시·군·구 안의 곳(다른 검색 결과). 모두 실패하면 상위 시·도 좌표를 쓰고 approx: true.
- 지오코딩(GeoNames 기반)에는 구 단위 행정구역(ADM2)이 없고 한글 이름이 없는 곳이 많아, "다른 검색 결과"로 잡힌
  곳은 그 시·군·구 안이지만 청사에서 떨어진 마을일 수 있다 (via 칸에 남는다).
- 요청 사이 0.2초. --cache 파일을 주면 같은 검색어는 다시 부르지 않는다.

사용:
  python3 backend/scripts/build-districts.py \\
      --out backend/src/main/resources/data/korea-districts.json [--cache geo-cache.json] [--report report.json]

2026-10-18 생성: 268곳, 호출 약 710회(같은 검색어 캐시). 결과 요약은 --report로 받는다.
"""

import argparse
import json
import sys
import time
import urllib.parse
import urllib.request

API = "https://geocoding-api.open-meteo.com/v1/search"
SLEEP = 0.2
COUNT = 100

# REGION 코드 → (짧은 이름, 정식 이름, 지오코딩 admin1로 받는 이름들, REGION 좌표[근사용])
# 좌표는 코드 그룹 REGION의 attrs(lat·lon)와 같다 (doc/06_코드_초기값.json)
REGIONS = {
    "SEOUL": ("서울", "서울특별시", ["서울특별시", "서울"], (37.5665, 126.978)),
    "BUSAN": ("부산", "부산광역시", ["부산광역시"], (35.1796, 129.0756)),
    "DAEGU": ("대구", "대구광역시", ["대구광역시"], (35.8714, 128.6014)),
    "INCHEON": ("인천", "인천광역시", ["인천광역시"], (37.4563, 126.7052)),
    "GWANGJU": ("광주", "광주광역시", ["광주광역시"], (35.1595, 126.8526)),
    "DAEJEON": ("대전", "대전광역시", ["대전광역시"], (36.3504, 127.3845)),
    "ULSAN": ("울산", "울산광역시", ["울산광역시"], (35.5384, 129.3114)),
    "SEJONG": ("세종", "세종특별자치시", ["세종특별자치시", "세종시"], (36.48, 127.289)),
    "GYEONGGI": ("경기", "경기도", ["경기도"], (37.2636, 127.0286)),
    "GANGWON": ("강원", "강원특별자치도", ["강원특별자치도", "강원도"], (37.8813, 127.7298)),
    "CHUNGBUK": ("충북", "충청북도", ["충청북도"], (36.6424, 127.489)),
    "CHUNGNAM": ("충남", "충청남도", ["충청남도"], (36.6588, 126.6728)),
    "JEONBUK": ("전북", "전북특별자치도", ["전북특별자치도", "전라북도"], (35.8242, 127.148)),
    "JEONNAM": ("전남", "전라남도", ["전라남도"], (34.8161, 126.4629)),
    "GYEONGBUK": ("경북", "경상북도", ["경상북도"], (36.576, 128.5056)),
    "GYEONGNAM": ("경남", "경상남도", ["경상남도"], (35.228, 128.6811)),
    "JEJU": ("제주", "제주특별자치도", ["제주특별자치도", "제주도"], (33.4996, 126.5312)),
}

# 시·도별 시·군·구. "시>구 구 구"는 시와 그 일반구. 세종은 시·군·구가 없다.
DISTRICTS = {
    "SEOUL": "종로구 중구 용산구 성동구 광진구 동대문구 중랑구 성북구 강북구 도봉구 노원구 은평구 서대문구 마포구 "
    "양천구 강서구 구로구 금천구 영등포구 동작구 관악구 서초구 강남구 송파구 강동구",
    "BUSAN": "중구 서구 동구 영도구 부산진구 동래구 남구 북구 해운대구 사하구 금정구 강서구 연제구 수영구 사상구 기장군",
    "DAEGU": "중구 동구 서구 남구 북구 수성구 달서구 달성군 군위군",
    "INCHEON": "제물포구 영종구 미추홀구 연수구 남동구 부평구 계양구 서구 검단구 강화군 옹진군",
    "GWANGJU": "동구 서구 남구 북구 광산구",
    "DAEJEON": "동구 중구 서구 유성구 대덕구",
    "ULSAN": "중구 남구 동구 북구 울주군",
    "GYEONGGI": "수원시>장안구,권선구,팔달구,영통구 고양시>덕양구,일산동구,일산서구 용인시>처인구,기흥구,수지구 "
    "성남시>수정구,중원구,분당구 부천시>원미구,소사구,오정구 화성시>만세구,효행구,병점구,동탄구 "
    "안산시>상록구,단원구 남양주시 안양시>만안구,동안구 평택시 시흥시 파주시 의정부시 김포시 광주시 광명시 "
    "군포시 하남시 오산시 양주시 이천시 구리시 안성시 포천시 의왕시 여주시 동두천시 과천시 가평군 양평군 연천군",
    "GANGWON": "춘천시 원주시 강릉시 동해시 태백시 속초시 삼척시 홍천군 횡성군 영월군 평창군 정선군 철원군 화천군 "
    "양구군 인제군 고성군 양양군",
    "CHUNGBUK": "청주시>상당구,서원구,흥덕구,청원구 충주시 제천시 보은군 옥천군 영동군 증평군 진천군 괴산군 음성군 단양군",
    "CHUNGNAM": "천안시>동남구,서북구 공주시 보령시 아산시 서산시 논산시 계룡시 당진시 금산군 부여군 서천군 청양군 "
    "홍성군 예산군 태안군",
    "JEONBUK": "전주시>완산구,덕진구 군산시 익산시 정읍시 남원시 김제시 완주군 진안군 무주군 장수군 임실군 순창군 "
    "고창군 부안군",
    "JEONNAM": "목포시 여수시 순천시 나주시 광양시 담양군 곡성군 구례군 고흥군 보성군 화순군 장흥군 강진군 해남군 "
    "영암군 무안군 함평군 영광군 장성군 완도군 진도군 신안군",
    "GYEONGBUK": "포항시>남구,북구 경주시 김천시 안동시 구미시 영주시 영천시 상주시 문경시 경산시 의성군 청송군 "
    "영양군 영덕군 청도군 고령군 성주군 칠곡군 예천군 봉화군 울진군 울릉군",
    "GYEONGNAM": "창원시>의창구,성산구,마산합포구,마산회원구,진해구 진주시 통영시 사천시 김해시 밀양시 거제시 양산시 "
    "의령군 함안군 창녕군 고성군 남해군 하동군 산청군 함양군 거창군 합천군",
    "JEJU": "제주시 서귀포시",
}

# 정식 이름·줄기 이름으로 못 찾는 곳의 힌트: (시·도 코드, 시 이름 또는 "", 시·군·구 이름) → 힌트 지명들.
# 힌트는 그 시·군·구 안(가능하면 구청·시청·군청이 있는 동·읍)의 지명이고, 결과의 admin2(일반구는 admin3)가
# 그 시·군·구와 맞을 때만 받는다. 좌표를 추측하지 않는다.
# 시·군은 로마자 이름(지오코딩 자료에 한글 이름이 없는 곳이 많다), 구는 구청이 있는 동 이름을 주로 쓴다.
_HINT_TEXT = {
    "SEOUL": "종로구=수송동,혜화동 중구=명동 성동구=행당동 광진구=자양동,구의동 동대문구=용두동,청량리 중랑구=신내동 성북구=삼선동 "
    "강북구=수유동 도봉구=방학동 노원구=상계동,Sanggye-dong,공릉동 은평구=녹번동 서대문구=연희동 마포구=성산동,망원동,상암동 구로구=구로동,Guro-dong,신도림 "
    "금천구=시흥동 동작구=노량진동 관악구=봉천동 서초구=서초동,방배동 강남구=삼성동 송파구=신천동,잠실,가락동 강동구=성내동,천호동",
    "BUSAN": "중구=대청동,남포동,Nampo 서구=토성동,충무동,Amnam 동구=수정동,초량동,Choryang 영도구=영도,Yeongdo,Taejongdae 부산진구=Seomyeon,부전동,가야동 남구=대연동,Daeyeon-dong,Yongho 북구=구포,덕천동 "
    "사하구=당리동,하단동,Dadaepo 금정구=부곡동,구서동,Jangjeon 연제구=연산동 수영구=광안동,민락동,Gwangalli 사상구=Sasang,괘법동,Jurye 강서구=대저동",
    "DAEGU": "중구=동인동,Dongin-dong,Samdeok-dong 동구=신암동 서구=평리동 남구=대명동,Daemyeong-dong,Bongdeok-dong 북구=침산동,칠성동,Bokhyeon-dong 수성구=범어동 달서구=Seongseo,상인동 "
    "달성군=화원",
    "INCHEON": "제물포구=Jemulpo,송림동 영종구=영종도,운서동 미추홀구=주안동 서구=심곡동 검단구=Geomdan 연수구=연수동 "
    "부평구=Bupyeong 옹진군=영흥도",
    "GWANGJU": "동구=서남동 서구=농성동 남구=봉선동 북구=용봉동 광산구=송정동",
    "DAEJEON": "동구=가오동 중구=대흥동 서구=둔산동 유성구=Yuseong 대덕구=오정동",
    "ULSAN": "중구=성남동 남구=신정동 동구=방어동,Bangeo-dong,Jeonha-dong 북구=화봉동 울주군=Eonyang",
    "GYEONGGI": "수원시>장안구=정자동,파장동 수원시>권선구=권선동 수원시>팔달구=인계동 수원시>영통구=영통동 "
    "고양시>일산동구=장항동 용인시=Yongin 용인시>처인구=김량장동 용인시>수지구=풍덕천동 "
    "성남시>수정구=태평동 성남시>중원구=성남동 성남시>분당구=수내동,서현동,Jeongja 부천시>소사구=Sosa "
    "화성시>만세구=Hyangnam 화성시>효행구=Bongdam 화성시>병점구=Byeongjeom 화성시>동탄구=Dongtan,동탄 "
    "안산시>상록구=본오동,Bono-dong,Sangnoksu 안산시>단원구=고잔동 안양시>만안구=안양동,석수동,Bakdal 안양시>동안구=Pyeongchon "
    "시흥시=Siheung 의왕시=고천동 군포시=Sanbon 안성시=Anseong",
    "GANGWON": "태백시=황지동,Hwangji,Jangseong-dong 삼척시=Samcheok 횡성군=Hoengseong,Hoengsong 영월군=Yeongwol 정선군=Jeongseon "
    "고성군=Ganseong",
    "CHUNGBUK": "청주시>상당구=용암동 청주시>서원구=모충동 청주시>흥덕구=복대동 청주시>청원구=Ochang "
    "보은군=Boeun 옥천군=Okcheon 영동군=Yeongdong 증평군=Jeungpyeong,Chungpyong,Jeungpyeong-eup 진천군=Jincheon,Chinchon,Jincheon-eup 괴산군=Goesan",
    "CHUNGNAM": "천안시>동남구=신부동 천안시>서북구=불당동,성정동 계룡시=Gyeryong,Gyeryong-si,Eomsa 금산군=Geumsan 부여군=Buyeo "
    "서천군=Seocheon 청양군=Cheongyang 홍성군=Hongseong 태안군=Taean",
    "JEONBUK": "전주시>완산구=Wansan 전주시>덕진구=Deokjin 남원시=Namwon 김제시=Gimje 완주군=Wanju "
    "장수군=Jangsu 순창군=Sunchang 부안군=Buan",
    "JEONNAM": "순천시=Suncheon 곡성군=Gokseong 무안군=Muan 함평군=Hampyeong 완도군=Wando 진도군=Jindo",
    "GYEONGBUK": "포항시>남구=대잠동 포항시>북구=죽도동 영천시=Yeongcheon,Yongchon,Yeongcheon-si 상주시=Sangju 의성군=Uiseong "
    "영양군=Yeongyang 청도군=Cheongdo 고령군=Goryeong 성주군=Seongju 칠곡군=Waegwan 울진군=Uljin "
    "울릉군=Ulleung",
    "GYEONGNAM": "창원시>의창구=Uichang,사림동,Sarim-dong 창원시>성산구=상남동,Sangnam-dong,Sangnam 창원시>마산합포구=Masan 창원시>마산회원구=회원동,Hoewon-dong,Naeseo "
    "창원시>진해구=Jinhae 통영시=Tongyeong 사천시=Sacheon 의령군=Uiryeong 함안군=Haman 고성군=Goseong "
    "남해군=Namhae 산청군=Sancheong,Sanchong,Sancheong-eup 함양군=Hamyang 합천군=Hapcheon",
    "JEJU": "서귀포시=Seogwipo",
}
HINTS = {}
for _code, _text in _HINT_TEXT.items():
    for _token in _text.split():
        _place, _hints = _token.split("=")
        _city, _name = _place.split(">") if ">" in _place else ("", _place)
        HINTS[(_code, _city, _name)] = _hints.split(",")

# 지오코딩 자료가 옛 행정구역이라 admin 이름이 다른 곳: (시·도, 시, 구) → 받는 admin 이름들.
# 힌트 검색어 결과에만 쓴다 (예: 2026-07 인천 중구·동구 → 제물포구·영종구, 서구 → 서구·검단구).
ADMIN_ALIASES = {
    ("INCHEON", "", "제물포구"): ["중구", "동구"],
    ("INCHEON", "", "영종구"): ["중구"],
    ("INCHEON", "", "검단구"): ["서구"],
    ("INCHEON", "", "미추홀구"): ["미추홀구", "彌鄒忽區", "남구"],
    ("DAEGU", "", "군위군"): ["군위군"],
    ("GYEONGBUK", "포항시", "남구"): ["Nam-gu"],
    ("GYEONGBUK", "포항시", "북구"): ["Buk-gu"],
}

# 지오코딩 자료에 아직 없는 일반구 (부천 2024-01, 화성 2026-02): 힌트 결과는 admin2(시)만 맞으면 받는다
NEW_GU = {("GYEONGGI", "화성시", g) for g in ("만세구", "효행구", "병점구", "동탄구")} | {
    ("GYEONGGI", "부천시", g) for g in ("원미구", "소사구", "오정구")
}

# 일반구의 admin3은 로마자로 오는 곳이 많다 (전주시 Wansan-gu, 용인시 Suji-gu). 늘 받는다
ROMAN_GU = {
    "장안구": "Jangan-gu", "권선구": "Gwonseon-gu", "팔달구": "Paldal-gu", "영통구": "Yeongtong-gu",
    "덕양구": "Deogyang-gu", "일산동구": "Ilsandong-gu", "일산서구": "Ilsanseo-gu",
    "처인구": "Cheoin-gu", "기흥구": "Giheung-gu", "수지구": "Suji-gu",
    "수정구": "Sujeong-gu", "중원구": "Jungwon-gu", "분당구": "Bundang-gu",
    "원미구": "Wonmi-gu", "소사구": "Sosa-gu", "오정구": "Ojeong-gu",
    "상록구": "Sangnok-gu", "단원구": "Danwon-gu", "만안구": "Manan-gu", "동안구": "Dongan-gu",
    "상당구": "Sangdang-gu", "서원구": "Seowon-gu", "흥덕구": "Heungdeok-gu", "청원구": "Cheongwon-gu",
    "동남구": "Dongnam-gu", "서북구": "Seobuk-gu", "완산구": "Wansan-gu", "덕진구": "Deokjin-gu",
    "남구": "Nam-gu", "북구": "Buk-gu",
    "의창구": "Uichang-gu", "성산구": "Seongsan-gu", "마산합포구": "Masanhappo-gu",
    "마산회원구": "Masanhoewon-gu", "진해구": "Jinhae-gu",
}

# 지오코딩 자료에 아직 없는 화성시 일반구(2026-02): admin2 = 화성시이고 admin3(읍·면·동)이 이 구에 든 곳이면 받는다
HWASEONG_GU = {
    "만세구": "우정읍 향남읍 남양읍 마도면 송산면 서신면 팔탄면 장안면 양감면 새솔동",
    "효행구": "봉담읍 매송면 비봉면 정남면 기배동",
    "병점구": "진안동 병점1동 병점2동 반월동 화산동",
    "동탄구": "동탄1동 동탄2동 동탄3동 동탄4동 동탄5동 동탄6동 동탄7동 동탄8동 동탄9동",
}

# 이 admin1도 받는다 (지오코딩 자료가 옛 소속): 군위군은 2023-07 경북 → 대구
EXTRA_ADMIN1 = {("DAEGU", "", "군위군"): ["경상북도"]}

# 받지 않는 지형 (산·고개·능선): 날씨 좌표로 쓰기에 높다
BAD_FEATURES = {"MT", "MTS", "PK", "PKS", "HLL", "HLLS", "PASS", "RDGE", "SPUR", "CLF", "VAL"}


def stem(name):
    """끝의 시·군·구를 뗀 이름 (해운대구 → 해운대). 두 글자 이상일 때만"""
    if len(name) >= 3 and name[-1] in "시군구":
        return name[:-1]
    return None


def entries():
    out = []
    for code, text in DISTRICTS.items():
        for token in text.split():
            if ">" in token:
                city, gus = token.split(">")
                out.append((code, "", city))
                for gu in gus.split(","):
                    out.append((code, city, gu))
            else:
                out.append((code, "", token))
    return out


class Geo:
    def __init__(self, cache_path):
        self.cache_path = cache_path
        self.cache = {}
        self.calls = 0
        if cache_path:
            try:
                with open(cache_path, encoding="utf-8") as f:
                    self.cache = json.load(f)
            except FileNotFoundError:
                pass

    def search(self, name):
        if name in self.cache:
            return self.cache[name]
        url = API + "?" + urllib.parse.urlencode(
            {"name": name, "language": "ko", "count": COUNT, "format": "json"}
        )
        time.sleep(SLEEP)
        self.calls += 1
        try:
            with urllib.request.urlopen(url, timeout=15) as r:
                results = json.load(r).get("results", [])
        except Exception as e:  # noqa: BLE001 - 실패는 결과 없음으로 보고 다음 이름으로
            print(f"  ! 호출 실패 {name}: {e}", file=sys.stderr)
            return []
        self.cache[name] = results
        return results

    def save(self):
        if self.cache_path:
            with open(self.cache_path, "w", encoding="utf-8") as f:
                json.dump(self.cache, f, ensure_ascii=False)


def accept(key, r, hinted):
    """결과 r이 시·군·구 key 안의 곳인지"""
    code, city, name = key
    if r.get("country_code") != "KR":
        return False
    if r.get("feature_code") in BAD_FEATURES:
        return False
    admin1 = REGIONS[code][2] + EXTRA_ADMIN1.get(key, [])
    if r.get("admin1") not in admin1:
        return False
    a2, a3 = r.get("admin2"), r.get("admin3")
    names = [name] + ([stem(name)] if stem(name) else [])
    if hinted:
        names = names + ADMIN_ALIASES.get(key, [])
    if city:
        # 일반구: admin2 = 시, admin3 = 구 (지오코딩 자료에 구가 없으면 힌트 결과에 한해 시만 맞춰도 받는다)
        if a2 != city:
            return False
        if a3 is not None and (a3 in names or a3 == ROMAN_GU.get(name)):
            return True
        if r.get("name") == name:
            return True
        if city == "화성시" and a3 in HWASEONG_GU.get(name, "").split():
            return True
        # 힌트 결과인데 admin3이 비어 있으면 받는다 (admin3이 다른 구·읍·면·동이면 받지 않는다)
        return hinted and key in NEW_GU and a3 is None
    if r.get("name") in (name, stem(name)) and r.get("feature_code", "").startswith("PPLA"):
        return True
    return a2 in names or (r.get("name") == name and a2 is None)


def score(key, r):
    """작을수록 좋다: 행정 중심 → 인구 있는 곳 → 마을 → 그 밖, 같은 순위면 인구 많은 곳"""
    f = r.get("feature_code") or ""
    pop = r.get("population") or 0
    if f.startswith("PPLA") or f.startswith("ADM"):
        rank = 0
    elif f.startswith("PPL") and pop > 0:
        rank = 1
    elif f in ("PPL", "PPLL"):
        rank = 2
    elif f.startswith("PPL"):
        rank = 3
    else:
        rank = 4
    name_bonus = 0 if r.get("name") in (key[2], stem(key[2])) else 1
    return (rank, name_bonus, -pop)


def queries(key):
    code, city, name = key
    qs = [name]
    s = stem(name)
    if s:
        qs.append(s)
    hints = HINTS.get(key, [])
    return [(q, False) for q in qs] + [(q, True) for q in hints]


def pick(key, results, hinted):
    ok = [r for r in results if accept(key, r, hinted)]
    return min(ok, key=lambda r: score(key, r)) if ok else None


def build(geo):
    """1단계: 정식 이름 → 줄기 이름 → 힌트 지명 차례로 검색. 2단계(못 찾은 곳): 이번 실행에서 받은 모든 응답 중 그 시·군·구 안의 곳"""
    keys = entries()
    found = {}
    pool = []
    for key in keys:
        for i, (q, hinted) in enumerate(queries(key)):
            results = geo.search(q)
            pool.extend(results)
            best = pick(key, results, hinted)
            if best:
                found[key] = (q, i, best)
                break
    for key in keys:
        if key not in found:
            best = pick(key, pool, False)
            if best:
                found[key] = ("(다른 검색 결과)", 99, best)

    rows, report = [], {"first": [], "alternate": [], "approx": []}
    for key in keys:
        code, city, name = key
        _, region_full, _, (rlat, rlon) = REGIONS[code]
        full = " ".join(x for x in (region_full, city, name) if x)
        row = {"name": name, "fullName": full, "city": city or None, "regionCode": code}
        if key in found:
            q, i, r = found[key]
            row.update(
                lat=round(r["latitude"], 4),
                lon=round(r["longitude"], 4),
                approx=False,
                via=f"{q} → {r.get('name')} ({r.get('feature_code')})",
            )
            report["first" if i == 0 else "alternate"].append(f"{full} ← {row['via']}")
        else:
            row.update(lat=rlat, lon=rlon, approx=True, via=None)
            report["approx"].append(full)
        rows.append(row)
        print(("~ " if row["approx"] else "  ") + full + (f"  [{row['via']}]" if row["via"] else ""))
    return rows, report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--cache")
    ap.add_argument("--report", help="성공·대체·근사 목록을 JSON으로")
    args = ap.parse_args()

    geo = Geo(args.cache)
    try:
        rows, report = build(geo)
    finally:
        geo.save()
    data = {
        "version": time.strftime("%Y-%m-%d"),
        "source": "Open-Meteo geocoding (backend/scripts/build-districts.py)",
        "districts": rows,
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=1)
    print(
        f"\n전체 {len(rows)} · 첫 이름 {len(report['first'])} · 다른 이름 {len(report['alternate'])}"
        f" · 근사 {len(report['approx'])} · 이번 실행 호출 {geo.calls}회"
    )


if __name__ == "__main__":
    main()
