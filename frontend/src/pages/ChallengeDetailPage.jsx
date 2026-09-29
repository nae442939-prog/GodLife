import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { ChallengeDetailView } from '../challenge/ChallengeDetailView.jsx'

export function ChallengeDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
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

  async function remove() {
    setBusy(true)
    setActionError('')
    try {
      await challengeApi.remove(id)
      navigate('/challenges', { replace: true })
    } catch (err) {
      setActionError(err.message)
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

  return (
    <ChallengeDetailView
      challenge={state.challenge}
      authed={status === 'authed'}
      from={location.pathname}
      busy={busy}
      actionError={actionError}
      onJoin={() => act(challengeApi.join)}
      onLeave={() => act(challengeApi.leave)}
      onRegenerateInvite={() => act(challengeApi.regenerateInvite)}
      onDelete={remove}
    />
  )
}
