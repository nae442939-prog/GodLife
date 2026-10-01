"""받아 둔 웹 사진 중 쓸 만한 것만 학습 폴더(dataset)에 합친다.

    python training/merge_web_photos.py

- Open Images (collect_openimages.py): 사람이 라벨을 확인한 사진이라 대부분 쓴다. 훑어보고 뺄 클래스만 OPENIMAGES_SKIP 에 적는다.
- 위키미디어 공용 (collect_web_photos.py): 검색 결과에 스캔 문서 · 그림 · 같은 사진 묶음이 많아서,
  모아 보기(_sheets)를 훑어보고 괜찮았던 검색어만 WIKIMEDIA_KEEP 에 적어 쓴다.
"""

from __future__ import annotations

import shutil
from pathlib import Path

DATA = Path(__file__).parent / "data"
OUT = DATA / "dataset"

# 위키미디어: 훑어보고 고른 검색어 (공부 쪽은 옛 공책 스캔뿐이라 전부 뺐다)
WIKIMEDIA_KEEP = {
    "exercise": ["dumbbells", "treadmill-gym", "running-shoes", "yoga-mat-exercise", "exercise-bike",
                 "swimming-pool-lane"],
    "study": [],
    "reading": ["person-reading-a-book", "stack-of-books"],
    "cooking": ["cooking-pan-stove", "homemade-meal-plate", "frying-pan-food", "kitchen-cooking-pot",
                "salad-bowl-homemade", "home-cooking"],
    "other": ["sunrise-morning", "alarm-clock", "glass-of-water", "made-bed-bedroom", "walking-path-park",
              "vitamins-pills", "houseplant-watering"],
}
# Open Images: 훑어보고 뺀 클래스 (폴더 이름) — 이유는 옆에
OPENIMAGES_SKIP: dict[str, str] = {
    "desk": "서랍장 · 사무실 가구 사진이 대부분",
    "student": "단체 사진이 대부분",
    "learning": "회의 · 발표 장면이 대부분",
    "walking": "등산 사진이 대부분이라 운동과 헷갈린다",
}

MAX_PER_FOLDER = 120


def copy(folder: Path, label: str, prefix: str) -> int:
    target = OUT / label
    target.mkdir(parents=True, exist_ok=True)
    photos = sorted(folder.glob("*.jpg"))[:MAX_PER_FOLDER]
    for photo in photos:
        shutil.copyfile(photo, target / f"{prefix}-{photo.name}")
    return len(photos)


def main() -> None:
    added: dict[str, int] = {}
    for label, queries in WIKIMEDIA_KEEP.items():
        for query in queries:
            added[label] = added.get(label, 0) + copy(DATA / "web" / label / query, label, "wm")
    for label_dir in sorted(p for p in (DATA / "openimages").iterdir() if p.is_dir() and not p.name.startswith("_")):
        for class_dir in sorted(p for p in label_dir.iterdir() if p.is_dir()):
            if class_dir.name in OPENIMAGES_SKIP:
                print(f"  뺌 {label_dir.name}/{class_dir.name}: {OPENIMAGES_SKIP[class_dir.name]}")
                continue
            added[label_dir.name] = added.get(label_dir.name, 0) + copy(class_dir, label_dir.name, "oi")
    for label, count in sorted(added.items()):
        print(f"{label:10s} +{count}장 → 모두 {len(list((OUT / label).glob('*.jpg')))}장")


if __name__ == "__main__":
    main()
