"""
갓생살기 이미지 분류 학습 데이터 수집/정리 스크립트

두 가지 모드가 있습니다.

1) openimages  : Open Images(구글 공개 데이터셋, 라이선스 CC BY 2.0)에서 라벨별 사진을 받아 정리
   python collect_images.py openimages --per-label 150

2) folder      : 내가 직접 찍은 사진 / AI Hub 등에서 받은 사진 폴더를 같은 규칙으로 정리
   python collect_images.py folder --src C:\\GodLife\\ai-server\\training\\raw\\my_photos
   (src 아래에 exercise, study ... 처럼 라벨 이름 폴더를 만들고 사진을 넣어 두면 됩니다)

공통 규칙
- 짧은 쪽 400px 미만 제외
- 세로/가로 비율이 2.5배를 넘는 아주 긴 이미지 제외
- 같은/거의 같은 사진 중복 제거 (perceptual hash)
- 긴 쪽 640px로 줄여서 JPG 저장, 001.jpg 002.jpg ... 순서로 이어서 번호 붙임

필요한 패키지 (처음 한 번만):
   pip install pillow imagehash fiftyone
"""

import argparse
import csv
import sys
from pathlib import Path

from PIL import Image, ImageOps
import imagehash

LABELS = ["exercise", "study", "reading", "cooking", "wake_up", "walk", "water", "clean", "plant"]

# Open Images 클래스 이름 -> 우리 라벨
# (Open Images는 '물건' 단위 라벨이라 walk / clean / wake_up은 비슷한 물건으로 대신합니다)
OPENIMAGES_CLASSES = {
    "exercise": ["Dumbbell", "Treadmill", "Stationary bicycle"],
    "study":    ["Pencil case", "Pen", "Desk"],
    "reading":  ["Book"],
    "cooking":  ["Frying pan", "Wok", "Cutting board", "Kitchen knife"],
    "wake_up":  ["Alarm clock", "Bed", "Pillow"],
    "walk":     ["Dog"],
    "water":    ["Bottle"],
    "clean":    ["Washing machine", "Sink", "Towel"],
    "plant":    ["Houseplant", "Flowerpot"],
}

DEFAULT_OUT = r"C:\GodLife\ai-server\training\data"

MIN_SHORT_SIDE = 400
MAX_ASPECT = 2.5
LONG_SIDE = 640
HASH_DISTANCE = 5  # 이 값 이하로 비슷하면 중복으로 봄
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


class Saver:
    """규칙 검사 + 리사이즈 + 저장 + 중복 체크를 한 곳에서 처리"""

    def __init__(self, out_root: Path):
        self.out_root = out_root
        self.hashes = []  # 모든 라벨 공통 (라벨끼리도 같은 사진이 안 들어가게)
        self.counts = {}
        self.skipped = {"small": 0, "long": 0, "dup": 0, "broken": 0}
        # 이미 저장된 사진의 해시도 미리 읽어 둠 (다시 실행해도 중복 안 생기게)
        for label in LABELS:
            d = out_root / label
            if d.exists():
                for f in d.glob("*.jpg"):
                    try:
                        with Image.open(f) as im:
                            self.hashes.append(imagehash.phash(im))
                    except Exception:
                        pass

    def next_path(self, label: str) -> Path:
        d = self.out_root / label
        d.mkdir(parents=True, exist_ok=True)
        nums = [int(p.stem) for p in d.glob("*.jpg") if p.stem.isdigit()]
        n = max(nums, default=0) + 1
        return d / f"{n:03d}.jpg"

    def save(self, src: Path, label: str):
        """저장하면 저장된 경로, 건너뛰면 None"""
        try:
            with Image.open(src) as im:
                im = ImageOps.exif_transpose(im)  # 폰 사진 회전 정보 반영
                im = im.convert("RGB")
        except Exception:
            self.skipped["broken"] += 1
            return None

        w, h = im.size
        if min(w, h) < MIN_SHORT_SIDE:
            self.skipped["small"] += 1
            return None
        if max(w, h) / min(w, h) > MAX_ASPECT:
            self.skipped["long"] += 1
            return None

        ph = imagehash.phash(im)
        if any(ph - old <= HASH_DISTANCE for old in self.hashes):
            self.skipped["dup"] += 1
            return None

        scale = LONG_SIDE / max(w, h)
        if scale < 1:
            im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)

        dst = self.next_path(label)
        im.save(dst, "JPEG", quality=90)
        self.hashes.append(ph)
        self.counts[label] = self.counts.get(label, 0) + 1
        return dst

    def report(self):
        lines = ["===== 라벨별 저장 결과 ====="]
        for label in LABELS:
            d = self.out_root / label
            total = len(list(d.glob("*.jpg"))) if d.exists() else 0
            lines.append(f"{label:10s} 이번에 +{self.counts.get(label, 0):4d}장   (폴더 전체 {total}장)")
        s = self.skipped
        lines.append(f"제외: 작은 사진 {s['small']} / 너무 긴 사진 {s['long']} / 중복 {s['dup']} / 깨진 파일 {s['broken']}")
        lines.append("※ 얼굴이 크게 나온 사진, 글자·콜라주 사진은 자동으로 못 거르니 폴더를 한 번 훑어보며 지워 주세요.")
        text = "\n".join(lines)
        print("\n" + text)
        self.out_root.mkdir(parents=True, exist_ok=True)
        (self.out_root / "report.txt").write_text(text, encoding="utf-8")


