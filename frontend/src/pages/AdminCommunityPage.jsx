import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { adminApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'

const TYPE_LABEL = { POST: '글', COMMENT: '댓글' }

/**
 * 관리자 화면: 커뮤니티 신고 (/admin/community). 관리자(role = ADMIN)만 들어올 수 있다.
 * 같은 글 · 댓글에 온 신고는 한 줄로 묶여 온다. 가리면 커뮤니티에서 사라지고, 신고한 사람과 쓴 사람에게 알림이 간다.
 */
export function AdminCommunityPage() {
  const { user } = useAuth()
  const [state, setState] = useState({ loaded: false, items: [], error: '' })
  const [busy, setBusy] = useState(null)
  const isAdmin = user.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    adminApi
      .communityReports()
      .then((items) => !cancelled && setState({ loaded: true, items, error: '' }))
      .catch((err) => !cancelled && setState({ loaded: true, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [isAdmin])

  if (!isAdmin) return <Navigate to="/" replace />

  const keyOf = (r) => `${r.targetType}:${r.targetId}`

  async function handle(report, action) {
    setBusy(keyOf(report))
    try {
      await adminApi.handleCommunityReport(report.targetType, report.targetId, action)
      setState((s) => ({ ...s, error: '', items: s.items.filter((r) => keyOf(r) !== keyOf(report)) }))
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
          <h1 className="page-title">관리자 · 커뮤니티 신고</h1>
          <p className="page-sub">신고된 글과 댓글이에요. 가리면 커뮤니티에서 보이지 않게 돼요.</p>
        </div>
      </div>
      <AdminNav />

      {state.error && <p className="form-error">{state.error}</p>}
      {!state.loaded ? (
        <p className="muted">불러오는 중…</p>
      ) : state.items.length === 0 ? (
        !state.error && <p className="muted">확인할 신고가 없어요.</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.items.map((r) => (
            <li key={keyOf(r)}>
              <div className="inquiry-head">
                <span className="inquiry-status">
                  {TYPE_LABEL[r.targetType]} · 신고 {r.reportCount}건
                </span>
                <strong>{r.postId ? <Link to={`/community/${r.postId}`}>{r.title}</Link> : '(지워진 글)'}</strong>
                <small>
                  {r.authorNickname} · {r.firstReportedAt.slice(0, 10)} 첫 신고
                </small>
              </div>
              <p className="inquiry-content">{r.content}</p>
              <ul className="adm-reasons">
                {r.reasons.map((reason, i) => (
                  <li key={i}>{reason}</li>
                ))}
              </ul>
              <div className="adm-answer">
                <div className="adm-answer-foot">
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    disabled={busy === keyOf(r)}
                    onClick={() => handle(r, 'dismiss')}
                  >
                    문제없음
                  </button>
                  <button
                    type="button"
                    className="btn btn-dark btn-sm"
                    disabled={busy === keyOf(r)}
                    onClick={() => handle(r, 'hide')}
                  >
                    {TYPE_LABEL[r.targetType]} 가리기
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
