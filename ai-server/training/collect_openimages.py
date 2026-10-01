"""Open Images(구글 공개 데이터셋, 사람이 확인한 라벨이 붙은 Flickr 사진)에서 인증 사진과 비슷한 사진을 받는다.

    python training/collect_openimages.py

Stanford 40 Actions 는 '사람이 그 행동을 하는 모습'이 대부분이라, 실제 인증에서 많이 찍는
'물건·장면' 사진(책, 아령, 러닝화, 프라이팬, 필기 노트, 알람 시계 …)을 여기서 보탠다.

- 먼저 받아 둘 파일 (training/data/openimages/):
    classes.csv  ← https://storage.googleapis.com/openimages/v7/oidv7-class-descriptions.csv
    val.csv      ← https://storage.googleapis.com/openimages/v5/validation-annotations-human-imagelabels.csv
    test.csv     ← https://storage.googleapis.com/openimages/v5/test-annotations-human-imagelabels.csv
- 결과: training/data/openimages/{라벨}/{클래스}/*.jpg (긴 쪽 640px), 모아 보기는 _sheets/  (git 에 올리지 않는다)
- 받은 뒤 모아 보기를 훑어보고 쓸 만한 클래스만 dataset 에 합친다 (merge_web_photos.py)
"""

from __future__ import annotations

import csv
import io
import random
import re
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent / "data" / "openimages"
IMAGE_URL = "https://s3.amazonaws.com/open-images-dataset/{split}/{id}.jpg"
SEED = 42
MAX_SIDE = 640

# 우리 라벨 → (Open Images 클래스, 최대 장수). 앞에 적은 클래스부터 사진을 가져간다 (한 사진은 한 번만)
PICKS = {
    "exercise": [("Dumbbell", 60), ("Kettlebell", 60), ("Treadmill", 60), ("Stationary bicycle", 60),
                 ("Exercise machine", 60), ("Exercise equipment", 60), ("Barbell", 80), ("Running shoe", 65),
                 ("Jogging", 60), ("Yoga", 72), ("Gym", 110), ("Weight training", 100), ("Running", 100),
                 ("Exercise", 80), ("Physical fitness", 80)],
    "study": [("Homework", 60), ("Student", 72), ("Handwriting", 110), ("Pen", 80), ("Laptop", 110),
              ("Learning", 110), ("Desk", 60)],
    "reading": [("Reading", 70), ("Book", 400)],
    "cooking": [("Frying pan", 60), ("Cutting board", 60), ("Kitchen stove", 60), ("Cookware and bakeware", 91),
                ("Stir frying", 60), ("Cooking", 300), ("Meal", 90), ("Dish", 90)],
    "other": [("Alarm clock", 60), ("Drinking water", 60), ("Walking", 60), ("Sunrise", 135), ("Bed", 110),
              ("Houseplant", 110)],
}
# 이 클래스가 같이 붙은 사진은 다른 라벨에 쓰지 않는다 (예: 책이 찍힌 사진을 '기타'로 배우지 않게)
OWNERS = {name: label for label, picks in PICKS.items() for name, _ in picks}


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def download(job: tuple[str, str, Path]) -> bool:
    split, image_id, target = job
    if target.is_file():
        return True
    try:
        with urllib.request.urlopen(IMAGE_URL.format(split=split, id=image_id), timeout=60) as res:
            image = Image.open(io.BytesIO(res.read())).convert("RGB")
        image.thumbnail((MAX_SIDE, MAX_SIDE))
        image.save(target, "JPEG", quality=88)
        return True
    except Exception as e:  # 한 장 실패는 건너뛴다
        print("   건너뜀:", type(e).__name__, image_id)
        return False


def sheet(folder: Path, target: Path) -> None:
    """클래스 하나의 사진을 한 장에 모아 훑어볼 수 있게"""
    files = sorted(folder.glob("*.jpg"))[:120]
    if not files:
        return
    cols, cell = 12, 110
    rows = (len(files) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * cell, rows * cell), "white")
    for i, f in enumerate(files):
        im = Image.open(f).convert("RGB")
        im.thumbnail((cell, cell))
        canvas.paste(im, ((i % cols) * cell, (i // cols) * cell))
    canvas.save(target, "JPEG", quality=80)


def main() -> None:
    random.seed(SEED)
    names = {row[0]: row[1] for row in csv.reader(open(ROOT / "classes.csv", encoding="utf-8"))}
    # 클래스 이름 → [(split, 사진 id)], 사진 id → 그 사진에 붙은 우리 라벨들
    by_class: dict[str, list[tuple[str, str]]] = {}
    labels_of: dict[str, set[str]] = {}
    for split, file in (("validation", "val.csv"), ("test", "test.csv")):
        for row in csv.DictReader(open(ROOT / file)):
            name = names.get(row["LabelName"])
            if row["Confidence"] == "1" and name in OWNERS:
                by_class.setdefault(name, []).append((split, row["ImageID"]))
                labels_of.setdefault(row["ImageID"], set()).add(OWNERS[name])

    (ROOT / "_sheets").mkdir(exist_ok=True)
    taken: set[str] = set()
    for label, picks in PICKS.items():
        for name, limit in picks:
            # 다른 라벨의 클래스도 같이 붙은 사진은 헷갈리므로 뺀다
            candidates = sorted(c for c in by_class.get(name, []) if c[1] not in taken and labels_of[c[1]] == {label})
            random.shuffle(candidates)
            chosen = candidates[:limit]
            taken.update(image_id for _, image_id in chosen)
            folder = ROOT / label / slug(name)
            folder.mkdir(parents=True, exist_ok=True)
            with ThreadPoolExecutor(max_workers=8) as pool:
                saved = sum(pool.map(download, [(s, i, folder / f"oi-{i}.jpg") for s, i in chosen]))
            sheet(folder, ROOT / "_sheets" / f"{label}__{slug(name)}.jpg")
            print(f"{label}/{slug(name)}: {saved}장", flush=True)


if __name__ == "__main__":
    main()
