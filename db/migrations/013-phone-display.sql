-- 설정 화면에서 본인에게 인증한 휴대폰 번호를 보여 주기 위한 암호화 값 (AES-256-GCM, 서버 키로만 풀 수 있다).
-- 번호의 중복·본인 확인은 계속 phone_hash(HMAC)로 한다. 이 변경 전에 인증한 회원은 값이 없어, 다시 인증하면 채워진다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/013-phone-display.sql

ALTER TABLE users
  ADD COLUMN phone_enc VARCHAR(100) NULL COMMENT '휴대폰 번호 암호화 값 (본인에게만 다시 보여 준다)' AFTER phone_hash;

ALTER TABLE phone_verifications
  ADD COLUMN phone_enc VARCHAR(100) NULL COMMENT '인증하는 번호의 암호화 값 (인증이 끝나면 users.phone_enc 로 옮긴다)' AFTER phone_hash;
