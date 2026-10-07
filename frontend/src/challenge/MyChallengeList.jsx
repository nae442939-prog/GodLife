import { Link } from 'react-router-dom'
import { daysBetween, toIsoDate } from './format.js'
import { CategoryIcon } from './icons.jsx'

/**
 * 내 챌린지 목록 (마이페이지 · 챌린지 목록 위).
 * 줄을 누르면 상세로, 오른쪽 버튼으로 바로 인증하러 간다.
 */
export function MyChallengeList({ items }) {
  return (
    <ul className="my-ch">
      {items.map((c) => (
        <MyChallengeRow key={c.id} challenge={c} />
      ))}
    </ul>
  )
}

function MyChallengeRow({ challenge: c }) {
  const today = toIsoDate(new Date())
  const percent = c.targetCount > 0 ? Math.min(100, Math.round((c.successDays / c.targetCount) * 100)) : 0
  const dayNumber = daysBetween(c.startDate, today) + 1

  return (
    <li className={`my-ch-row cat-${c.category.id}`}>
      <Link to={`/challenges/${c.id}`} className="my-ch-main">
        <span className="cl-icon-tile my-ch-icon">
          <CategoryIcon id={c.category.id} size={19} strokeWidth={1.8} />
        </span>
        <span className="my-ch-text">
          <span className="my-ch-title">{c.title}</span>
          <span className="my-ch-meta">
            {c.inProgress ? (
              <>
                <span className="my-ch-state is-on">진행 중</span>
                {dayNumber}일째 / {c.totalDays}일
              </>
            ) : (
              <>
                <span className="my-ch-state">시작 전</span>
                D-{daysBetween(today, c.startDate)}
              </>
            )}
            {!c.joined && c.host && ' · 개설만 함'}
          </span>
        </span>
        {c.joined && c.inProgress && (
          <span className="my-ch-percent" aria-label={`내 달성률 ${percent}%`}>
            {percent}%
            <span className="my-ch-bar" aria-hidden="true">
              <span style={{ width: `${percent}%` }} />
            </span>
          </span>
        )}
      </Link>
      <RowAction challenge={c} />
    </li>
  )
}

function RowAction({ challenge: c }) {
  if (!c.joined || !c.inProgress) return null
  if (c.verifyState === 'OPEN') {
    return (
      <Link to={`/challenges/${c.id}/verify`} className="my-ch-go">
        인증하기
      </Link>
    )
  }
  if (c.verifyState === 'DONE_TODAY') {
    return <span className="my-ch-done">✓ 오늘 완료</span>
  }
  return null
}
