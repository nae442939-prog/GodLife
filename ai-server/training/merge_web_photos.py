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
              "vitamins-pills", "houseplant-watering", "cleaning-room",
              "park-footpath", "walking-trail-forest-path", "sidewalk-street-trees", "water-bottle",
              "bottled-water", "drinking-water-glass", "watering-can-plants"],
}
# '기타'로 받아 둔 사진(web/other · openimages/other 의 폴더)을 세부 라벨로 나눈다. 여기 없는 폴더는 other 그대로
SUB_LABELS = {
    "sunrise-morning": "wake_up", "alarm-clock": "wake_up", "made-bed-bedroom": "wake_up",
    "sunrise": "wake_up", "bed": "wake_up", "morning": "wake_up", "bedroom": "wake_up", "pillow": "wake_up",
    "walking-path-park": "walk", "park-footpath": "walk", "walking-trail-forest-path": "walk",
    "sidewalk-street-trees": "walk", "dog-walking": "walk", "walkway": "walk",
    "glass-of-water": "water", "drinking-water": "water", "water-bottle": "water", "drinking": "water",
    "bottled-water": "water", "drinking-water-glass": "water",
    "cleaning-room": "clean", "sink": "clean", "washing-machine": "clean", "cleanliness": "clean",
    "closet": "clean", "laundry": "clean", "laundry-room": "clean",
    "houseplant-watering": "plant", "houseplant": "plant", "flowerpot": "plant", "garden": "plant",
    "watering-can-plants": "plant",
}
# Open Images: 훑어보고 뺀 클래스 (폴더 이름) — 이유는 옆에
OPENIMAGES_SKIP: dict[str, str] = {
    "desk": "서랍장 · 사무실 가구 사진이 대부분",
    "student": "단체 사진이 대부분",
    "learning": "회의 · 발표 장면이 대부분",
    "walking": "등산 사진이 대부분이라 운동과 헷갈린다",
    "office-supplies": "프린터 · 상자 · 타자기 사진이 대부분",
    "paper": "종이접기 · 지폐 사진이 대부분",
    "blackboard": "카페 메뉴판이 대부분",
    "bookcase": "빈 책장 · 가구 사진이 대부분",
    "library": "사진이 거의 없다",
    "trail": "산악자전거 · 등산 사진이 대부분이라 운동과 헷갈린다",
    "pedestrian": "차가 다니는 도심 사진이 대부분",
    "street": "차가 다니는 도심 사진이 대부분",
    "bottle": "탄산음료 · 술 · 샴푸 병이 섞여 있다",
    "drinkware": "와인잔이 대부분",
    "mug": "커피 · 차 사진이 대부분",
}

MAX_PER_FOLDER = 120
# 사진이 적은 라벨(독서)을 채우려고 더 많이 쓰는 폴더
MAX_OVERRIDES = {"book": 400}


def copy(folder: Path, label: str, prefix: str) -> int:
    target = OUT / label
    target.mkdir(parents=True, exist_ok=True)
    photos = sorted(folder.glob("*.jpg"))[:MAX_OVERRIDES.get(folder.name, MAX_PER_FOLDER)]
    for photo in photos:
        shutil.copyfile(photo, target / f"{prefix}-{photo.name}")
    return len(photos)


def main() -> None:
    added: dict[str, int] = {}
    for label, queries in WIKIMEDIA_KEEP.items():
        for query in queries:
            target = SUB_LABELS.get(query, label) if label == "other" else label
            added[target] = added.get(target, 0) + copy(DATA / "web" / label / query, target, "wm")
    for label_dir in sorted(p for p in (DATA / "openimages").iterdir() if p.is_dir() and not p.name.startswith("_")):
        for class_dir in sorted(p for p in label_dir.iterdir() if p.is_dir()):
            if class_dir.name in OPENIMAGES_SKIP:
                print(f"  뺌 {label_dir.name}/{class_dir.name}: {OPENIMAGES_SKIP[class_dir.name]}")
                continue
            target = SUB_LABELS.get(class_dir.name, label_dir.name) if label_dir.name == "other" else label_dir.name
            added[target] = added.get(target, 0) + copy(class_dir, target, "oi")
    for label, count in sorted(added.items()):
        print(f"{label:10s} +{count}장 → 모두 {len(list((OUT / label).glob('*.jpg')))}장")


if __name__ == "__main__":
    main()
