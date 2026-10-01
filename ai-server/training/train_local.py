"""인증 사진 분류 모델을 내 컴퓨터(CPU)에서 전이학습으로 학습한다.

    python training/train_local.py

- 사진: training/data/dataset/{exercise,study,reading,cooking,other}/  (prepare_dataset.py · merge_web_photos.py 가 만든다)
- ImageNet 으로 사전학습된 MobileNetV2 에서 시작해 1단계 분류층만 → 2단계 뒤쪽 블록 4개까지 풀어 미세 조정한다.
  (Colab 노트북 godlife_finetune.ipynb 와 같은 방법)
- CPU 에서도 빨리 끝나게, 학습하지 않는 앞부분(얼린 블록)의 출력은 사진마다 한 번만 계산해 두고
  뒤쪽 블록 4개 + 분류층만 반복해서 학습한다. 증강은 사진마다 몇 가지 모습(views)을 미리 만들어 둔다.
- 결과: models/godlife-mobilenetv2.pt (ai-server 가 읽는 체크포인트) + models/godlife-mobilenetv2.metrics.json (학습 기록)
"""

from __future__ import annotations

import copy
import json
import random
import time
from pathlib import Path

import torch
from torch import nn
from torchvision import datasets, models, transforms

ROOT = Path(__file__).parent.parent
DATA_DIR = ROOT / "training" / "data" / "dataset"
OUT = ROOT / "models" / "godlife-mobilenetv2.pt"
METRICS = ROOT / "models" / "godlife-mobilenetv2.metrics.json"

LABELS = ["exercise", "study", "reading", "cooking", "other"]  # categories.ai_label 과 같은 폴더 이름
MODEL_VERSION = "mobilenetv2-v1"
SEED = 42
BATCH = 32
HEAD_EPOCHS, FINETUNE_EPOCHS = 8, 6
UNFROZEN_BLOCKS = 4   # 뒤에서부터 미세 조정할 블록 수
AUG_VIEWS = 2         # 학습 사진 한 장마다 만들어 둘 증강 모습 수 (원본 1 + 증강 2)

# 서빙(model.py)과 같은 값을 써야 한다
IMAGE_SIZE, RESIZE = 224, 256
MEAN, STD = (0.485, 0.456, 0.406), (0.229, 0.224, 0.225)


class Views(torch.utils.data.Dataset):
    """사진 한 장을 여러 전처리로 읽어 (모습들, 정답) 을 돌려준다."""

    def __init__(self, samples, loader, transforms_):
        self.samples, self.loader, self.transforms = samples, loader, transforms_

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, index):
        path, target = self.samples[index]
        image = self.loader(path)
        return torch.stack([t(image) for t in self.transforms]), target


