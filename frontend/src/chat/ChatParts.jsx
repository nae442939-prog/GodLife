import { useEffect, useId, useRef, useState } from 'react'

const REPORT_REASONS = [
  { value: 'ABUSE', label: '욕설 · 비방' },
  { value: 'SPAM', label: '스팸 · 광고' },
  { value: 'INAPPROPRIATE', label: '부적절한 내용' },
  { value: 'OTHER', label: '기타' },
]
const REASON_LABEL = Object.fromEntries(REPORT_REASONS.map((r) => [r.value, r.label]))
const MENU_HEIGHT = 140 // ⋯ 메뉴가 펼쳐졌을 때 대략 높이 (항목 3개)

/** 가운데 뜨는 창. 바깥(어두운 배경)을 누르거나 Esc 로 닫는다. */
export function Dialog({ title, onClose, children }) {
  const titleId = useId()
  const boxRef = useRef(null)

  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    boxRef.current?.querySelector('button, input, textarea')?.focus()
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="dlg-backdrop" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="dlg" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={boxRef}>
        <h2 id={titleId}>{title}</h2>
        {children}
      </div>
    </div>
  )
}

/** 예/아니오 확인 창 (차단 · 내보내기) */
export function ConfirmDialog({ title, body, confirmLabel, danger, busy, onConfirm, onClose }) {
  return (
    <Dialog title={title} onClose={onClose}>
      <p className="dlg-body">{body}</p>
      <div className="dlg-actions">
        <button type="button" className="btn btn-outline" onClick={onClose} disabled={busy}>
          취소
        </button>
        <button type="button" className={`btn ${danger ? 'dt-delete-confirm' : 'btn-dark'}`} onClick={onConfirm} disabled={busy}>
          {busy ? '처리 중…' : confirmLabel}
        </button>
      </div>
    </Dialog>
  )
}

/** 메시지 신고: 사유를 고르고(필수) 자세한 내용은 선택 */
export function ReportDialog({ message, busy, error, onSubmit, onClose }) {
  const [reason, setReason] = useState('')
  const [detail, setDetail] = useState('')

  return (
    <Dialog title="메시지 신고" onClose={onClose}>
      <p className="dlg-quote">
        <strong>{message.senderNickname}</strong> “{message.content}”
      </p>
      <fieldset className="dlg-reasons">
        <legend className="sr-only">신고 사유</legend>
        {REPORT_REASONS.map((r) => (
          <label key={r.value} className={`dlg-reason ${reason === r.value ? 'is-active' : ''}`}>
            <input
              type="radio"
              name="report-reason"
              value={r.value}
              checked={reason === r.value}
              onChange={() => setReason(r.value)}
              className="sr-only"
            />
            {r.label}
          </label>
        ))}
      </fieldset>
      <textarea
        className="dlg-textarea"
        rows={3}
        maxLength={300}
        value={detail}
        onChange={(e) => setDetail(e.target.value)}
        placeholder="자세한 내용 (선택) — 방장이 판단할 때 참고해요"
        aria-label="자세한 내용"
      />
      <p className="dlg-help">신고가 많이 쌓이면 방장에게 알려서 내보낼지 판단하게 해요. 신고한 사람은 알려지지 않아요.</p>
      {error && <p className="form-error">{error}</p>}
      <div className="dlg-actions">
        <button type="button" className="btn btn-outline" onClick={onClose} disabled={busy}>
          취소
        </button>
        <button
          type="button"
          className="btn dt-delete-confirm"
          disabled={!reason || busy}
          onClick={() => onSubmit(reason, detail.trim() || null)}
        >
          {busy ? '보내는 중…' : '신고하기'}
        </button>
      </div>
    </Dialog>
  )
}

