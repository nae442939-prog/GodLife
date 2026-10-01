# 모델 파일을 두는 곳

직접 전이학습으로 학습한 체크포인트를 이 폴더에 둔다. (외부 AI API 를 쓰지 않는다)

- 파일 이름: `godlife-mobilenetv2.pt` (다른 위치를 쓰려면 환경변수 `MODEL_PATH`)
- 안에 든 것: 가중치(`state_dict`) · 라벨 순서(`labels`) · 버전(`version`) · 검증 정확도(`val_accuracy`)
- `godlife-mobilenetv2.metrics.json`: 학습 기록 (사진 수, 에폭별 손실·정확도, 혼동 행렬)

이 파일이 없으면 ai-server 는 뜨지만 `/predict` 가 503 을 돌려주고, 백엔드는 AI 판정 없이 인증을 받는다.

## 만드는 방법

내 컴퓨터(CPU)에서:

```
python training/prepare_dataset.py   # 공개 데이터셋(Stanford 40 Actions)에서 다섯 라벨 사진 고르기
python training/train_local.py       # MobileNetV2 전이학습 → 이 폴더에 저장
```

또는 Colab(GPU)에서 `training/godlife_finetune.ipynb` 를 돌리고 받은 파일을 이 폴더에 넣는다.

직접 찍은 인증 사진을 `training/data/dataset/{exercise,study,reading,cooking,other}/` 에 더 넣고 다시 학습하면
실제 인증 사진에 더 잘 맞는다.
