"""네이버 이미지 검색 API 로 '실제 인증 사진처럼 폰으로 찍은' 사진을 검색해 받는다.

    python training/collect_naver_photos.py

공개 데이터셋 · 위키미디어 사진은 잘 찍은 사진이 대부분이라, 블로그 · 카페에 올라온
인증샷(오운완, 공부 인증, 미라클모닝 …)을 여기서 보탠다. 직접 찍은 사진 대신 쓴다.

- 키: 네이버 개발자센터에서 사용 API 가 '검색'인 애플리케이션을 따로 등록한다. (소셜 로그인 애플리케이션과 다른 키)
  환경변수 NAVER_SEARCH_CLIENT_ID · NAVER_SEARCH_CLIENT_SECRET, 없으면 backend/.env 의 같은 이름을 읽는다.
- 결과: training/data/naver/{라벨}/{검색어}/*.jpg  (git 에 올리지 않는다. 학습에만 쓰고 다시 배포하지 않는다)
- 받은 뒤 검색어별로 훑어보고(training/data/naver/_sheets/*.jpg) 쓸 만한 검색어만 dataset 에 합친다 (merge_web_photos.py)
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent
OUT = ROOT / "data" / "naver"
API = "https://openapi.naver.com/v1/search/image"
PER_QUERY = 100  # API 한 번에 받을 수 있는 최대
SIZE = 640
BROWSER = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GodLifeBootcampProject/1.0"}

# 라벨 → 검색어 (사람들이 인증샷을 올릴 때 쓰는 말)
QUERIES = {
    "exercise": ["오운완 인증", "헬스장 인증샷", "러닝 인증 기록", "홈트 인증", "런닝머신 인증",
                 "덤벨 운동 인증", "필라테스 인증샷", "오수완 수영 인증", "요가매트 홈트"],
    "study": ["공부 인증", "공스타그램 책상", "스터디플래너 인증", "필기 노트 공부 인증", "독서실 책상 공부",
              "인강 공부 인증", "문제집 풀이 인증", "열공 인증샷"],
    "reading": ["독서 인증", "책 읽기 인증샷", "북스타그램", "독서 기록 책 펼친 사진", "필사 인증",
                "오늘 읽은 책 인증"],
    "cooking": ["집밥 인증", "요리 인증샷", "도시락 싸기 인증", "자취 요리 일상", "식단 인증 직접 만든",
                "밀프렙 만들기"],
    "wake_up": ["미라클모닝 인증", "기상 인증", "새벽 기상 인증샷", "이불 정리 인증", "아침 기상 타임스탬프"],
    "walk": ["산책 인증", "만보 걷기 인증", "저녁 산책 인증샷", "강아지 산책 인증", "공원 산책길 일상"],
    "water": ["물 마시기 인증", "물 2리터 인증", "텀블러 물 인증", "하루 물 2L 챌린지"],
    "clean": ["청소 인증", "방 정리 인증샷", "설거지 인증", "정리정돈 비포 애프터", "빨래 개기 인증"],
    "plant": ["식물 키우기 인증", "반려식물 물주기", "식집사 일상", "화분 물주기 인증", "베란다 텃밭 일상"],
}


def keys() -> tuple[str, str]:
    found = {name: os.environ.get(name, "") for name in ("NAVER_SEARCH_CLIENT_ID", "NAVER_SEARCH_CLIENT_SECRET")}
    env = ROOT.parent.parent / "backend" / ".env"
    if not all(found.values()) and env.is_file():
        for line in env.read_text(encoding="utf-8").splitlines():
            name, _, value = line.partition("=")
            if name.strip() in found and not found[name.strip()]:
                found[name.strip()] = value.strip().strip('"')
    if not all(found.values()):
        raise SystemExit("NAVER_SEARCH_CLIENT_ID · NAVER_SEARCH_CLIENT_SECRET 이 없다.")
    return found["NAVER_SEARCH_CLIENT_ID"], found["NAVER_SEARCH_CLIENT_SECRET"]


def search(query: str, client: tuple[str, str]) -> list[str]:
    """검색어에 맞는 사진의 원본 주소"""
    url = API + "?" + urllib.parse.urlencode({"query": query, "display": PER_QUERY, "sort": "sim", "filter": "large"})
    request = urllib.request.Request(url, headers={"X-Naver-Client-Id": client[0], "X-Naver-Client-Secret": client[1]})
    with urllib.request.urlopen(request, timeout=30) as res:
        items = json.load(res).get("items", [])
    urls = []
    for item in items:
        # 지나치게 길쭉한 그림(이어 붙인 캡처, 배너)은 뺀다
        w, h = int(item.get("sizewidth") or 0), int(item.get("sizeheight") or 0)
        if min(w, h) >= 400 and max(w, h) / max(1, min(w, h)) <= 2.2:
            urls.append(item["link"])
    return urls


def download(url: str, folder: Path) -> bool:
    # 같은 사진이 여러 검색어에 나오므로 주소로 이름을 지어 한 번만 받는다
    target = folder / (hashlib.sha1(url.encode()).hexdigest()[:16] + ".jpg")
    if target.is_file():
        return True
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=BROWSER), timeout=20) as res:
            raw = res.read(12_000_000)
        im = Image.open(io.BytesIO(raw)).convert("RGB")
        im.thumbnail((SIZE, SIZE))
        im.save(target, "JPEG", quality=88)
        return True
    except Exception:  # 한 장 실패(지워진 글, 막힌 주소)는 건너뛴다
        return False


def sheet(folder: Path, target: Path) -> None:
    """검색어 하나의 사진을 한 장에 모아 훑어볼 수 있게"""
    files = sorted(folder.glob("*.jpg"))[:100]
    if not files:
        return
    cols, cell = 10, 128
    rows = (len(files) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * cell, rows * cell), "white")
    for i, f in enumerate(files):
        im = Image.open(f).convert("RGB")
        im.thumbnail((cell, cell))
        canvas.paste(im, ((i % cols) * cell, (i // cols) * cell))
    canvas.save(target, "JPEG", quality=80)


def main() -> None:
    client = keys()
    (OUT / "_sheets").mkdir(parents=True, exist_ok=True)
    seen: set[str] = set()
    for label, queries in QUERIES.items():
        for n, query in enumerate(queries):
            slug = f"q{n:02d}-" + query.replace(" ", "-")
            folder = OUT / label / slug
            done = OUT / "_sheets" / f"{label}__{slug}.jpg"
            if done.is_file():
                print(f"{label}/{slug}: 이미 있음")
                continue
            try:
                urls = [u for u in search(query, client) if u not in seen]
            except urllib.error.HTTPError as e:
                raise SystemExit(f"검색 실패 {e.code}: {e.read().decode('utf-8', 'replace')}")
            seen.update(urls)
            folder.mkdir(parents=True, exist_ok=True)
            with ThreadPoolExecutor(max_workers=4) as pool:
                saved = sum(pool.map(download, urls, [folder] * len(urls)))
            sheet(folder, done)
            print(f"{label}/{slug}: {saved}장", flush=True)
            time.sleep(0.5)


if __name__ == "__main__":
    main()
