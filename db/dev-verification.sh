#!/usr/bin/env bash
# 개발용: '오늘의 인증' 화면 확인용 진행 중 챌린지와 더미 인증 사진을 만든다.
#   bash db/dev-verification.sh
# backend/.env 의 DB 접속 정보를 쓰고, 사진은 backend/uploads 에 만든다. (dev-dummy.sql 을 먼저 넣어 둘 것)
set -euo pipefail
cd "$(dirname "$0")/../backend"
set -a; . ./.env; set +a
export MYSQL_PWD="$DB_PASSWORD"

mysql -u "${DB_USERNAME:-godlife_user}" --default-character-set=utf8mb4 --batch --skip-column-names \
  "${DB_NAME:-godlife}" < ../db/dev-verification.sql \
  | java ../db/DevVerificationPhotos.java uploads