/** 남의 메시지 옆 ⋯ 메뉴: 신고 · 차단 (방장이면 내보내기도) */
export function MessageMenu({ isHost, onReport, onBlock, onKick }) {
  const [open, setOpen] = useState(false)
  const [up, setUp] = useState(false)
  const rootRef = useRef(null)

  function toggle() {
    if (!open) {
      // 채팅창 아래쪽 공간이 모자라면(맨 아래 메시지) 메뉴를 위로 연다 — 채팅창 밖으로 잘리지 않게
      const button = rootRef.current.getBoundingClientRect()
      const box = rootRef.current.closest('.chat-list')?.getBoundingClientRect()
      setUp(Boolean(box) && box.bottom - button.bottom < MENU_HEIGHT)
    }
    setOpen((v) => !v)
  }

  useEffect(() => {
    if (!open) return
    function onPointerDown(e) {
      if (!rootRef.current?.contains(e.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function choose(fn) {
    setOpen(false)
    fn()
  }

  return (
    <span className={`msg-menu ${open ? 'is-open' : ''}`} ref={rootRef}>
      <button
        type="button"
        className="msg-menu-btn"
        aria-label="메시지 메뉴"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={toggle}
      >
        ⋯
      </button>
      {open && (
        <span className={`msg-menu-panel ${up ? 'is-up' : ''}`} role="menu">
          <button type="button" role="menuitem" onClick={() => choose(onReport)}>
            신고하기
          </button>
          <button type="button" role="menuitem" onClick={() => choose(onBlock)}>
            차단하기
          </button>
          {isHost && (
            <button type="button" role="menuitem" className="is-danger" onClick={() => choose(onKick)}>
              내보내기
            </button>
          )}
        </span>
      )}
    </span>
  )
}

/** 채팅방 맨 위 고정 공지. 방장은 바로 고치거나 내릴 수 있다. */
export function NoticeBar({ notice, isHost, busy, onSave }) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')

  if (!notice && !isHost) return null

  if (editing) {
    return (
      <div className="notice-bar is-editing">
        <textarea
          rows={2}
          maxLength={300}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="참가자 모두에게 보일 공지를 적어 주세요 (300자까지)"
          aria-label="공지 내용"
          autoFocus
        />
        <div className="notice-actions">
          {notice && (
            <button
              type="button"
              className="link-button is-muted"
              disabled={busy}
              onClick={async () => {
                await onSave('')
                setEditing(false)
              }}
            >
              공지 내리기
            </button>
          )}
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(false)} disabled={busy}>
            취소
          </button>
          <button
            type="button"
            className="btn btn-dark btn-sm"
            disabled={busy || !draft.trim()}
            onClick={async () => {
              await onSave(draft)
              setEditing(false)
            }}
          >
            {busy ? '올리는 중…' : '공지 올리기'}
          </button>
        </div>
      </div>
    )
  }

  if (!notice) {
    return (
      <div className="notice-bar is-empty">
        <button
          type="button"
          className="link-button is-muted"
          onClick={() => {
            setDraft('')
            setEditing(true)
          }}
        >
          📢 공지 올리기 (방장)
        </button>
      </div>
    )
  }

  return (
    <div className="notice-bar">
      <span className="notice-icon" aria-hidden="true">
        📢
      </span>
      <p className="notice-text">
        <span className="sr-only">공지: </span>
        {notice}
      </p>
      {isHost && (
        <button
          type="button"
          className="link-button is-muted notice-edit"
          onClick={() => {
            setDraft(notice)
            setEditing(true)
          }}
        >
          수정
        </button>
      )}
    </div>
  )
}

/** 방장 전용: 신고가 많이 쌓인 참가자. 사유를 보고 내보내거나 넘긴다. */
export function ReportAlerts({ alerts, busy, onKick, onDismiss }) {
  if (!alerts.length) return null
  return (
    <section className="report-alerts" aria-label="신고 알림">
      <p className="report-alerts-title">⚠️ 신고가 많이 쌓인 참가자가 있어요. 사유를 보고 판단해 주세요.</p>
      {alerts.map((a) => (
        <div key={a.userId} className="report-alert">
          <div>
            <strong>{a.nickname}</strong>님 · 신고 {a.reportCount}건
            <span className="report-reasons">
              {Object.entries(a.reasons)
                .map(([reason, count]) => `${REASON_LABEL[reason] ?? reason} ${count}`)
                .join(' · ')}
            </span>
            {a.recentDetails.length > 0 && (
              <ul className="report-details">
                {a.recentDetails.map((d, i) => (
                  <li key={i}>“{d}”</li>
                ))}
              </ul>
            )}
          </div>
          <div className="report-alert-actions">
            <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => onDismiss(a)}>
              넘기기
            </button>
            <button type="button" className="btn btn-sm dt-delete-confirm" disabled={busy} onClick={() => onKick(a)}>
              내보내기
            </button>
          </div>
        </div>
      ))}
    </section>
  )
}
