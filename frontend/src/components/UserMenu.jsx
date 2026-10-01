import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { messageApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'

// 프로필 사진이 없으면 기본 사람 아이콘을 보여준다.
export function Avatar({ src, size = 36 }) {
  return (
    <span className="avatar" style={{ width: size, height: size }} aria-hidden="true">
      {src ? (
        <img src={src} alt="" />
      ) : (
        <svg viewBox="0 0 24 24" width="62%" height="62%" fill="currentColor">
          <circle cx="12" cy="8" r="4.2" />
          <path d="M3.5 21c0-4.4 3.8-7.5 8.5-7.5s8.5 3.1 8.5 7.5z" />
        </svg>
      )}
    </span>
  )
}

export function UserMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const location = useLocation()
  // 안 읽은 메시지 수 + 받은 메시지 요청 수: 30초마다, 화면을 옮길 때마다 새로
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    let cancelled = false
    function load() {
      messageApi
        .unreadCount()
        .then((r) => !cancelled && setUnread(r.count + (r.requests ?? 0)))
        .catch(() => {}) // 보조 정보라 실패해도 메뉴는 그대로
    }
    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), 30_000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [location.pathname])

  // 바깥을 누르거나 Esc 를 누르면 닫는다.
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

  async function onLogout() {
    setOpen(false)
    await logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="user-menu" ref={rootRef}>
      <button
        type="button"
        className="user-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Avatar src={user.profileImageUrl} />
        {unread > 0 && <span className="user-menu-dot" aria-label={`새 메시지 ${unread}개`} />}
        <span className="user-menu-name">{user.nickname}</span>
        <span className="user-menu-caret" aria-hidden="true">
          ▾
        </span>
      </button>

      {open && (
        <div className="user-menu-panel" role="menu">
          <div className="user-menu-head">
            <Avatar src={user.profileImageUrl} size={44} />
            <div>
              <strong>{user.nickname}</strong>
              <small>{user.email}</small>
            </div>
          </div>
          <Link to="/me" role="menuitem" className="user-menu-item" onClick={() => setOpen(false)}>
            마이페이지
          </Link>
          <Link to="/messages" role="menuitem" className="user-menu-item" onClick={() => setOpen(false)}>
            메시지
            {unread > 0 && <span className="user-menu-badge">{unread > 99 ? '99+' : unread}</span>}
          </Link>
          <Link to="/wallet" role="menuitem" className="user-menu-item" onClick={() => setOpen(false)}>
            포인트 지갑
          </Link>
          <Link to="/settings" role="menuitem" className="user-menu-item" onClick={() => setOpen(false)}>
            설정
          </Link>
          <hr />
          <button type="button" role="menuitem" className="user-menu-item is-danger" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      )}
    </div>
  )
}
