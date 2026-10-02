import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { adminApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'

const FILTERS = [
  { value: 'OPEN', label: '검토 대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '거절' },
  { value: '', label: '전체' },
]
const REASON = {
  LOW_CONFIDENCE: 'AI 가 확신하지 못함',
  DUPLICATE_SUSPECT: '예전 사진과 거의 같음',
  REPORTED: '신고됨',
}
const STATUS = { OPEN: '검토 대기', APPROVED: '승인', REJECTED: '거절' }
// AI 모델의 라벨 (categories.ai_label)
const LABEL = { exercise: '운동', study: '공부', reading: '독서', cooking: '요리', other: '기타' }

const percent = (value) => (value == null ? null : `${Math.round(Number(value) * 100)}%`)

/**
 * 관리자 화면: AI 가 애매하다고 넘긴 인증 사진과 참가자가 신고한 인증 사진 검토 (/admin/reviews).
 * 관리자(role = ADMIN)만 들어올 수 있다.
 * 검토 중인 인증은 일단 인정된 상태다. 승인하면 그대로 인정되고, 거절하면 그 인증이 취소되고 회원에게 알림이 간다.
 */
export function AdminReviewsPage() {
  const { user } = useAuth()
  const [filter, setFilter] = useState('OPEN')
  const [state, setState] = useState({ key: null, items: [], error: '' })
  const isAdmin = user.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    adminApi
      .reviews(filter)
      .then((items) => !cancelled && setState({ key: filter, items, error: '' }))
      .catch((err) => !cancelled && setState({ key: filter, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [filter, isAdmin])

  if (!isAdmin) return <Navigate to="/" replace />

  // 처리하면 목록에서 바로 반영한다 (검토 대기만 보는 중이면 그 건은 빠진다)
  function onDecided(id, status, memo) {
    setState((s) => ({
      ...s,
      items:
        filter === 'OPEN'
          ? s.items.filter((r) => r.id !== id)
          : s.items.map((r) => (r.id === id ? { ...r, status, memo } : r)),
    }))
  }

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">관리자 · 인증 검토</h1>
          <p className="page-sub">AI 가 판단하기 애매했거나 참가자가 신고한 인증 사진을 직접 보고 승인하거나 거절해요.</p>
        </div>
      </div>
      <AdminNav />

      <div className="cl-tabs" role="group" aria-label="검토 상태">
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
        !state.error && <p className="muted">{filter === 'OPEN' ? '검토를 기다리는 인증이 없어요.' : '검토한 인증이 없어요.'}</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.items.map((r) => (
            <ReviewRow key={r.id} review={r} onDecided={onDecided} />
          ))}
        </ul>
      )}
    </div>
  )
}

