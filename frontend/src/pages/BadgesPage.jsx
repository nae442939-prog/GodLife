import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { profileApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { BADGE_EMOJI } from '../profile/badges.js'

// 묶음 순서와 이름
const GROUPS = [
  { key: 'VERIFY', title: '인증 횟수' },
  { key: 'STREAK', title: '연속 인증' },
  { key: 'FINISH', title: '챌린지 완주' },
  { key: 'ACTIVITY', title: '활동' },
]

/**
 * 뱃지 전체 보기 (/me/badges): 마이페이지의 뱃지 카드에서 온다.
 * 15개를 묶음별로 보여 주고, 딴 것은 또렷하게, 아직인 것은 흐리게 + 얼마나 왔는지(진행 막대).
 */
export function BadgesPage() {
  const { user } = useAuth()
  const [state, setState] = useState({ badges: null, error: '' })

  useEffect(() => {
    let cancelled = false
    profileApi
      .get(user.id)
      .then((p) => !cancelled && setState({ badges: p.badges, error: '' }))
      .catch((err) => !cancelled && setState({ badges: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [user.id])

  const badges = state.badges
  const earned = badges ? badges.filter((b) => b.earned).length : 0

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">뱃지</h1>
          <p className="page-sub">꾸준히 인증하고 챌린지를 완주하면 뱃지가 쌓여요.</p>
        </div>
        <Link to="/me" className="btn btn-dark-outline">
          마이페이지로
        </Link>
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {!badges ? (
        !state.error && <p className="muted">불러오는 중…</p>
      ) : (
        <>
          <div className="bg-summary">
            <strong>
              {earned}
              <small> / {badges.length}개</small>
            </strong>
            <div className="bg-bar" aria-hidden="true">
              <span style={{ width: `${(earned / badges.length) * 100}%` }} />
            </div>
            <p>
              {earned === badges.length
                ? '모든 뱃지를 모았어요! 🎉'
                : earned === 0
                  ? '첫 인증을 하면 첫 뱃지를 받아요.'
                  : `뱃지 ${badges.length - earned}개가 더 남았어요.`}
            </p>
          </div>

          {GROUPS.map((g) => {
            const list = badges.filter((b) => b.group === g.key)
            if (list.length === 0) return null
            return (
              <section key={g.key} className="bg-group">
                <h2>
                  {g.title}
                  <small>
                    {list.filter((b) => b.earned).length}/{list.length}
                  </small>
                </h2>
                <ul className="bg-grid">
                  {list.map((b) => (
                    <li key={b.code}>
                      <div className={`bg-card${b.earned ? ' is-earned' : ''}`}>
                        <span className="pf-badge-icon" aria-hidden="true">
                          {BADGE_EMOJI[b.code] ?? '🏅'}
                        </span>
                        <strong>{b.name}</strong>
                        <p>{b.description}</p>
                        <div className="bg-progress">
                          {!b.earned && (
                            <div className="bg-bar" aria-hidden="true">
                              <span style={{ width: `${(b.current / b.target) * 100}%` }} />
                            </div>
                          )}
                          <span className="bg-count">{b.earned ? '획득 ✓' : `${b.current} / ${b.target}`}</span>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            )
          })}
        </>
      )}
    </div>
  )
}
