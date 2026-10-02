// 커뮤니티 화면들이 같이 쓰는 표시용 문구

// 말머리 (서버의 Topic 과 같은 값)
export const TOPICS = [
  { value: 'FREE', label: '자유' },
  { value: 'REVIEW', label: '인증 후기' },
  { value: 'TIP', label: '팁' },
  { value: 'QUESTION', label: '질문' },
]

export const TOPIC_LABEL = Object.fromEntries(TOPICS.map((t) => [t.value, t.label]))

export const POST_SORTS = [
  { value: 'latest', label: '최신순' },
  { value: 'popular', label: '인기순' },
]

// 서버 규칙(CommunityDtos)과 같은 값
export const MAX_TITLE = 100
export const MAX_CONTENT = 2000
export const MAX_COMMENT = 300
export const MAX_IMAGES = 4

/** '방금 전' / '5분 전' / '3시간 전' / '어제' / '10.02' */
export function timeAgo(iso, now = new Date()) {
  const then = new Date(iso)
  const minutes = Math.floor((now - then) / 60_000)
  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  if (hours < 48) return '어제'
  const p = (n) => String(n).padStart(2, '0')
  const date = `${p(then.getMonth() + 1)}.${p(then.getDate())}`
  return then.getFullYear() === now.getFullYear() ? date : `${then.getFullYear()}.${date}`
}
