import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { settlementApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

const TOP = 10

/** 챌린지 랭킹 (참가자끼리): 인증 횟수 → 최장 연속. 상위 10명 + 내가 그 밖이면 내 줄을 따로. */
export function ChallengeRanking({ challengeId }) {
  const [rows, setRows] = useState(null)

  useEffect(() => {
    let cancelled = false
    settlementApi
      .ranking(challengeId)
      .then((list) => !cancelled && setRows(list))
      .catch(() => !cancelled && setRows([]))
    return () => {
      cancelled = true
    }
  }, [challengeId])

  if (!rows || rows.length === 0) return null

  const top = rows.slice(0, TOP)
  const me = rows.findIndex((r) => r.mine) >= TOP ? rows.find((r) => r.mine) : null

  return (
    <section className="dt-section">
      <h2>챌린지 랭킹</h2>
      <ol className="rk">
        {top.map((r, i) => (
          <Row key={i} row={r} />
        ))}
        {me && (
          <>
            <li className="rk-gap" aria-hidden="true">
              ⋯
            </li>
            <Row row={me} />
          </>
        )}
      </ol>
    </section>
  )
}

function Row({ row: r }) {
  return (
    <li className={`rk-row is-link${r.mine ? ' is-mine' : ''}${r.rank <= 3 ? ` is-top${r.rank}` : ''}`}>
      <Link to={`/users/${r.userId}`} className="rk-row-link" aria-label={`${r.nickname} 프로필 보기`}>
        <span className="rk-rank">{r.rank}</span>
        {r.profileImageUrl ? (
          <Avatar src={r.profileImageUrl} size={30} />
        ) : (
          <span className="rk-initial" aria-hidden="true">
            {r.nickname.slice(0, 1)}
          </span>
        )}
        <span className="rk-name">
          {r.nickname}
          {r.mine && <small>나</small>}
        </span>
        <span className="rk-stat">
          인증 <strong>{r.successDays}</strong>회
          <span className="rk-streak">최장 {r.maxStreak}일</span>
        </span>
        <span className="rk-go" aria-hidden="true">
          프로필 보기 ›
        </span>
      </Link>
    </li>
  )
}
