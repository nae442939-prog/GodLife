#!/usr/bin/env bash
# 개발용: 대표 사진이 없는 챌린지에 종류별 더미 사진을 붙인다. (사진 출처는 db/dev-covers/CREDITS.md)
#   bash db/dev-covers.sh
# backend/.env 의 DB 접속 정보를 쓰고, 사진은 backend/uploads/cover/seed 로 복사한다.
set -euo pipefail
cd "$(dirname "$0")/../backend"
set -a; . ./.env; set +a
export MYSQL_PWD="$DB_PASSWORD"

mkdir -p uploads/cover/seed
cp ../db/dev-covers/*.jpg uploads/cover/seed/

mysql -u "${DB_USERNAME:-godlife_user}" --default-character-set=utf8mb4 "${DB_NAME:-godlife}" < ../db/dev-covers.sql
echo "대표 사진을 붙였어요."
