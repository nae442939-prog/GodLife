import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { ChallengeDetailView } from '../challenge/ChallengeDetailView.jsx'

/**
 * 초대 링크(/challenges/join/:code)로 들어온 화면. 비공개 챌린지도 이 코드로 보여 주고 참여시킨다.
 * 로그인 안 했으면 로그인 후 이 주소로 돌아온다.
 */
export function ChallengeInvitePage() {
  const { code } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { status } = useAuth()
  const [state, setState] = useState({ key: null, challenge: null, error: '' })
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadKey = `${code}|${status}`

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    challengeApi
      .getByInvite(code)
      .then((challenge) => !cancelled && setState({ key: loadKey, challenge, error: '' }))
      .catch((err) => !cancelled && setState({ key: loadKey, challenge: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [code, status, loadKey])

  async function join() {
    setBusy(true)
    setActionError('')
    try {
      const joined = await challengeApi.joinByInvite(code)
      navigate(`/challenges/${joined.id}`, { replace: true })
    } catch (err) {
      setActionError(err.message)
      setBusy(false)
    }
  }

  if (state.key !== loadKey) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page detail-page">
        <div className="invite-missing card">
          <h1>초대 링크를 열 수 없어요</h1>
          <p>{state.error}</p>
          <Link to="/challenges" className="btn btn-dark-outline">
            다른 챌린지 둘러보기
          </Link>
        </div>
      </div>
    )
  }

  return (
    <ChallengeDetailView
      challenge={state.challenge}
      authed={status === 'authed'}
      from={location.pathname}
      busy={busy}
      actionError={actionError}
      onJoin={join}
      joinLabel="초대받은 챌린지 참여하기"
      notice={
        <p className="invite-notice">
          <strong>{state.challenge.hostNickname}</strong>님이 챌린지에 초대했어요
        </p>
      }
    />
  )
}
