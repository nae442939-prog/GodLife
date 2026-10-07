import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { adminApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'

const CATEGORY = { ACCOUNT: '계정', CHALLENGE: '챌린지 · 인증', POINT: '포인트', BUG: '오류 신고', ETC: '기타' }
const FILTERS = [
  { value: 'WAITING', label: '답변 대기' },
  { value: 'ANSWERED', label: '답변 완료' },
  { value: '', label: '전체' },
]

/**
 * 관리자 화면: 고객센터 1:1 문의 답변 (/admin/inquiries). 관리자(role = ADMIN)만 들어올 수 있다.
 * 답변 대기 문의가 오래된 것부터 나오고, 답변을 달면 회원의 [설정 → 고객센터 → 내 문의 내역]에 보인다.
 */
export function AdminInquiriesPage() {
  const { user } = useAuth()
  const [filter, setFilter] = useState('WAITING')
  const [state, setState] = useState({ key: null, items: [], error: '' })
  const isAdmin = user.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    adminApi
      .inquiries(filter)
      .then((items) => !cancelled && setState({ key: filter, items, error: '' }))
      .catch((err) => !cancelled && setState({ key: filter, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [filter, isAdmin])

  if (!isAdmin) return <Navigate to="/" replace />

  // 답변을 달면 목록에서 바로 반영한다 (답변 대기만 보는 중이면 그 문의는 빠진다)
  function onAnswered(id, answer) {
    setState((s) => ({
      ...s,
      items:
        filter === 'WAITING'
          ? s.items.filter((x) => x.inquiry.id !== id)
          : s.items.map((x) =>
              x.inquiry.id === id ? { ...x, inquiry: { ...x.inquiry, answer, status: 'ANSWERED' } } : x,
            ),
    }))
  }

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">관리자 · 1:1 문의</h1>
          <p className="page-sub">회원이 고객센터에 남긴 문의에 답변해요.</p>
        </div>
      </div>
      <AdminNav />

      <div className="cl-tabs" role="group" aria-label="문의 상태">
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
        !state.error && <p className="muted">{filter === 'WAITING' ? '답변을 기다리는 문의가 없어요.' : '문의가 없어요.'}</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.items.map((x) => (
            <InquiryRow key={x.inquiry.id} item={x} onAnswered={onAnswered} />
          ))}
        </ul>
      )}
    </div>
  )
}

function InquiryRow({ item, onAnswered }) {
  const q = item.inquiry
  const [text, setText] = useState(q.answer ?? '')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState({ kind: '', text: '' })

  async function submit(e) {
    e.preventDefault()
    const answer = text.trim()
    if (!answer) {
      setMessage({ kind: 'error', text: '답변을 입력해 주세요.' })
      return
    }
    setBusy(true)
    setMessage({ kind: '', text: '' })
    try {
      await adminApi.answerInquiry(q.id, answer)
      setMessage({ kind: 'ok', text: '답변을 저장했어요.' })
      onAnswered(q.id, answer)
    } catch (err) {
      setMessage({ kind: 'error', text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <li>
      <div className="inquiry-head">
        <span className={`inquiry-status${q.status === 'ANSWERED' ? ' is-answered' : ''}`}>
          {q.status === 'ANSWERED' ? '답변 완료' : '답변 대기'}
        </span>
        <strong>{q.title}</strong>
        <small>
          {CATEGORY[q.category]} · <Link to={`/users/${item.userId}`}>{item.nickname}</Link> ·{' '}
          {q.createdAt.slice(0, 16).replace('T', ' ')}
        </small>
      </div>
      <p className="inquiry-content">{q.content}</p>

      <form className="adm-answer" onSubmit={submit}>
        <label htmlFor={`answer-${q.id}`}>답변</label>
        <textarea
          id={`answer-${q.id}`}
          value={text}
          rows={3}
          maxLength={2000}
          placeholder="회원에게 보일 답변을 적어 주세요."
          onChange={(e) => setText(e.target.value)}
        />
        <div className="adm-answer-foot">
          {message.text && (
            <span className={message.kind === 'error' ? 'form-error' : 'settings-notice'} role="status">
              {message.text}
            </span>
          )}
          <button type="submit" className="btn btn-dark btn-sm" disabled={busy}>
            {busy ? '저장하는 중…' : q.status === 'ANSWERED' ? '답변 고치기' : '답변 달기'}
          </button>
        </div>
      </form>
    </li>
  )
}