def run_openimages(args):
    try:
        import fiftyone.zoo as foz
    except ImportError:
        sys.exit("fiftyone이 없습니다. 먼저 실행: pip install fiftyone")

    out_root = Path(args.out) / "openimages"
    saver = Saver(out_root)
    credits_path = out_root / "credits.csv"
    out_root.mkdir(parents=True, exist_ok=True)
    new_file = not credits_path.exists()

    with open(credits_path, "a", newline="", encoding="utf-8-sig") as cf:
        writer = csv.writer(cf)
        if new_file:
            writer.writerow(["label", "saved_file", "openimages_id", "class", "license"])

        for label in args.labels:
            classes = OPENIMAGES_CLASSES[label]
            # 제외 규칙에 걸리는 사진이 있으니 넉넉하게 받음
            want = int(args.per_label * 1.6)
            print(f"\n[{label}] {classes} 에서 최대 {want}장 받는 중...")
            ds = foz.load_zoo_dataset(
                "open-images-v7",
                split="train",
                label_types=["detections"],
                classes=classes,
                max_samples=want,
                shuffle=True,
                seed=51,
                dataset_name=f"godlife-{label}",
                drop_existing_dataset=True,
            )
            saved = 0
            for sample in ds:
                if saved >= args.per_label:
                    break
                dst = saver.save(Path(sample.filepath), label)
                if dst:
                    saved += 1
                    oi_id = Path(sample.filepath).stem
                    dets = sample["ground_truth"].detections if sample["ground_truth"] else []
                    cls = ";".join(sorted({d.label for d in dets}))
                    writer.writerow([label, dst.name, oi_id, cls, "CC BY 2.0 (Open Images)"])
            print(f"[{label}] {saved}장 저장")

    saver.report()
    print(f"\n출처 기록: {credits_path}")


def run_folder(args):
    src_root = Path(args.src)
    if not src_root.exists():
        sys.exit(f"폴더가 없습니다: {src_root}")
    out_root = Path(args.out) / args.name
    saver = Saver(out_root)
    for label in args.labels:
        d = src_root / label
        if not d.exists():
            continue
        files = sorted(p for p in d.rglob("*") if p.suffix.lower() in IMAGE_EXTS)
        print(f"[{label}] 원본 {len(files)}장 처리 중...")
        for f in files:
            saver.save(f, label)
    saver.report()


def main():
    p = argparse.ArgumentParser(description="갓생살기 학습 이미지 수집/정리")
    sub = p.add_subparsers(dest="mode", required=True)

    a = sub.add_parser("openimages", help="Open Images에서 받아서 정리")
    a.add_argument("--per-label", type=int, default=150)
    a.add_argument("--out", default=DEFAULT_OUT)
    a.add_argument("--labels", nargs="+", default=LABELS, choices=LABELS)

    b = sub.add_parser("folder", help="내 사진 폴더를 정리")
    b.add_argument("--src", required=True, help="라벨 이름 폴더들이 들어 있는 원본 폴더")
    b.add_argument("--name", default="my_photos", help=r"저장될 하위 폴더 이름 (data\<name>\<라벨>)")
    b.add_argument("--out", default=DEFAULT_OUT)
    b.add_argument("--labels", nargs="+", default=LABELS, choices=LABELS)

    args = p.parse_args()
    if args.mode == "openimages":
        run_openimages(args)
    else:
        run_folder(args)


if __name__ == "__main__":
    main()
