package com.godlife.backend.verification.ai;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.verification.VerificationStatus;
import com.godlife.backend.verification.ai.AiClassifier.AiPrediction;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증 사진의 AI 판정. 직접 학습한 분류 모델(ai-server)이 낸 라벨·확신도와, 예전 사진과의 닮은 정도로 정한다.
 * <ul>
 *   <li>통과(AUTO_PASS): 라벨이 챌린지 카테고리와 같고 확신도가 충분 → 승인</li>
 *   <li>거절(AUTO_REJECT): 다른 카테고리라고 강하게 확신 → 저장하지 않고 다시 찍게 한다.
 *       하루에 {@value #MAX_AUTO_REJECTS}번까지만 거절하고, 그 뒤에는 사람이 보도록 검토로 넘긴다
 *       (모델이 틀려서 인증을 아예 못 하는 일이 없게)</li>
 *   <li>검토(NEED_REVIEW): 그 사이의 애매한 경우, 또는 예전 사진과 거의 같은 경우 → 일단 인증으로 받고 관리자 검토로 넘긴다</li>
 *   <li>'기타' 카테고리는 사진 모양이 제각각이라(일찍 일어나기 · 산책 등) 라벨로 거르지 않고, 같은 사진 재사용만 본다</li>
 *   <li>건너뜀(SKIPPED): AI 서버가 꺼져 있거나 모델이 없을 때 → 예전처럼 바로 승인</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AiVerifier {

    /** 같은 카테고리로 봤을 때 이 확신도 이상이면 바로 통과 */
    static final double PASS_CONFIDENCE = 0.60;
    /** 다른 카테고리로 봤을 때 이 확신도 이상이면 바로 거절 */
    static final double REJECT_CONFIDENCE = 0.85;
    /** 예전 사진과의 코사인 유사도가 이 이상이면 같은 사진을 다시 쓴 것으로 의심 */
    static final double DUPLICATE_SIMILARITY = 0.985;
    /** 한 참가자가 하루에 AI 에게 바로 거절당할 수 있는 횟수. 넘으면 관리자 검토로 넘긴다 */
    static final int MAX_AUTO_REJECTS = 5;
    private static final int COMPARE_LIMIT = 500;
    /** '기타' 카테고리의 라벨 (categories.ai_label) */
    private static final String ANY_LABEL = "other";

    public enum Decision {
        AUTO_PASS, AUTO_REJECT, NEED_REVIEW, SKIPPED
    }

    /**
     * @param reviewReason  검토로 넘기는 이유 — LOW_CONFIDENCE / DUPLICATE_SUSPECT (검토가 아니면 null)
     * @param maxSimilarity 예전 사진과 가장 닮은 정도 (비교할 사진이 없으면 null)
     * @param duplicateOfId 가장 닮았던 예전 인증
     */
    public record Judgement(Decision decision, AiPrediction prediction, boolean categoryMatch, String reviewReason,
                            Double maxSimilarity, Long duplicateOfId) {

        public VerificationStatus status() {
            return decision == Decision.NEED_REVIEW ? VerificationStatus.IN_REVIEW : VerificationStatus.APPROVED;
        }

        public boolean rejected() {
            return decision == Decision.AUTO_REJECT;
        }
    }

    private final AiClassifier classifier;
    private final NamedParameterJdbcTemplate jdbc;

    /** 오늘 참가자별로 AI 가 거절한 횟수 (거절은 DB 에 남기지 않아 메모리에 센다. 서버를 다시 켜면 0부터) */
    private final Map<Long, Integer> rejectsToday = new ConcurrentHashMap<>();
    private volatile LocalDate rejectsDate;

    /** 저장한 사진 파일을 판정한다 (아직 인증 줄을 만들기 전). */
    public Judgement judge(Challenge challenge, Long participantId, Long userId, Path image, LocalDate today) {
        Optional<AiPrediction> result = classifier.classify(image);
        if (result.isEmpty()) {
            return new Judgement(Decision.SKIPPED, null, false, null, null, null);
        }
        AiPrediction p = result.get();
        String expected = challenge.getCategory().getAiLabel();
        boolean free = ANY_LABEL.equals(expected);
        boolean match = free || expected.equals(p.label());
        if (!match && p.confidence() >= REJECT_CONFIDENCE && countReject(participantId, today) <= MAX_AUTO_REJECTS) {
            return new Judgement(Decision.AUTO_REJECT, p, false, null, null, null);
        }

        Similar similar = mostSimilar(p.embedding(), userId, challenge.getId());
        if (similar != null && similar.similarity() >= DUPLICATE_SIMILARITY) {
            return new Judgement(Decision.NEED_REVIEW, p, match, "DUPLICATE_SUSPECT", similar.similarity(),
                    similar.verificationId());
        }
        Double max = similar == null ? null : similar.similarity();
        Long nearest = similar == null ? null : similar.verificationId();
        if (free || (match && p.confidence() >= PASS_CONFIDENCE)) {
            return new Judgement(Decision.AUTO_PASS, p, true, null, max, nearest);
        }
        return new Judgement(Decision.NEED_REVIEW, p, match, "LOW_CONFIDENCE", max, nearest);
    }

    /** 인증 줄을 만든 뒤: 판정 결과 · 임베딩을 남기고, 검토가 필요하면 검토 큐에 올린다. */
    public void record(Long verificationId, Judgement j) {
        if (j.prediction() == null) {
            return;
        }
        AiPrediction p = j.prediction();
        jdbc.update("""
                INSERT INTO ai_inference_results
                    (verification_id, model_version, predicted_label, confidence, category_match, max_similarity,
                     duplicate_of_id, decision)
                VALUES (:id, :version, :label, :confidence, :match, :similarity, :duplicate, :decision)
                """, new MapSqlParameterSource("id", verificationId)
                .addValue("version", cut(p.modelVersion(), 30)).addValue("label", cut(p.label(), 50))
                .addValue("confidence", Math.max(0, Math.min(1, p.confidence())))
                .addValue("match", j.categoryMatch())
                .addValue("similarity", j.maxSimilarity() == null ? null : Math.max(-1, Math.min(1, j.maxSimilarity())))
                .addValue("duplicate", j.duplicateOfId()).addValue("decision", j.decision().name()));
        jdbc.update("""
                INSERT INTO image_embeddings (verification_id, embedding, model_version) VALUES (:id, :embedding, :version)
                """, new MapSqlParameterSource("id", verificationId).addValue("embedding", p.embedding())
                .addValue("version", cut(p.modelVersion(), 30)));
        if (j.decision() == Decision.NEED_REVIEW) {
            jdbc.update("INSERT INTO review_queue (verification_id, reason) VALUES (:id, :reason)",
                    new MapSqlParameterSource("id", verificationId).addValue("reason", j.reviewReason()));
        }
    }

    /** 이 참가자가 오늘 거절당한 횟수를 하나 올리고 돌려준다 (날짜가 바뀌면 처음부터) */
    private int countReject(Long participantId, LocalDate today) {
        if (!today.equals(rejectsDate)) {
            rejectsToday.clear();
            rejectsDate = today;
        }
        return rejectsToday.merge(participantId, 1, Integer::sum);
    }

    /** 거절 안내 문구 */
    public static String rejectMessage(Challenge challenge) {
        return "사진이 '" + challenge.getCategory().getName() + "' 챌린지와 맞지 않는 것 같아요. 다시 찍어 주세요.";
    }

    private record Similar(Long verificationId, double similarity) {
    }

    /**
     * 내 예전 인증 사진과, 같은 챌린지 참가자들의 인증 사진 중 가장 닮은 것 (최근 500장까지).
     * 임베딩이 L2 정규화되어 있어 내적이 곧 코사인 유사도다.
     */
    private Similar mostSimilar(byte[] embedding, Long userId, Long challengeId) {
        float[] mine = floats(embedding);
        Similar[] best = {null};
        jdbc.query("""
                SELECT e.verification_id, e.embedding
                FROM image_embeddings e
                  JOIN verifications v ON v.id = e.verification_id
                  JOIN challenge_participants p ON p.id = v.participant_id
                WHERE (p.user_id = :user OR p.challenge_id = :challenge) AND v.status <> 'REJECTED'
                ORDER BY e.verification_id DESC LIMIT :limit
                """, new MapSqlParameterSource("user", userId).addValue("challenge", challengeId)
                .addValue("limit", COMPARE_LIMIT), rs -> {
            float[] other = floats(rs.getBytes("embedding"));
            if (other.length != mine.length) {
                return; // 다른 모델이 만든 벡터는 비교하지 않는다
            }
            double dot = 0;
            for (int i = 0; i < mine.length; i++) {
                dot += mine[i] * other[i];
            }
            if (best[0] == null || dot > best[0].similarity()) {
                best[0] = new Similar(rs.getLong("verification_id"), dot);
            }
        });
        return best[0];
    }

    private static float[] floats(byte[] raw) {
        float[] values = new float[raw.length / Float.BYTES];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
