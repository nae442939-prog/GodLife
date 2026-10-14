// 뱃지가 생길 만한 일(인증 · 참여)을 마친 뒤 부르면 Celebrations 의 BadgeWatcher 가 바로 새 뱃지를 확인한다
export const CHECK_EVENT = 'godlife:check-badges'

export function checkBadges() {
  window.dispatchEvent(new Event(CHECK_EVENT))
}
