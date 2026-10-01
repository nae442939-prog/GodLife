"""갓생살기 AI 서버: 인증 사진이 챌린지 카테고리와 맞는지 분류한다.

직접 파인튜닝한 모델(models/godlife-mobilenetv2.pt)을 이 서버가 읽어 추론한다. 외부 AI API 는 부르지 않는다.
메인 백엔드(Spring Boot)만 호출하는 내부 서버라 127.0.0.1 에서만 띄운다.

    uvicorn main:app --host 127.0.0.1 --port 8000
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile

from model import Classifier

MAX_BYTES = 6 * 1024 * 1024  # 백엔드가 다시 그려 저장한 사진(긴 쪽 1600px)만 오므로 넉넉한 상한
classifier = Classifier()


@asynccontextmanager
async def lifespan(app: FastAPI):
    classifier.load()
    yield


app = FastAPI(title="GodLife AI server", lifespan=lifespan)


@app.get("/")
def read_root():
    return {"message": "GodLife AI server is running"}


@app.get("/health")
def health():
    """모델이 올라와 있는지. 백엔드가 판정을 맡겨도 되는지 볼 때 쓴다."""
    return {
        "model_loaded": classifier.loaded,
        "model_version": classifier.version or None,
        "labels": classifier.labels,
        "val_accuracy": classifier.val_accuracy,
        "reason": classifier.reason or None,
    }


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """사진 한 장을 분류한다: 가장 그럴듯한 라벨 · 확신도 · 라벨별 점수 · 임베딩(재사용 사진 탐지용)."""
    if not classifier.loaded:
        raise HTTPException(status_code=503, detail=classifier.reason)
    data = await file.read()
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(status_code=400, detail="사진이 비었거나 너무 커요.")
    try:
        prediction = classifier.predict(data)
    except (OSError, ValueError):
        # PIL 이 열 수 없는 파일
        raise HTTPException(status_code=400, detail="사진을 읽을 수 없어요.")
    return prediction
