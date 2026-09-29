import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { MODE_LABEL, dDayText, frequencyText, periodText, pointText, timeText } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'
import { Avatar } from '../components/UserMenu.jsx'

// 참가자 동그라미 색 (사진이 없으면 닉네임 첫 글자 + 이 색들을 돌아가며)
const TINTS = 4

export function ChallengeDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const { status } = useAuth()
  // 로그인 상태가 정해진 뒤에 불러와야 참여 여부(joined)가 맞게 온다.
  const [state, setState] = useState({ key: null, challenge: null, error: '' })
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadKey = `${id}|${status}`

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    challengeApi
      .get(id)
      .then((challenge) => !cancelled && setState({ key: loadKey, challenge, error: '' }))
      .catch((err) => !cancelled && setState({ key: loadKey, challenge: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id, status, loadKey])

  async function act(fn) {
    setBusy(true)
    setActionError('')
    try {
      const challenge = await fn(id)
      setState((s) => ({ ...s, challenge }))
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (state.key !== loadKey) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page detail-page">
        <p className="form-error">{state.error}</p>
        <Link to="/challenges">챌린지 목록으로</Link>
      </div>
    )
  }

  const c = state.challenge
  const isBet = c.mode === 'BET'

  return (
    <div className="container page detail-page">
      <Link to="/challenges" className="back-button">
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        챌린지 목록
      </Link>

      <article className="card dt-card">
        <div className="dt-top">
          <div className="dt-tags">
            <span className="dt-tag">
              <CategoryIcon id={c.category.id} size={13} strokeWidth={1.8} />
              {c.category.name}
            </span>
            <span className={`dt-tag ${isBet ? 'is-bet' : 'is-free'}`}>{MODE_LABEL[c.mode]}</span>
          </div>
          <span className="dt-dday">{dDayText(c)}</span>
        </div>

        <h1 className="dt-title">{c.title}</h1>
        <div className="dt-host">
          <Avatar size={24} />
          <span>개설자 {c.hostNickname}</span>
        </div>

        <dl className="mc-rows dt-rows">
          <div>
            <dt>기간</dt>
            <dd>{periodText(c)}</dd>
          </div>
          <div>
            <dt>인증</dt>
            <dd>
              {frequencyText(c)}
              {c.verifyFrom && ` · ${timeText(c.verifyFrom)} ~ ${timeText(c.verifyUntil)}에만`}
            </dd>
          </div>
          <div>
            <dt>참가 포인트</dt>
            <dd>
              {pointText(c)}
              {isBet && (c.partialRefund ? ' · 부분 성공 시 비례 환급' : ' · 성공 시 전액 환급')}
            </dd>
          </div>
          <div>
            <dt>참가자</dt>
            <dd>
              {c.participantCount} / {c.maxParticipants}명
            </dd>
          </div>
        </dl>

        <section className="dt-section">
          <h2>챌린지 소개</h2>
          <p className="dt-description">{c.description}</p>
        </section>

        <div className="dt-action">
          <JoinAction
            challenge={c}
            authed={status === 'authed'}
            from={location.pathname}
            busy={busy}
            onJoin={() => act(challengeApi.join)}
            onLeave={() => act(challengeApi.leave)}
          />
          {actionError && <p className="form-error">{actionError}</p>}
        </div>

        <section className="dt-section">
          <h2>참가자 {c.participants.length}명</h2>
          {c.participants.length === 0 ? (
            <p className="muted">아직 참가자가 없어요. 첫 번째로 참여해 보세요!</p>
          ) : (
            <ul className="dt-people">
              {c.participants.map((p, i) => (
                <li key={`${p.nickname}-${i}`}>
                  {p.profileImageUrl ? (
                    <Avatar src={p.profileImageUrl} size={34} />
                  ) : (
                    <span className={`dt-initial t${i % TINTS}`} aria-hidden="true">
                      {p.nickname.slice(0, 1)}
                    </span>
                  )}
                  <span>{p.nickname}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </article>
    </div>
  )
}

function JoinAction({ challenge: c, authed, from, busy, onJoin, onLeave }) {
  if (!authed) {
    return (
      <Link to="/login" state={{ from }} className="btn btn-dark btn-block dt-btn">
        로그인하고 참여하기
      </Link>
    )
  }
  if (c.joined) {
    return (
      <div className="joined">
        <p className="joined-badge">참여 중인 챌린지예요</p>
        {c.canLeave ? (
          <button type="button" className="btn btn-outline btn-block dt-btn" onClick={onLeave} disabled={busy}>
            참여 취소
          </button>
        ) : (
          <p className="dt-note">시작된 챌린지는 참여를 취소할 수 없어요.</p>
        )}
      </div>
    )
  }
  if (!c.recruiting) return <Disabled>모집이 끝났어요</Disabled>
  if (c.participantCount >= c.maxParticipants) return <Disabled>정원이 다 찼어요</Disabled>
  if (c.mode === 'BET') {
    return (
      <>
        <Disabled>포인트 챌린지 참여 준비 중</Disabled>
        <p className="dt-note">포인트 지갑이 열리면 참여할 수 있어요</p>
      </>
    )
  }
  return (
    <button type="button" className="btn btn-dark btn-block dt-btn" onClick={onJoin} disabled={busy}>
      {busy ? '참여하는 중…' : '참여하기'}
    </button>
  )
}

function Disabled({ children }) {
  return (
    <button type="button" className="btn btn-block dt-btn dt-btn-off" disabled>
      {children}
    </button>
  )
}
