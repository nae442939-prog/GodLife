import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { seasonApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'
import { Podium } from './Podium.jsx'

const PERIODS = [
  { value: 'weekly', label: '주간 시즌' },
  { value: 'monthly', label: '월간 시즌' },
]

/**
 * 시즌 랭킹: 주간(월~일) · 월간(1일~말일) 중 하나를 본다. 그 시즌 동안 승인된 인증 수로 순위를 매기고,
 * 시즌이 끝나면 상위 3명(공동 순위 포함)이 차등 보너스 포인트를 받는다. 비로그인도 볼 수 있다.
 */
export function SeasonRanking({ period, onPeriod }) {
  const [data, setData] = useState({ key: null, season: null, error: '' })

  useEffect(() => {
    let cancelled = false
    seasonApi
      .current(period)
      .then((r) => !cancelled && setData({ key: period, season: r, error: '' }))
      .catch((err) => !cancelled && setData({ key: period, season: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [period])

  const value = (v) => `${Math.round(v).toLocaleString()}회`

  return (
    <>
      <div className="rn-metrics" role="group" aria-label="시즌 기준">
        {PERIODS.map((p) => (
          <button
            key={p.value}
            type="button"
            className={`rn-metric${p.value === period ? ' is-active' : ''}`}
            aria-pressed={p.value === period}
            onClick={() => onPeriod(p.value)}
          >
            {p.label}
          </button>
        ))}
      </div>

      {data.key !== period ? (
        <p className="muted">불러오는 중…</p>
      ) : data.error ? (
        <p className="form-error">{data.error}</p>
      ) : (
        <SeasonBody season={data.season} value={value} />
      )}
    </>
  )
}

function SeasonBody({ season: s, value }) {
  return (
    <>
      <div className="rn-season-head">
        <span>
          {s.start} ~ {s.end}
        </span>
        <span className="rn-season-dday">
          {s.status === 'CLOSED' ? '종료됨' : s.daysLeft === 0 ? '오늘 마감' : `D-${s.daysLeft}`}
        </span>
      </div>
      <p className="rn-help">
        그 시즌 동안 승인된 인증 수로 순위를 매겨요. 상위 3명(공동 순위 포함)은 시즌이 끝나면 보너스 포인트를
        받아요: 1위 5,000P · 2위 3,000P · 3위 1,000P.
        {!s.bonusConfirmed && ' 진행 중에는 지금 순위 기준 예상 보너스예요 — 시즌이 끝나야 확정되고 지급돼요.'}
      </p>

      <Podium entries={s.top} format={value} />

      {s.me ? (
        <div className="rn-me">
          <span>내 순위</span>
          <strong>{s.me.rank}위</strong>
          <span className="rn-me-value">{value(s.me.score)}</span>
          {s.me.bonusPoints > 0 && (
            <span className="rn-bonus">
              +{s.me.bonusPoints.toLocaleString()}P{!s.bonusConfirmed && ' 예상'}
            </span>
          )}
        </div>
      ) : (
        <p className="muted">아직 기록이 없어요. 챌린지에 참여하고 인증하면 순위에 올라요!</p>
      )}

      {s.top.length === 0 ? (
        <p className="muted">아직 랭킹에 오른 사람이 없어요.</p>
      ) : (
        <ol className="rk">
          {s.top.map((r, i) => (
            <li
              key={i}
              className={`rk-row is-link${r.mine ? ' is-mine' : ''}${r.rank <= 3 ? ` is-top${r.rank}` : ''}`}
            >
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
                <span className="rn-value">{value(r.score)}</span>
                {r.bonusPoints > 0 && (
                  <span className="rn-bonus">
                    +{r.bonusPoints.toLocaleString()}P{!s.bonusConfirmed && ' 예상'}
                  </span>
                )}
                <span className="rk-go" aria-hidden="true">
                  프로필 보기 ›
                </span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </>
  )
}
