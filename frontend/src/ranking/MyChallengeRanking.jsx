import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { settlementApi, verificationApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { daysBetween, toIsoDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'
import { Avatar } from '../components/UserMenu.jsx'
import { Podium } from './Podium.jsx'

const format = (v) => `${Math.round(v)}회`

/**
 * 내 챌린지 랭킹: 내가 하는 챌린지를 고르면 그 챌린지 참가자들의 순위를 3D 시상대로 (인증 횟수 → 최장 연속).
 * 고른 챌린지는 주소(?challenge=)에 둔다. 비로그인이면 로그인 안내.
 */
export function MyChallengeRanking({ selectedId, onSelect }) {
  const { status } = useAuth()
  const [mine, setMine] = useState({ loaded: false, items: [], error: '' })

  useEffect(() => {
    if (status !== 'authed') return
    let cancelled = false
    verificationApi
      .myChallenges()
      .then((items) => !cancelled && setMine({ loaded: true, items: items.filter((c) => c.joined), error: '' }))
      .catch((err) => !cancelled && setMine({ loaded: true, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [status])

  if (status === 'anon') {
    return (
      <div className="my-ch-empty">
        <p>로그인하면 내가 하는 챌린지의 참가자 순위를 볼 수 있어요.</p>
        <Link to="/login" state={{ from: '/rankings?tab=challenges' }} className="btn btn-dark">
          로그인
        </Link>
      </div>
    )
  }
  if (!mine.loaded) return <p className="muted">불러오는 중…</p>
  if (mine.error) return <p className="form-error">{mine.error}</p>
  if (mine.items.length === 0) {
    return (
      <div className="my-ch-empty">
        <p>아직 참여 중인 챌린지가 없어요. 챌린지에 참여하면 참가자끼리 순위를 겨룰 수 있어요.</p>
        <Link to="/challenges" className="btn btn-dark">
          챌린지 둘러보기
        </Link>
      </div>
    )
  }

  // 고른 챌린지가 없으면 진행 중인 것 먼저
  const selected =
    mine.items.find((c) => String(c.id) === String(selectedId)) ??
    mine.items.find((c) => c.inProgress) ??
    mine.items[0]
  const today = toIsoDate(new Date())

  return (
    <>
      <ul className="mcr-picker" aria-label="내 챌린지">
        {mine.items.map((c) => (
          <li key={c.id}>
            <button
              type="button"
              className={`mcr-card cat-${c.category.id}${c.id === selected.id ? ' is-active' : ''}`}
              aria-pressed={c.id === selected.id}
              onClick={() => onSelect(c.id)}
            >
              <span className="mcr-icon">
                <CategoryIcon id={c.category.id} size={17} strokeWidth={1.8} />
              </span>
              <span className="mcr-text">
                <strong>{c.title}</strong>
                <span>
                  {c.inProgress
                    ? `진행 중 · ${daysBetween(c.startDate, today) + 1}일째`
                    : `시작 전 · D-${daysBetween(today, c.startDate)}`}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>

      <ChallengePodium key={selected.id} challenge={selected} />
    </>
  )
}

function ChallengePodium({ challenge: c }) {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    settlementApi
      .ranking(c.id)
      .then((list) => !cancelled && setRows(list))
      .catch((err) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
  }, [c.id])

  if (error) return <p className="form-error">{error}</p>
  if (!rows) return <p className="muted">불러오는 중…</p>

  // 시상대·순위표는 인증 횟수로
  const entries = rows.map((r) => ({ ...r, value: r.successDays }))
  const me = entries.find((r) => r.mine)

  return (
    <section className="mcr-board">
      <div className="mcr-board-head">
        <h2>{c.title}</h2>
        <Link to={`/challenges/${c.id}`} className="section-more">
          챌린지로 가기 →
        </Link>
      </div>
      <p className="rn-help">같은 챌린지 참가자끼리 인증 횟수로 겨뤄요. 같으면 최장 연속 기록이 긴 사람이 앞이에요.</p>

      {entries.every((r) => r.value === 0) ? (
        <p className="muted">아직 인증한 사람이 없어요. 첫 인증으로 1등 단상에 올라 보세요!</p>
      ) : (
        <Podium entries={entries.filter((r) => r.value > 0)} format={format} />
      )}

      {me && (
        <div className="rn-me">
          <span>내 순위</span>
          <strong>{me.rank}위</strong>
          <span className="rn-me-value">
            {format(me.value)} · 최장 {me.maxStreak}일
          </span>
        </div>
      )}

      <ol className="rk">
        {entries.map((r, i) => (
          <li key={i} className={`rk-row is-link${r.mine ? ' is-mine' : ''}${r.rank <= 3 ? ` is-top${r.rank}` : ''}`}>
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
                인증 <strong>{r.successDays}</strong>회<span className="rk-streak">최장 {r.maxStreak}일</span>
              </span>
              <span className="rk-go" aria-hidden="true">
                프로필 보기 ›
              </span>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  )
}
