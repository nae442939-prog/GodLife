"""개발용 더미 상품(db/dev-shop.sql)에 사진을 붙인다. 운영 DB 에는 쓰지 않는다.

사진은 위키미디어 공용(Wikimedia Commons)의 자유 라이선스 사진만 쓴다 (CC0 · 퍼블릭 도메인 · CC BY · CC BY-SA).
어떤 사진을 쓸지는 사람이 직접 보고 골라 아래 PHOTOS 에 적어 두었다 (상표가 크게 보이는 사진 · 얼굴이 드러난 사진은 뺐다).
맞는 자유 라이선스 사진을 찾지 못한 상품(러닝 암밴드 · 헬스 스트랩)은 사진 없이 기본 그림으로 둔다.

하는 일
  1. 공용 API 로 사진 주소 · 작가 · 라이선스를 다시 확인한다 (자유 라이선스가 아니면 건너뛴다)
  2. 사진을 받아 긴 쪽 1200px JPEG 로 다시 저장한다 → backend/uploads/product/{상품 id}/{uuid}.jpg (촬영 정보는 지워진다)
  3. 상품의 image_url 을 바꾸고, 저작자 표시를 상품 설명 끝에 붙이는 SQL 을 표준 출력으로 낸다
  4. 출처 목록을 db/dev-shop-photos.md 에 쓴다 (CC BY 는 저작자 표시가 조건이다)

쓰는 법 (저장소 맨 위 폴더에서, Pillow 가 깔린 파이썬으로 — ai-server 의 venv 를 쓰면 된다)
  mysql -u godlife_user -p -N godlife -e "SELECT id, name FROM products" > products.tsv
  ai-server/venv/Scripts/python db/dev-shop-photos.py products.tsv > photos.sql
  mysql -u godlife_user -p godlife < photos.sql
"""
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPLOADS = ROOT / "backend" / "uploads" / "product"
CREDITS = ROOT / "db" / "dev-shop-photos.md"
API = "https://commons.wikimedia.org/w/api.php"
UA = "GodLifePortfolio/1.0 (student portfolio project; dev sample product photos)"
FREE = ("cc0", "public domain", "pd", "cc by")
MAX_SIDE = 1200
CREDIT_MARK = "\n\n사진: "

# 상품 이름 → 위키미디어 공용 파일
PHOTOS = {
    "미끄럼 방지 요가 매트 6mm": "File:A woman prepares for her yoga routine.jpg",
    "보틀 1L (눈금 표시)": "File:Metal Water Bottles.jpeg",
    "갓생 플래너 (6개월)": "File:Desk with notebook pens and glasses.jpg",
    "젤 펜 5색 세트": "File:Colored-pencils-402546 640.jpg",
    "독서 기록 노트": "File:Person writing in a notebook while sitting at a desk.jpg",
    "무드등 겸용 기상 알람 시계": "File:Old alarm clock on the bookshelf - 50233824038.jpg",
    "밀프렙 도시락 용기 3개 세트": "File:Meal prep glass container (45165270525).jpg",
    "헬스장 1일 이용권": "File:Marines' voices echo, prompt new gym hours 150927-M-AI083-067.jpg",
    "스터디카페 4시간 이용권": "File:Reading room, Ground floor, VW Library, Berlin.jpg",
    "요가 클래스 1회 체험권": "File:SABDA Studio immersive room Barcelona.jpg",
    "도서 상품권 5,000원": "File:Stack of multicolored books on a table.jpg",
    "도서 상품권 10,000원": "File:Old Books 01.JPG",
}


def get(url):
    """요청이 너무 잦다고(429) 거절되면 쉬었다가 다시 한다."""
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as res:
                return res.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 4:
                raise
            time.sleep(20 * (attempt + 1))


def info(title):
    params = {
        "action": "query", "format": "json", "titles": title, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": MAX_SIDE,
    }
    pages = json.loads(get(API + "?" + urllib.parse.urlencode(params)))["query"]["pages"]
    image = next(iter(pages.values()))["imageinfo"][0]
    meta = image["extmetadata"]
    artist = re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "")).strip()
    return {
        "url": image.get("thumburl") or image["url"],
        "page": image["descriptionurl"],
        "license": meta.get("LicenseShortName", {}).get("value", ""),
        "artist": re.sub(r"\s+", " ", artist) or "알 수 없음",
    }


def sql(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    ids = {}
    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
        product_id, _, name = line.partition("\t")
        ids[name.strip()] = product_id.strip()

    credits = [
        "# 개발용 더미 상품 사진 출처",
        "",
        "`db/dev-shop-photos.py` 가 붙인 상품 사진의 출처다. 모두 위키미디어 공용(Wikimedia Commons)의 자유 라이선스 사진이고,",
        "긴 쪽 1200px 로 줄여 다시 저장했다 (그 밖의 수정 없음). 포트폴리오 시연용 더미 데이터에만 쓴다.",
        "",
        "| 상품 | 작가 | 라이선스 | 원본 |",
        "|---|---|---|---|",
    ]
    print("SET NAMES utf8mb4;")
    for name, title in PHOTOS.items():
        if name not in ids:
            print(f"-- 건너뜀 (상품 없음): {name}", file=sys.stderr)
            continue
        meta = info(title)
        if not meta["license"].lower().startswith(FREE):
            print(f"-- 건너뜀 (자유 라이선스 아님: {meta['license']}): {name}", file=sys.stderr)
            continue
        image = Image.open(io.BytesIO(get(meta["url"]))).convert("RGB")
        image.thumbnail((MAX_SIDE, MAX_SIDE))
        folder = UPLOADS / ids[name]
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.jpg"):
            old.unlink()
        file_name = f"{uuid.uuid4()}.jpg"
        image.save(folder / file_name, "JPEG", quality=85)

        credit = f"{meta['artist']} / {meta['license']} (Wikimedia Commons)"
        # 다시 실행해도 저작자 표시가 두 번 붙지 않게, 붙어 있던 표시를 떼고 새로 붙인다
        print(
            "UPDATE products SET image_url = {url}, "
            "description = CONCAT(SUBSTRING_INDEX(description, {mark}, 1), {mark}, {credit}) WHERE id = {id};".format(
                url=sql(f"/api/shop/images/{ids[name]}/{file_name}"), mark=sql(CREDIT_MARK), credit=sql(credit),
                id=int(ids[name]),
            )
        )
        credits.append(f"| {name} | {meta['artist']} | {meta['license']} | {meta['page']} |")
        print(f"-- {name}: {meta['license']} / {meta['artist']}", file=sys.stderr)
        time.sleep(3)  # 공용 서버에 부담을 주지 않게 천천히 받는다

    credits += [
        "",
        "사진이 없는 상품(러닝 암밴드 · 손목 보호 헬스 스트랩)은 맞는 자유 라이선스 사진을 찾지 못해 기본 그림으로 둔다.",
        "",
    ]
    CREDITS.write_text("\n".join(credits), encoding="utf-8")


if __name__ == "__main__":
    main()
