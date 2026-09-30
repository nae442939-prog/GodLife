import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { profileApi } from '../api/client.js'
import { MODE_LABEL, daysBetween, toIsoDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'

/**
 * 회원 프로필 (/users/:id). 랭킹·참가자 목록에서 사람을 누르면 온다.
 * 프로필과 기록을 카드 하나로, 참여 중인 공개 챌린지는 목록 카드 하나로 보여 준다.
 * 팔로우·메시지는 다음 단계에서 연다.
 */
export function ProfilePage() {
  const { id } = useParams()
  const [state, setState] = useState({ key: null, profile: null, error: '' })

  useEffect(() => {
    let cancelled = false
    profileApi
      .get(id)
      .then((profile) => !cancelled && setState({ key: id, profile, error: '' }))
      .catch((err) => !cancelled && setState({ key: id, profile: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id])

  if (state.key !== id) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page">
        <p className="form-error">{state.error}</p>
        <Link to="/rankings">랭킹으로</Link>
      </div>
    )
  }

  const u = state.profile
  const today = toIsoDate(new Date())
  const stats = [
    { key: 'verify', label: '이번 달 인증', value: `${u.monthVerify}회`, icon: <CheckIcon /> },
    { key: 'streak', label: '최장 연속', value: `${u.maxStreak}일`, icon: <FlameIcon /> },
    {
      key: 'rate',
      label: '누적 성공률',
      value: u.successRate == null ? '—' : `${u.successRate.toFixed(1)}%`,
      empty: u.successRate == null,
      icon: <RateIcon />,
    },
    { key: 'done', label: '완주한 챌린지', value: `${u.completedCount}개`, icon: <StarIcon /> },
  ]

  return (
    <div className="container page pf-page">
      <section className="pf-card">
        <div className="pf-head">
          <div className="pf-identity">
            {u.profileImageUrl ? (
              <img className="pf-avatar" src={u.profileImageUrl} alt="" />
            ) : (
              <span className="pf-avatar" aria-hidden="true">
                {u.nickname.slice(0, 1)}
              </span>
            )}
            <div className="pf-info">
              <h1 className="pf-name">
                {u.nickname}
                {u.mine && <small>나</small>}
              </h1>
              {u.bio && <p className="pf-bio">{u.bio}</p>}
              {u.joinedAt && <p className="pf-joined">{u.joinedAt.replaceAll('-', '.')} 가입</p>}
            </div>
          </div>
          {!u.mine && (
            <div className="pf-actions">
              <button type="button" className="pf-follow" disabled title="곧 열려요">
                + 팔로우
              </button>
              <button type="button" className="pf-msg" disabled title="곧 열려요">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                  <path d="M4 5h16v11H8l-4 4V5z" strokeWidth="1.8" strokeLinejoin="round" />
                </svg>
                메시지
              </button>
            </div>
          )}
        </div>

        <ul className="pf-stats">
          {stats.map((s) => (
            <li key={s.key} className={`pf-stat is-${s.key}`}>
              <span className="pf-stat-icon" aria-hidden="true">
                {s.icon}
              </span>
              <strong className={s.empty ? 'is-empty' : undefined}>{s.value}</strong>
              <span>{s.label}</span>
            </li>
          ))}
        </ul>
      </section>

      <h2 className="pf-section-title">참여 중인 챌린지</h2>
      {u.challenges.length === 0 ? (
        <p className="muted">지금 참여 중인 공개 챌린지가 없어요.</p>
      ) : (
        <ul className="pf-list">
          {u.challenges.map((c) => {
            const day = daysBetween(c.startDate, today) + 1
            const percent = Math.min(100, Math.round((day / c.totalDays) * 100))
            return (
              <li key={c.id} className={`pf-row cat-${c.category.id}`}>
                <Link to={`/challenges/${c.id}`} className="pf-row-link">
                  <span className="pf-row-icon">
                    <CategoryIcon id={c.category.id} size={18} strokeWidth={1.8} />
                  </span>
                  <span className="pf-row-body">
                    <span className="pf-row-title">{c.title}</span>
                    <span className="pf-row-meta">
                      {c.inProgress ? (
                        <>
                          <span className="pf-tag is-live">진행 중</span>
                          {day}일째 / {c.totalDays}일
                        </>
                      ) : (
                        <>
                          <span className="pf-tag">시작 전</span>D-{daysBetween(today, c.startDate)}
                        </>
                      )}
                      {' · '}
                      {MODE_LABEL[c.mode]}
                    </span>
                    {c.inProgress && (
                      <span className="pf-row-bar" aria-hidden="true">
                        <span style={{ width: `${percent}%` }} />
                      </span>
                    )}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

function CheckIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
      <path d="M4 12l5 5L20 6" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function FlameIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24">
      <path d="M12 2c-3 4-7 8-7 12a7 7 0 0 0 14 0c0-4-4-8-7-12z" fill="currentColor" />
    </svg>
  )
}

function RateIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor">
      <circle cx="12" cy="12" r="8" strokeWidth="2" />
      <path d="M9 12h6" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function StarIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24">
      <path d="M12 2l2.4 6.6L21 9l-5.4 4.4L17.4 21 12 17l-5.4 4 1.8-7.6L3 9l6.6-.4L12 2z" fill="currentColor" />
    </svg>
  )
}
