"""직접 파인튜닝한 인증 사진 분류 모델을 불러와 추론한다.

- 모델은 Colab 노트북(training/godlife_finetune.ipynb)에서 MobileNetV2 를 전이학습으로 학습해 만든 체크포인트(.pt)다.
  외부 AI API 를 부르지 않고, 이 서버가 그 파일을 직접 읽어 추론한다.
- 체크포인트가 없거나 PyTorch 가 설치되지 않았으면 모델 없이 뜬다 (/health 가 이유를 알려 주고 /predict 는 503).
  사전학습 가중치만으로 대신 판정하지 않는다: 직접 학습한 모델이 없으면 '판정할 수 없음'이 맞다.
"""

from __future__ import annotations

import base64
import io
import os
from dataclasses import dataclass
from pathlib import Path

# 학습(노트북)과 서빙이 같은 값을 써야 한다
IMAGE_SIZE = 224
RESIZE = 256
MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)
EMBEDDING_DIM = 1280  # MobileNetV2 마지막 특징 벡터 (float32 × 1280 = 5120바이트 → image_embeddings.embedding)

DEFAULT_MODEL_PATH = Path(__file__).parent / "models" / "godlife-mobilenetv2.pt"


@dataclass
class Prediction:
    label: str
    confidence: float
    scores: dict[str, float]
    embedding: str  # float32 little-endian 1280개를 base64 로 (L2 정규화되어 있어 내적이 곧 코사인 유사도)
    model_version: str


class Classifier:
    """체크포인트 하나를 메모리에 올려 두고 사진을 분류한다."""

    def __init__(self) -> None:
        self.model = None
        self.labels: list[str] = []
        self.version = ""
        self.val_accuracy: float | None = None
        self.reason = "모델을 아직 불러오지 않았어요."
        self._torch = None
        self._transform = None

    @property
    def loaded(self) -> bool:
        return self.model is not None

    def load(self, path: str | os.PathLike | None = None) -> None:
        """서버가 뜰 때 한 번 부른다. 실패해도 예외를 던지지 않고 이유만 남긴다."""
        target = Path(path or os.environ.get("MODEL_PATH") or DEFAULT_MODEL_PATH)
        try:
            import torch
            from torchvision import models, transforms
        except ImportError:
            self.reason = "PyTorch 가 설치되지 않았어요. `pip install -r requirements.txt` 를 실행해 주세요."
            return
        if not target.is_file():
            self.reason = (
                f"모델 파일이 없어요: {target}. Colab 노트북(training/godlife_finetune.ipynb)으로 학습한 "
                "체크포인트를 이 위치에 넣어 주세요."
            )
            return

        # 우리가 만든 파일만 읽는다 (weights_only=True: 임의 코드 실행을 막는다)
        checkpoint = torch.load(target, map_location="cpu", weights_only=True)
        labels = list(checkpoint["labels"])
        model = models.mobilenet_v2(weights=None)
        model.classifier[1] = torch.nn.Linear(model.last_channel, len(labels))
        model.load_state_dict(checkpoint["state_dict"])
        model.eval()

        self._torch = torch
        self._transform = transforms.Compose(
            [
                transforms.Resize(RESIZE),
                transforms.CenterCrop(IMAGE_SIZE),
                transforms.ToTensor(),
                transforms.Normalize(MEAN, STD),
            ]
        )
        self.model = model
        self.labels = labels
        self.version = str(checkpoint.get("version", target.stem))
        self.val_accuracy = checkpoint.get("val_accuracy")
        self.reason = ""

    def predict(self, image_bytes: bytes) -> Prediction:
        from PIL import Image, ImageOps

        torch = self._torch
        image = Image.open(io.BytesIO(image_bytes))
        image = ImageOps.exif_transpose(image).convert("RGB")  # 휴대폰 사진의 회전 정보를 반영한다
        batch = self._transform(image).unsqueeze(0)

        with torch.inference_mode():
            features = self.model.features(batch)
            pooled = torch.nn.functional.adaptive_avg_pool2d(features, (1, 1)).flatten(1)
            logits = self.model.classifier(pooled)
            probs = torch.softmax(logits, dim=1)[0]
            embedding = torch.nn.functional.normalize(pooled, dim=1)[0]

        best = int(torch.argmax(probs))
        raw = embedding.to(torch.float32).numpy().astype("<f4").tobytes()
        return Prediction(
            label=self.labels[best],
            confidence=round(float(probs[best]), 4),
            scores={label: round(float(p), 4) for label, p in zip(self.labels, probs)},
            embedding=base64.b64encode(raw).decode("ascii"),
            model_version=self.version,
        )
