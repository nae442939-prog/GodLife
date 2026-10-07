package com.godlife.backend.verification.ai;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 인증 사진 분류기. 구현은 직접 학습한 모델을 서빙하는 ai-server(FastAPI)를 부른다 ({@link HttpAiClassifier}).
 * 외부 LLM·비전 API 를 부르는 구현은 만들지 않는다 (과제 요구사항: 직접 전이학습으로 훈련한 모델).
 */
public interface AiClassifier {

    /**
     * 사진을 분류한다. AI 서버가 꺼져 있거나 모델이 아직 없으면 빈 값 (그때는 AI 판정 없이 인증을 받는다).
     */
    Optional<AiPrediction> classify(Path image);

    /**
     * @param label        가장 그럴듯한 라벨 (categories.ai_label 과 같은 이름)
     * @param confidence   그 라벨의 확신도 0~1
     * @param embedding    사진의 특징 벡터 (float32 little-endian × 1280, L2 정규화). 비슷한 사진 찾기에 쓴다
     * @param modelVersion 판정한 모델 버전
     */
    record AiPrediction(String label, double confidence, byte[] embedding, String modelVersion) {
    }
}
