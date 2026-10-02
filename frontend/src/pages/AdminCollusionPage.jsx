import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { adminApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'

const FILTERS = [
  { value: 'OPEN', label: '확인 전' },
  { value: 'CONFIRMED', label: '담합으로 봄' },
  { value: 'DISMISSED', label: '문제없음' },
  { value: '', label: '전체' },
]
const STATUS = { OPEN: '확인 전', CONFIRMED: '담합으로 봄', DISMISSED: '문제없음' }

/**
 * 관리자 화면: 담합(짜고 지기) 의심 조합 (/admin/collusion). 관리자(role = ADMIN)만 들어올 수 있다.
 * 같은 두 사람이 작은 포인트 챌린지에서 여러 번 함께하고 늘 한쪽만 실패한 경우를 서버가 자정마다 찾아 올린다.
 * 여기서는 판단만 기록한다 (포인트를 되돌리거나 계정을 막지 않는다).
 */
export function AdminCollusionPage() {
  const { user } = useAuth()
  const [filter, setFilter] = useState('OPEN')
  const [state, setState] = useState({ key: null, items: [], error: '' })
  const [busy, setBusy] = useState(null)
  const isAdmin = user.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    adminApi
      .collusion(filter)
      .then((items) => !cancelled && setState({ key: filter, items, error: '' }))
      .catch((err) => !cancelled && setState({ key: filter, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [filter, isAdmin])

  if (!isAdmin) return <Navigate to="/" replace />

  async function decide(flag, status) {
    setBusy(flag.id)
    try {
      await (status === 'CONFIRMED' ? adminApi.confirmCollusion(flag.id) : adminApi.dismissCollusion(flag.id))
      setState((s) => ({
        ...s,
        error: '',
        items: filter ? s.items.filter((f) => f.id !== flag.id) : s.items.map((f) => (f.id === flag.id ? { ...f, status } : f)),
      }))
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">관리자 · 담합 의심</h1>
          <p className="page-sub">
            같은 두 사람이 작은 포인트 챌린지에서 여러 번 함께하고, 늘 한쪽만 실패한 조합이에요.
          </p>
        </div>
      </div>
      <AdminNav />

      <div className="cl-tabs" role="group" aria-label="확인 상태">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={`cl-tab${filter === f.value ? ' is-active' : ''}`}
            aria-pressed={filter === f.value}
            onClick={() => setFilter(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {state.key !== filter ? (
        <p className="muted">불러오는 중…</p>
      ) : state.items.length === 0 ? (
        !state.error && <p className="muted">{filter === 'OPEN' ? '확인할 조합이 없어요.' : '기록이 없어요.'}</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.items.map((f) => (
            <li key={f.id}>
              <div className="inquiry-head">
                <span
                  className={`inquiry-status${f.status === 'DISMISSED' ? ' is-answered' : ''}${f.status === 'CONFIRMED' ? ' is-rejected' : ''}`}
                >
                  {STATUS[f.status]}
                </span>
                <strong>
                  <Link to={`/users/${f.userAId}`}>{f.userANickname}</Link> ·{' '}
                  <Link to={`/users/${f.userBId}`}>{f.userBNickname}</Link>
                </strong>
                <small>{f.detectedAt.slice(0, 10)} 발견</small>
              </div>
              <dl className="adm-review-facts adm-collusion-facts">
                <div>
                  <dt>함께한 포인트 챌린지</dt>
                  <dd>{f.coMatchCount}번</dd>
                </div>
                <div>
                  <dt>한쪽만 실패한 비율</dt>
                  <dd>{Math.round(Number(f.score))}%</dd>
                </div>
                <div>
                  <dt>가장 최근에 함께한 챌린지</dt>
                  <dd>
                    <Link to={`/challenges/${f.challengeId}`}>{f.challengeTitle}</Link>
                  </dd>
                </div>
              </dl>
              {f.status === 'OPEN' && (
                <div className="adm-answer">
                  <div className="adm-answer-foot">
                    <button type="button" className="btn btn-outline btn-sm" disabled={busy === f.id} onClick={() => decide(f, 'DISMISSED')}>
                      문제없음
                    </button>
                    <button type="button" className="btn btn-dark btn-sm" disabled={busy === f.id} onClick={() => decide(f, 'CONFIRMED')}>
                      담합으로 기록
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