function ReviewRow({ review: r, onDecided }) {
  const [memo, setMemo] = useState(r.memo ?? '')
  const [busy, setBusy] = useState(false)
  const [confirming, setConfirming] = useState(false) // 거절은 한 번 더 눌러야 한다
  const [error, setError] = useState('')
  const open = r.status === 'OPEN'

  async function decide(status) {
    setBusy(true)
    setError('')
    try {
      const text = memo.trim()
      await (status === 'APPROVED' ? adminApi.approveReview(r.id, text) : adminApi.rejectReview(r.id, text))
      onDecided(r.id, status, text)
    } catch (err) {
      setError(err.message)
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <li>
      <div className="inquiry-head">
        <span className={`inquiry-status${r.status === 'APPROVED' ? ' is-answered' : ''}${r.status === 'REJECTED' ? ' is-rejected' : ''}`}>
          {STATUS[r.status]}
        </span>
        <strong>
          <Link to={`/challenges/${r.challengeId}`}>{r.challengeTitle}</Link>
        </strong>
        <small>
          {r.categoryName} · <Link to={`/users/${r.userId}`}>{r.nickname}</Link> · {r.verifyDate}
        </small>
      </div>

      <div className="adm-review">
        <figure>
          <ReviewPhoto verificationId={r.verificationId} alt="검토할 인증 사진" />
          <figcaption>이번 인증 사진</figcaption>
        </figure>
        {r.reason === 'DUPLICATE_SUSPECT' && r.duplicateOfId && (
          <figure>
            <ReviewPhoto verificationId={r.duplicateOfId} alt="가장 닮은 예전 인증 사진" />
            <figcaption>가장 닮은 예전 사진</figcaption>
          </figure>
        )}
        <dl className="adm-review-facts">
          <div>
            <dt>넘어온 이유</dt>
            <dd>{REASON[r.reason]}</dd>
          </div>
          {r.predictedLabel && (
            <div>
              <dt>AI 가 본 것</dt>
              <dd>
                {LABEL[r.predictedLabel] ?? r.predictedLabel} ({percent(r.confidence)})
                {r.predictedLabel !== r.expectedLabel && r.expectedLabel !== 'other' && (
                  <span className="adm-review-miss"> · 챌린지는 {r.categoryName}</span>
                )}
              </dd>
            </div>
          )}
          {r.maxSimilarity != null && (
            <div>
              <dt>예전 사진과 닮은 정도</dt>
              <dd>{percent(r.maxSimilarity)}</dd>
            </div>
          )}
          {r.reports.length > 0 && (
            <div>
              <dt>들어온 신고 {r.reports.length}건</dt>
              <dd>
                <ul className="adm-reports">
                  {r.reports.map((report) => (
                    <li key={report.reporterId}>
                      <Link to={`/users/${report.reporterId}`}>{report.nickname}</Link> {report.reason}
                    </li>
                  ))}
                </ul>
              </dd>
            </div>
          )}
          {!open && r.memo && (
            <div>
              <dt>메모</dt>
              <dd>{r.memo}</dd>
            </div>
          )}
        </dl>
      </div>

      {open && (
        <div className="adm-answer">
          <label htmlFor={`memo-${r.id}`}>메모 (거절하면 회원의 거절 이유로 남아요)</label>
          <input
            id={`memo-${r.id}`}
            className="adm-memo"
            type="text"
            value={memo}
            maxLength={100}
            placeholder="예: 운동 사진이 아니에요"
            onChange={(e) => setMemo(e.target.value)}
          />
          <div className="adm-answer-foot">
            {error && (
              <span className="form-error" role="status">
                {error}
              </span>
            )}
            {confirming ? (
              <>
                <span className="adm-confirm">이 인증을 취소할까요? 회원에게 알림이 가요.</span>
                <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => setConfirming(false)}>
                  아니요
                </button>
                <button type="button" className="btn btn-danger btn-sm" disabled={busy} onClick={() => decide('REJECTED')}>
                  {busy ? '처리하는 중…' : '거절하기'}
                </button>
              </>
            ) : (
              <>
                <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => setConfirming(true)}>
                  거절
                </button>
                <button type="button" className="btn btn-dark btn-sm" disabled={busy} onClick={() => decide('APPROVED')}>
                  {busy ? '처리하는 중…' : '승인'}
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </li>
  )
}

/** 검토할 사진. 관리자만 받을 수 있어 토큰을 붙여 받은 뒤 보여 준다. */
function ReviewPhoto({ verificationId, alt }) {
  const [state, setState] = useState({ src: null, failed: false })

  useEffect(() => {
    let cancelled = false
    let url = null
    adminApi
      .reviewImageBlob(verificationId)
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setState({ src: url, failed: false })
      })
      .catch(() => !cancelled && setState({ src: null, failed: true }))
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [verificationId])

  if (state.failed) return <span className="adm-review-photo is-failed">사진을 불러오지 못했어요</span>
  if (!state.src) return <span className="adm-review-photo is-loading" aria-label="사진 불러오는 중" />
  return (
    <a href={state.src} target="_blank" rel="noreferrer" className="adm-review-photo">
      <img src={state.src} alt={alt} />
    </a>
  )
}
