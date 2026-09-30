// 챌린지 화면들이 같이 쓰는 표시용 문구

export const MODE_LABEL = { FREE: '무료 챌린지', BET: '포인트 챌린지' }

export const SORT_OPTIONS = [
  { value: 'popular', label: '인기순' },
  { value: 'deadline', label: '시작 임박순' },
  { value: 'latest', label: '최신순' },
]

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** '2026-10-01' → Date (로컬 자정). new Date('YYYY-MM-DD') 는 UTC 로 읽혀 날짜가 밀릴 수 있어 직접 나눈다. */
export function parseDate(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** Date → 'YYYY-MM-DD' (로컬 기준) */
export function toIsoDate(date) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`
}

export function addDays(iso, days) {
  const d = parseDate(iso)
  d.setDate(d.getDate() + days)
  return toIsoDate(d)
}

/** 두 날짜 사이 일수 (b - a) */
export function daysBetween(a, b) {
  return Math.round((parseDate(b) - parseDate(a)) / 86_400_000)
}

/** '10.01(수)' */
export function shortDate(iso) {
  const d = parseDate(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}(${WEEKDAYS[d.getDay()]})`
}

export function periodText(c) {
  return `${shortDate(c.startDate)} - ${shortDate(c.endDate)} · ${c.totalDays}일`
}

export function frequencyText(c) {
  return c.frequencyType === 'DAILY' ? '매일 인증' : `주 ${c.weeklyCount}회 인증`
}

export function pointText(c) {
  return c.mode === 'BET' ? `${c.entryFee.toLocaleString()}P` : '무료'
}

/** 'HH:MM:SS' → 'HH:MM' */
export function timeText(t) {
  return t ? t.slice(0, 5) : ''
}

/** 시작까지 남은 날 표시: 'D-3' / '오늘 시작' / '진행 중' / '종료' */
export function dDayText(c, todayIso = toIsoDate(new Date())) {
  const toStart = daysBetween(todayIso, c.startDate)
  if (toStart > 0) return `D-${toStart}`
  if (toStart === 0) return '오늘 시작'
  return daysBetween(todayIso, c.endDate) >= 0 ? '진행 중' : '종료'
}

/** 지금 인증할 수 없는 이유 (인증 화면과 같이 쓴다) */
export function closedText(state, c) {
  return {
    DONE_TODAY: '오늘 인증 완료 ✅',
    WEEK_DONE: '이번 주 인증을 다 채웠어요',
    TIME_CLOSED: `${timeText(c.verifyFrom)} ~ ${timeText(c.verifyUntil)}에만 인증할 수 있어요`,
    NOT_STARTED: `${shortDate(c.startDate)}부터 인증할 수 있어요`,
    ENDED: '끝난 챌린지예요',
  }[state]
}

/** 내 진행 현황(달성률)을 볼 수 있는 참가 상태: 참여 중이거나, 끝난 뒤 성공·실패 판정을 받음 */
export function hasProgress(c) {
  return ['ACTIVE', 'COMPLETED', 'FAILED'].includes(c.myStatus)
}
