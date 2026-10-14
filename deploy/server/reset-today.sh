#!/bin/bash
# '안녕'(id 4)의 오늘 인증만 지워서 다시 인증해 볼 수 있게 한다 (시연 · 확인용). 사진 파일은 그대로 둔다
set -e
sudo mysql godlife <<'SQL'
START TRANSACTION;
CREATE TEMPORARY TABLE t AS
  SELECT v.id AS vid, v.participant_id AS pid
  FROM verifications v JOIN challenge_participants p ON p.id = v.participant_id
  WHERE p.user_id = 4 AND v.verify_date = CURDATE();
UPDATE ai_inference_results SET duplicate_of_id = NULL WHERE duplicate_of_id IN (SELECT vid FROM t);
DELETE FROM review_queue WHERE verification_id IN (SELECT vid FROM t);
DELETE FROM reports WHERE verification_id IN (SELECT vid FROM t);
DELETE FROM comments WHERE verification_id IN (SELECT vid FROM t);
DELETE FROM image_embeddings WHERE verification_id IN (SELECT vid FROM t);
DELETE FROM ai_inference_results WHERE verification_id IN (SELECT vid FROM t);
DELETE FROM verifications WHERE id IN (SELECT vid FROM t);
UPDATE challenge_participants p JOIN t ON t.pid = p.id
  SET p.success_days = GREATEST(p.success_days - 1, 0),
      p.current_streak = GREATEST(p.current_streak - 1, 0),
      p.max_streak = IF(p.success_days - 1 <= 0, 0, p.max_streak);
SELECT COUNT(*) AS removed FROM t;
COMMIT;
SQL
sudo mysql godlife -t -e "SELECT c.title, p.success_days, p.current_streak, p.max_streak, (SELECT COUNT(*) FROM verifications v WHERE v.participant_id=p.id AND v.verify_date=CURDATE()) AS today FROM challenge_participants p JOIN challenges c ON c.id=p.challenge_id WHERE p.user_id=4 AND p.status='ACTIVE'"