def main() -> None:
    random.seed(SEED)
    torch.manual_seed(SEED)

    normalize = [transforms.ToTensor(), transforms.Normalize(MEAN, STD)]
    eval_tf = transforms.Compose([transforms.Resize(RESIZE), transforms.CenterCrop(IMAGE_SIZE), *normalize])
    train_tf = transforms.Compose([
        transforms.RandomResizedCrop(IMAGE_SIZE, scale=(0.6, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.2),
        transforms.RandomRotation(12),
        *normalize,
    ])

    folder = datasets.ImageFolder(DATA_DIR)
    assert folder.classes == sorted(LABELS), f"폴더 이름을 확인해 주세요: {folder.classes}"
    classes = folder.classes  # 폴더 이름의 사전순으로 번호가 매겨진다 → 이 순서를 체크포인트에 저장한다

    # 클래스마다 8:2 로 나눈다 (검증 사진은 학습에 쓰지 않는다)
    by_class: dict[int, list[tuple[str, int]]] = {}
    for sample in folder.samples:
        by_class.setdefault(sample[1], []).append(sample)
    train_samples, val_samples = [], []
    for samples in by_class.values():
        random.shuffle(samples)
        cut = max(1, int(len(samples) * 0.2))
        val_samples += samples[:cut]
        train_samples += samples[cut:]
    counts = {classes[t]: len(s) for t, s in sorted(by_class.items())}
    print("classes:", classes, counts)
    print(f"train {len(train_samples)}장 / val {len(val_samples)}장", flush=True)

    model = models.mobilenet_v2(weights=models.MobileNet_V2_Weights.IMAGENET1K_V2)
    model.classifier[1] = nn.Linear(model.last_channel, len(classes))  # 1280 → 5
    trunk = model.features[:-UNFROZEN_BLOCKS]   # 끝까지 얼려 두는 앞부분
    tail = model.features[-UNFROZEN_BLOCKS:]    # 2단계에서 미세 조정할 뒤쪽 블록
    for p in model.features.parameters():
        p.requires_grad = False
    model.eval()

    def cache(samples, transforms_, name):
        """얼린 앞부분의 출력을 미리 계산해 둔다 → (모습 수 × 사진 수) 개의 특징과 정답"""
        loader = torch.utils.data.DataLoader(Views(samples, folder.loader, transforms_), batch_size=BATCH)
        features, targets, started = [], [], time.time()
        with torch.inference_mode():
            for i, (x, y) in enumerate(loader):
                views = x.shape[1]
                features.append(trunk(x.flatten(0, 1)).to(torch.float16))
                targets.append(y.repeat_interleave(views))
                if i % 20 == 0:
                    print(f"  {name} {i * BATCH}/{len(samples)}  ({time.time() - started:.0f}s)", flush=True)
        return torch.cat(features), torch.cat(targets)

    train_x, train_y = cache(train_samples, [eval_tf] + [train_tf] * AUG_VIEWS, "train")
    val_x, val_y = cache(val_samples, [eval_tf], "val")
    print(f"특징 준비 끝: train {tuple(train_x.shape)} / val {tuple(val_x.shape)}", flush=True)

    # 라벨마다 사진 수가 달라서(독서가 가장 적다) 적은 라벨의 손실을 그만큼 더 크게 본다
    weights = torch.tensor([len(train_samples) / (len(classes) * sum(1 for _, t in train_samples if t == c))
                            for c in range(len(classes))])
    criterion = nn.CrossEntropyLoss(weight=weights, label_smoothing=0.05)

    def forward(x):
        pooled = nn.functional.adaptive_avg_pool2d(tail(x.to(torch.float32)), (1, 1)).flatten(1)
        return model.classifier(pooled)

    def run_epoch(x, y, optimizer=None, train_tail=False):
        training = optimizer is not None
        model.classifier.train(training)
        tail.train(training and train_tail)  # 얼려 둔 동안에는 배치 정규화 통계도 건드리지 않는다
        order = torch.randperm(len(y)) if training else torch.arange(len(y))
        total, correct, loss_sum = 0, 0, 0.0
        with torch.set_grad_enabled(training):
            for start in range(0, len(y), BATCH):
                index = order[start:start + BATCH]
                out = forward(x[index])
                loss = criterion(out, y[index])
                if training:
                    optimizer.zero_grad()
                    loss.backward()
                    optimizer.step()
                loss_sum += loss.item() * len(index)
                correct += (out.argmax(1) == y[index]).sum().item()
                total += len(index)
        return loss_sum / total, correct / total

    best = {"acc": 0.0, "state": None}
    history = []

    def save(confusion=None) -> None:
        OUT.parent.mkdir(exist_ok=True)
        torch.save({
            "state_dict": best["state"],
            "labels": classes,
            "version": MODEL_VERSION,
            "val_accuracy": round(best["acc"], 4),
        }, OUT)
        METRICS.write_text(json.dumps({
            "version": MODEL_VERSION,
            "base": "torchvision MobileNetV2 (ImageNet1K_V2) 전이학습",
            "labels": classes,
            "images": counts,
            "train": len(train_samples),
            "val": len(val_samples),
            "val_accuracy": round(best["acc"], 4),
            "history": history,
            "confusion": confusion,  # 줄 = 정답, 칸 = 예측 (labels 순서)
        }, ensure_ascii=False, indent=2), encoding="utf-8")

    def fit(epochs: int, optimizer, stage: str, train_tail: bool) -> None:
        for epoch in range(1, epochs + 1):
            started = time.time()
            tr_loss, tr_acc = run_epoch(train_x, train_y, optimizer, train_tail)
            va_loss, va_acc = run_epoch(val_x, val_y)
            history.append({"stage": stage, "epoch": epoch, "train_loss": round(tr_loss, 4),
                            "train_acc": round(tr_acc, 4), "val_loss": round(va_loss, 4), "val_acc": round(va_acc, 4)})
            mark = ""
            if va_acc > best["acc"]:
                best["acc"], best["state"], mark = va_acc, copy.deepcopy(model.state_dict()), "  ← best"
            print(f"[{stage}] {epoch:2d}  train loss {tr_loss:.3f} acc {tr_acc:.3f} | val loss {va_loss:.3f} "
                  f"acc {va_acc:.3f}  ({time.time() - started:.0f}s){mark}", flush=True)

    # 1단계: 분류층만
    fit(HEAD_EPOCHS, torch.optim.Adam(model.classifier.parameters(), lr=1e-3), "head", train_tail=False)

    # 2단계: 뒤쪽 블록을 풀어 작은 학습률로
    for p in tail.parameters():
        p.requires_grad = True
    fit(FINETUNE_EPOCHS, torch.optim.Adam([p for p in model.parameters() if p.requires_grad], lr=1e-4), "finetune",
        train_tail=True)

    model.load_state_dict(best["state"])
    model.eval()
    confusion = [[0] * len(classes) for _ in classes]
    with torch.inference_mode():
        for start in range(0, len(val_y), BATCH):
            predicted = forward(val_x[start:start + BATCH]).argmax(1)
            for t, p in zip(val_y[start:start + BATCH].tolist(), predicted.tolist()):
                confusion[t][p] += 1
    save(confusion)

    print(f"가장 좋은 검증 정확도: {best['acc']:.3f}")
    print("줄 = 정답, 칸 = 예측")
    print(" " * 10 + "".join(f"{c[:8]:>9s}" for c in classes))
    for i, c in enumerate(classes):
        print(f"{c:10s}" + "".join(f"{n:9d}" for n in confusion[i])
              + f"   정확도 {confusion[i][i] / max(1, sum(confusion[i])):.2f}")
    print("저장:", OUT, f"({OUT.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
