-- 자동 로그인 설정 (설정 → 보안).
-- 켜져 있으면(기본) 리프레시 토큰 쿠키를 14일 동안 남겨 브라우저를 껐다 켜도 로그인이 유지된다.
-- 끄면 쿠키를 브라우저를 닫을 때 사라지는 세션 쿠키로 내려 준다. (공용 컴퓨터에서 쓰는 사람용)
-- 이미 만든 DB 에 적용한다. (새로 만드는 DB 는 01-schema.sql 에 이미 반영됨)
--   mysql -u godlife_user -p godlife < db/migrations/020-auto-login.sql

SET NAMES utf8mb4;

ALTER TABLE users
  ADD COLUMN auto_login BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '자동 로그인. TRUE = 브라우저를 닫아도 로그인 유지(14일), FALSE = 브라우저를 닫으면 로그아웃' AFTER status;
