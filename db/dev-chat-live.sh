#!/usr/bin/env bash
# 개발용: 더미 참가자들이 몇 초마다 오픈채팅에 메시지를 보내는 것처럼 흉내 낸다. (실시간 채팅 화면 확인용)
#   bash db/dev-chat-live.sh [챌린지 제목] [간격(초)]
# backend/.env 의 DB 접속 정보를 쓴다. Ctrl+C 로 멈춘다. 메시지를 다 보내면 스스로 끝난다.
set -euo pipefail
cd "$(dirname "$0")/../backend"
set -a; . ./.env; set +a
export MYSQL_PWD="$DB_PASSWORD"

TITLE="${1:-저녁 산책 30분}"
INTERVAL="${2:-6}"

LINES=(
  "walk@dummy.godlife|방금 공원 한 바퀴 돌고 왔어요 🌙"
  "early@dummy.godlife|오 저도 지금 나가려던 참! 같이 인증해요"
  "water@dummy.godlife|오늘 하늘 노을 미쳤어요 사진 꼭 찍으세요"
  "yoga@dummy.godlife|산책하고 스트레칭 5분 하면 다음 날 덜 뻐근해요"
  "walk@dummy.godlife|연속 7일째! 다들 어디까지 왔어요?"
  "early@dummy.godlife|저 5일째요 ㅎㅎ 주말이 고비 😂"
  "water@dummy.godlife|주말엔 낮에 걷는 것도 인정되는 거죠?"
  "walk@dummy.godlife|네! 하루 중 아무 때나 30분이면 돼요"
  "yoga@dummy.godlife|방금 인증 올렸어요. 오늘도 갓생 ✨"
  "early@dummy.godlife|내일 비 온대요. 우산 챙겨서 걸어요 ☔"
)

for line in "${LINES[@]}"; do
  email="${line%%|*}"
  content="${line#*|}"
  mysql -u "${DB_USERNAME:-godlife_user}" --default-character-set=utf8mb4 "${DB_NAME:-godlife}" -e "
    INSERT INTO chat_messages (challenge_id, sender_id, content)
    SELECT c.id, u.id, '${content//\'/\'\'}'
    FROM challenges c JOIN users u ON u.email = '${email}'
    WHERE c.title = '${TITLE//\'/\'\'}' LIMIT 1;"
  echo "[$(date +%H:%M:%S)] ${email%%@*}: ${content}"
  sleep "$INTERVAL"
done
echo "끝"
