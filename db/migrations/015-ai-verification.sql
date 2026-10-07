-- AI 인증 검증: 관리자 검토에서 인증이 거절되면 회원에게 알린다 → 알림 종류에 VERIFY_REJECTED 를 더한다.
-- (판정 결과 · 임베딩 · 검토 큐 테이블 ai_inference_results / image_embeddings / review_queue 는 처음부터 있었다)
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/015-ai-verification.sql

ALTER TABLE notifications
  MODIFY type ENUM('SETTLEMENT','VERIFY_REMINDER','COMMENT','REPORT_RESULT','REPORT_ALERT',
                   'FOLLOW','MESSAGE_REQUEST','INQUIRY_ANSWER','VERIFY_REJECTED') NOT NULL;
