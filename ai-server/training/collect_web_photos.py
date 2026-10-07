"""위키미디어 공용(Wikimedia Commons)에서 인증 사진과 비슷한 사진을 검색해 받는다. (자유 이용 허락 사진, API 키 필요 없음)

    python training/collect_web_photos.py

Stanford 40 Actions 는 '사람이 그 행동을 하는 모습'이 대부분이라, 실제 인증에서 많이 찍는
'물건·장면' 사진(펼친 책, 러닝화, 아령, 냄비, 필기 노트 …)을 여기서 보탠다.

- 결과: training/data/web/{라벨}/{검색어}/*.jpg  (git 에 올리지 않는다)
- 받은 뒤 검색어별로 훑어보고(training/data/web/_sheets/*.jpg) 쓸 만한 검색어만 dataset 에 합친다 (merge_web_photos.py)
"""

from __future__ import annotations

import io
import json
import re
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

OUT = Path(__file__).parent / "data" / "web"
API = "https://commons.wikimedia.org/w/api.php"
HEADERS = {"User-Agent": "GodLifeBootcampProject/1.0 (educational image classifier training)"}
PER_QUERY = 70
WIDTH = 640

# 라벨 → 검색어 (실제 인증에서 찍을 법한 장면)
QUERIES = {
    "exercise": ["treadmill gym", "dumbbells", "yoga mat exercise", "running shoes", "jogging park",
                 "push-up exercise", "gym interior equipment", "exercise bike", "swimming pool lane", "jump rope"],
    "study": ["handwritten notes notebook", "student studying desk", "textbook and notebook", "laptop on desk study",
              "homework", "math homework paper", "library study desk", "flashcards", "open laptop code screen"],
    "reading": ["open book pages", "person reading a book", "book on table", "reading novel", "e-reader",
                "stack of books", "book and coffee", "paperback book"],
    "cooking": ["cooking pan stove", "homemade meal plate", "chopping vegetables cutting board", "frying pan food",
                "kitchen cooking pot", "home cooking", "baking dough kitchen", "salad bowl homemade"],
    "other": ["sunrise morning", "alarm clock", "glass of water", "made bed bedroom", "walking path park",
              "vitamins pills", "meditation", "houseplant watering", "cleaning room", "skincare products",
              "bullet journal planner",
              # '기타'의 세부 라벨(산책 · 물 마시기 · 청소/정리 · 식물 가꾸기)에 보탤 사진
              "park footpath", "walking trail forest path", "sidewalk street trees", "water bottle",
              "tumbler cup", "vacuum cleaner", "tidy living room", "potted plant windowsill",
              "bottled water", "drinking water glass", "water jug glass", "reusable water bottle",
              "mopping floor", "watering can plants"],
}


def call(params: dict) -> dict:
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as res:
        return json.load(res)


def search(query: str) -> list[str]:
    """검색어에 맞는 사진(JPG)의 640px 미리보기 주소"""
    data = call({
        "action": "query", "generator": "search", "gsrnamespace": 6, "gsrlimit": PER_QUERY,
        "gsrsearch": f"{query} filetype:bitmap filemime:image/jpeg",
        "prop": "imageinfo", "iiprop": "url|size", "iiurlwidth": WIDTH,
    })
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 0))
    urls = []
    for page in pages:
        info = (page.get("imageinfo") or [{}])[0]
        # 너무 작은 그림 · 지나치게 길쭉한 그림(파노라마, 문서 스캔)은 뺀다
        w, h = info.get("width", 0), info.get("height", 0)
        thumb = info.get("thumburl", "")
        if min(w, h) >= 400 and max(w, h) / max(1, min(w, h)) <= 2.2 and "/thumb/" in thumb:
            urls.append(thumb)
    return urls


def download(url: str, target: Path) -> bool:
    if target.is_file():
        return True
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as res:
            raw = res.read()
        Image.open(io.BytesIO(raw)).convert("RGB").save(target, "JPEG", quality=88)
        return True
    except Exception as e:  # 한 장 실패는 건너뛴다
        print("   건너뜀:", type(e).__name__, url[-60:])
        return False


def sheet(folder: Path, target: Path) -> None:
    """검색어 하나의 사진을 한 장에 모아 훑어볼 수 있게"""
    files = sorted(folder.glob("*.jpg"))[:70]
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
    (OUT / "_sheets").mkdir(parents=True, exist_ok=True)
    for label, queries in QUERIES.items():
        for query in queries:
            slug = re.sub(r"[^a-z0-9]+", "-", query.lower()).strip("-")
            folder = OUT / label / slug
            done = OUT / "_sheets" / f"{label}__{slug}.jpg"
            if done.is_file():
                print(f"{label}/{slug}: 이미 있음")
                continue
            folder.mkdir(parents=True, exist_ok=True)
            try:
                urls = search(query)
            except Exception as e:
                print(f"{label}/{slug}: 검색 실패 {e}")
                continue
            # 한 번에 4장씩만 받는다 (서버에 부담을 주지 않게)
            with ThreadPoolExecutor(max_workers=4) as pool:
                saved = sum(pool.map(download, urls, [folder / f"{slug}-{i:03d}.jpg" for i in range(len(urls))]))
            sheet(folder, done)
            print(f"{label}/{slug}: {saved}장", flush=True)
            time.sleep(1)


if __name__ == "__main__":
    main()
