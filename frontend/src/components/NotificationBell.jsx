import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { notificationApi } from '../api/client.js'

const POLL_MS = 30_000
const TYPE_ICON = {
  VERIFY_REMINDER: '📸',
  SETTLEMENT: '🏁',
  FOLLOW: '👋',
  MESSAGE_REQUEST: '💬',
  INQUIRY_ANSWER: '📮',
  REPORT_ALERT: '🚨',
}

/**
 * 헤더의 종: 안 읽은 알림 수를 30초마다(그리고 화면을 옮길 때마다) 확인하고, 누르면 알림 창이 열린다.
 * 알림을 누르면 읽음으로 바꾸고 그 알림의 화면으로 간다. [모두 읽음]으로 한 번에 지울 수 있다.
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [count, setCount] = useState(0)
  const [state, setState] = useState({ items: null, error: '' })
  const rootRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    function load() {
      notificationApi
        .unreadCount()
        .then((r) => !cancelled && setCount(r.count))
        .catch(() => {}) // 보조 정보라 실패해도 헤더는 그대로
    }
    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [location.pathname])

  const loadList = useCallback(() => {
    notificationApi
      .list()
      .then((items) => {
        setState({ items, error: '' })
        setCount(items.filter((n) => !n.read).length)
      })
      .catch((err) => setState({ items: null, error: err.message }))
  }, [])

  // 바깥을 누르거나 Esc 를 누르면 닫는다
  useEffect(() => {
    if (!open) return
    function onPointerDown(e) {
      if (!rootRef.current?.contains(e.target)) setOpen(false)
    }
    function onKeyDown(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  function toggle() {
    if (!open) loadList()
    setOpen((v) => !v)
  }

  function openItem(n) {
    setOpen(false)
    if (!n.read) {
      notificationApi.read(n.id).catch(() => {})
      setCount((c) => Math.max(0, c - 1))
      setState((s) => ({ ...s, items: s.items?.map((x) => (x.id === n.id ? { ...x, read: true } : x)) ?? null }))
    }
    if (n.link) navigate(n.link)
  }

  async function readAll() {
    try {
      await notificationApi.readAll()
      setCount(0)
      setState((s) => ({ ...s, items: s.items?.map((x) => ({ ...x, read: true })) ?? null }))
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    }
  }

  const items = state.items

  return (
    <div className="noti" ref={rootRef}>
      <button
        type="button"
        className="noti-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={count > 0 ? `알림 ${count}개` : '알림'}
        onClick={toggle}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <path
            d="M12 3a5 5 0 0 0-5 5v3.5c0 1-.4 2-1.2 2.7L4 16h16l-1.8-1.8c-.8-.7-1.2-1.7-1.2-2.7V8a5 5 0 0 0-5-5z"
            strokeWidth="1.8"
            strokeLinejoin="round"
          />
          <path d="M9.5 19a2.5 2.5 0 0 0 5 0" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        {count > 0 && <span className="noti-count">{count > 99 ? '99+' : count}</span>}
      </button>

      {open && (
        <div className="noti-panel" role="dialog" aria-label="알림">
          <header className="noti-head">
            <strong>알림</strong>
            {items?.some((n) => !n.read) && (
              <button type="button" className="noti-readall" onClick={readAll}>
                모두 읽음
              </button>
            )}
          </header>

          {state.error && <p className="form-error noti-empty">{state.error}</p>}
          {!items ? (
            !state.error && <p className="muted noti-empty">불러오는 중…</p>
          ) : items.length === 0 ? (
            <p className="muted noti-empty">아직 온 알림이 없어요.</p>
          ) : (
            <ul className="noti-list">
              {items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`noti-item${n.read ? '' : ' is-unread'}`}
                    onClick={() => openItem(n)}
                  >
                    <span className="noti-icon" aria-hidden="true">
                      {TYPE_ICON[n.type] ?? '🔔'}
                    </span>
                    <span className="noti-text">
                      <strong>{n.title}</strong>
                      <span>{n.body}</span>
                      <time dateTime={n.sentAt}>{whenText(n.sentAt)}</time>
                    </span>
                    {!n.read && <span className="noti-dot" aria-label="안 읽음" />}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <Link to="/settings?tab=alarm" className="noti-foot" onClick={() => setOpen(false)}>
            알림 설정
          </Link>
        </div>
      )}
    </div>
  )
}

/** 방금 · N분 전 · N시간 전 · 어제 · M.D */
function whenText(iso) {
  const then = new Date(iso)
  const minutes = Math.floor((Date.now() - then.getTime()) / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  if (hours < 48) return '어제'
  return `${then.getMonth() + 1}.${then.getDate()}`
}
