"""공개 데이터셋(Stanford 40 Actions)에서 우리 라벨의 학습 사진을 골라 낸다.

    python training/prepare_dataset.py

- 받을 파일: http://vision.stanford.edu/Datasets/Stanford40_JPEGImages.zip (약 290MB, 연구·교육용)
  → training/data/Stanford40_JPEGImages.zip 에 두고 실행한다.
- 결과: training/data/dataset/{exercise,study,reading,cooking,walk,water,clean,plant,other}/*.jpg  (git 에 올리지 않는다)
- 직접 찍은 인증 사진을 같은 폴더에 더 넣고 train_local.py 를 다시 돌리면 그 사진까지 배운다.
"""

from __future__ import annotations

import random
import re
import zipfile
from pathlib import Path

DATA = Path(__file__).parent / "data"
ARCHIVE = DATA / "Stanford40_JPEGImages.zip"
OUT = DATA / "dataset"

# 우리 라벨(categories.ai_label · category_sub_types.ai_label) ← Stanford 40 의 행동 이름
ACTIONS = {
    "exercise": ["running", "climbing", "jumping", "riding_a_bike", "rowing_a_boat"],
    "study": ["writing_on_a_book", "using_a_computer", "writing_on_a_board", "looking_through_a_microscope"],
    "reading": ["reading"],
    "cooking": ["cooking", "cutting_vegetables"],
    # '기타' 카테고리의 세부 라벨 (category_sub_types.ai_label).
    # 일찍 일어나기(wake_up)는 여기 맞는 행동이 없어 웹 사진으로만 채운다 (merge_web_photos.py)
    "walk": ["walking_the_dog"],
    "water": ["drinking"],
    "clean": ["cleaning_the_floor", "washing_dishes"],
    "plant": ["gardening"],
    # 어느 라벨에도 들지 않는 생활 모습 (세부 종류 '그 밖')
    "other": [
        "brushing_teeth", "playing_guitar", "taking_photos", "fishing", "holding_an_umbrella",
        "watching_TV", "phoning",
    ],
}
MAX_PER_LABEL = 420  # 라벨끼리 장수가 너무 벌어지지 않게
SEED = 42


def main() -> None:
    if not ARCHIVE.is_file():
        raise SystemExit(f"먼저 받아 주세요: {ARCHIVE}")
    random.seed(SEED)
    with zipfile.ZipFile(ARCHIVE) as archive:
        by_action: dict[str, list[str]] = {}
        for name in archive.namelist():
            if name.lower().endswith(".jpg"):
                action = re.sub(r"_\d+\.jpg$", "", Path(name).name)
                by_action.setdefault(action, []).append(name)

        for label, actions in ACTIONS.items():
            quota = MAX_PER_LABEL // len(actions)
            picked: list[str] = []
            for action in actions:
                names = sorted(by_action.get(action, []))
                if not names:
                    raise SystemExit(f"압축 파일에 '{action}' 사진이 없어요.")
                random.shuffle(names)
                picked += names[:quota]
            folder = OUT / label
            folder.mkdir(parents=True, exist_ok=True)
            for name in picked:
                (folder / Path(name).name).write_bytes(archive.read(name))
            print(f"{label:10s} {len(picked)}장  ← {', '.join(actions)}")


if __name__ == "__main__":
    main()
