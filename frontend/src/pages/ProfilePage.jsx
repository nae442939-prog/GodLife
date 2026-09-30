import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { profileApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { MODE_LABEL, daysBetween, toIsoDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'

/**
 * 회원 프로필 (/users/:id). 랭킹·참가자 목록에서 사람을 누르면 온다.
 * 프로필과 기록을 카드 하나로, 참여 중인 공개 챌린지는 목록 카드 하나로 보여 준다.
 * [메시지]는 서로 팔로우(맞팔로우)했을 때만 대화방으로 가고, 아니면 왜 안 되는지 안내한다.
 */
export function ProfilePage() {
  const { id } = useParams()
  const location = useLocation()
  const { status } = useAuth()
  const [state, setState] = useState({ key: null, profile: null, error: '' })
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  // 맞팔로우가 아닌데 [메시지]를 누르면 띄우는 안내
  const [msgNotice, setMsgNotice] = useState(false)
  const navigate = useNavigate()
  // 로그인 상태가 정해진 뒤에 불러와야 '내가 팔로우 중인지'가 맞게 온다
  const loadKey = `${id}|${status}`

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    profileApi
      .get(id)
      .then((profile) => !cancelled && setState({ key: loadKey, profile, error: '' }))
      .catch((err) => !cancelled && setState({ key: loadKey, profile: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id, status, loadKey])

  async function toggleFollow() {
    const u = state.profile
    setBusy(true)
    setActionError('')
    try {
      await (u.following ? profileApi.unfollow(u.id) : profileApi.follow(u.id))
      setState((st) => ({
        ...st,
        profile: { ...st.profile, following: !u.following, followerCount: u.followerCount + (u.following ? -1 : 1) },
      }))
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function openMessage() {
    const u = state.profile
    if (status !== 'authed') {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    if (u.following && u.followsMe) {
      navigate(`/messages/${u.id}`)
      return
    }
    setMsgNotice(true)
  }

  if (state.key !== loadKey) return <p className="loading">불러오는 중…</p>
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
                {u.following && u.followsMe && <small className="is-mutual">맞팔로우</small>}
                {!u.following && u.followsMe && <small className="is-follows-me">나를 팔로우해요</small>}
              </h1>
              {u.bio && <p className="pf-bio">{u.bio}</p>}
              <p className="pf-joined">
                팔로워 <strong>{u.followerCount}</strong> · 팔로잉 <strong>{u.followingCount}</strong>
                {u.joinedAt && ` · ${u.joinedAt.replaceAll('-', '.')} 가입`}
              </p>
            </div>
          </div>
          {!u.mine && (
            <div className="pf-actions">
              {status !== 'authed' ? (
                <Link to="/login" state={{ from: location.pathname }} className="pf-follow">
                  + 팔로우
                </Link>
              ) : (
                <button
                  type="button"
                  className={`pf-follow${u.following ? ' is-following' : ''}`}
                  onClick={toggleFollow}
                  disabled={busy}
                  aria-pressed={u.following}
                >
                  {u.following ? (
                    <>
                      <span className="pf-follow-on">팔로잉 ✓</span>
                      <span className="pf-follow-off">언팔로우</span>
                    </>
                  ) : (
                    '+ 팔로우'
                  )}
                </button>
              )}
              <button type="button" className="pf-msg" onClick={openMessage} aria-expanded={msgNotice}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                  <path d="M4 5h16v11H8l-4 4V5z" strokeWidth="1.8" strokeLinejoin="round" />
                </svg>
                메시지
              </button>
            </div>
          )}
        </div>

        {msgNotice && !(u.following && u.followsMe) && (
          <div className="pf-msg-notice" role="status">
            <span className="pf-msg-notice-icon" aria-hidden="true">
              💬
            </span>
            <div>
              <strong>메시지는 서로 팔로우한 친구끼리만 보낼 수 있어요</strong>
              <p>
                {u.following
                  ? `${u.nickname}님도 나를 팔로우하면 바로 메시지를 보낼 수 있어요. 조금만 기다려 주세요!`
                  : u.followsMe
                    ? `${u.nickname}님은 이미 나를 팔로우하고 있어요. 나도 팔로우하면 바로 대화할 수 있어요!`
                    : `먼저 ${u.nickname}님을 팔로우해 보세요. ${u.nickname}님도 나를 팔로우하면 대화할 수 있어요.`}
              </p>
            </div>
            <div className="pf-msg-notice-actions">
              {!u.following && (
                <button type="button" className="pf-follow" onClick={toggleFollow} disabled={busy}>
                  + 팔로우하기
                </button>
              )}
              <button type="button" className="pf-msg-close" onClick={() => setMsgNotice(false)} aria-label="안내 닫기">
                ✕
              </button>
            </div>
          </div>
        )}
        {actionError && <p className="form-error">{actionError}</p>}

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
