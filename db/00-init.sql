-- GodLife 로컬 개발용 DB/계정 초기화. MySQL root 로 한 번만 실행한다.
--   mysql -u root -p < db/00-init.sql
-- 실행 전에 아래 'CHANGE_ME' 를 본인이 정한 비밀번호로 바꾼다. (바꾼 값은 backend/.env 의 DB_PASSWORD 와 같아야 함)
-- 비밀번호를 채운 채로 이 파일을 커밋하지 않는다.

CREATE DATABASE IF NOT EXISTS godlife
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'godlife_user'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- 스키마 스크립트(CREATE TABLE)를 실행할 수 있도록 godlife DB 안에서만 전체 권한
GRANT ALL PRIVILEGES ON godlife.* TO 'godlife_user'@'localhost';

-- 백엔드 통합 테스트 전용 DB. 테스트가 개발 데이터를 건드리지 않도록 분리한다.
-- 만든 뒤 스키마와 기준 데이터를 한 번 넣어 둔다:
--   mysql -u godlife_user -p godlife_test < db/01-schema.sql
--   mysql -u godlife_user -p godlife_test < db/02-seed.sql
CREATE DATABASE IF NOT EXISTS godlife_test
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON godlife_test.* TO 'godlife_user'@'localhost';
FLUSH PRIVILEGES;
