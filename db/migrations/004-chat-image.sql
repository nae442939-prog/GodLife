-- 오픈채팅 사진 전송. 사진 파일은 서버 디스크(app.upload.dir)에 두고 DB 에는 파일 키만 둔다.
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/004-chat-image.sql

ALTER TABLE chat_messages
  MODIFY content VARCHAR(500) NULL COMMENT '글. 사진만 보낸 메시지는 NULL',
  ADD COLUMN image_key VARCHAR(80) NULL COMMENT '사진 파일 키(서버가 만든 이름). 참가자만 내려받을 수 있다' AFTER content;
