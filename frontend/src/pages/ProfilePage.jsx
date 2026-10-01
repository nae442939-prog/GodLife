import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { profileApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { MODE_LABEL, daysBetween, toIsoDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'
import { BioHint, MyProfileActions, ProfileCard } from '../profile/ProfileCard.jsx'

/**
 * 회원 프로필 (/users/:id). 랭킹·참가자 목록에서 사람을 누르면 온다.
 * 프로필(사진 · 닉네임 · 칭호 · 한 줄 소개 · 뱃지)과 기록을 카드 하나로, 참여 중인 공개 챌린지는 목록 카드 하나로 보여 준다.
 * 내 프로필이면 팔로우·메시지 대신 [프로필 수정] [설정]이 나온다.
 * [메시지]는 항상 대화방으로 간다 (맞팔로우·같은 챌린지가 아니면 대화방에서 메시지 요청으로 보내진다).
 */
export function ProfilePage() {
  const { id } = useParams()
  const location = useLocation()
  const { status } = useAuth()
  const [state, setState] = useState({ key: null, profile: null, error: '' })
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
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
    navigate(`/messages/${u.id}`)
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
  return (
    <div className="container page pf-page">
      <ProfileCard
        u={u}
        error={actionError}
        bioHint={u.mine ? <BioHint /> : null}
        actions={
          u.mine ? (
            <MyProfileActions />
          ) : (
            <>
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
              <button type="button" className="pf-msg" onClick={openMessage}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                  <path d="M4 5h16v11H8l-4 4V5z" strokeWidth="1.8" strokeLinejoin="round" />
                </svg>
                메시지
              </button>
            </>
          )
        }
      />

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
