import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { verificationApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'

/**
 * 헤더 메가 메뉴: 메뉴에 마우스를 올리거나(키보드 포커스) 헤더 바로 아래로 화면 전체 폭 패널이 내려온다.
 * 왼쪽은 링크 목록(links), 구분선 오른쪽은 aside(선택)를 둔다.
 */
export function MegaMenu({ label, to, links, aside }) {
  const [open, setOpen] = useState(false)
  const boxRef = useRef(null)
  const location = useLocation()
  const [lastPath, setLastPath] = useState(location.pathname)

  // 다른 화면으로 이동하면 닫는다 (패널 안 링크를 눌렀을 때)
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return
    function onKeyDown(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  return (
    <div
      ref={boxRef}
      className={`menu-item${open ? ' is-open' : ''}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={(e) => !boxRef.current.contains(e.relatedTarget) && setOpen(false)}
    >
      <NavLink to={to} aria-haspopup="true" aria-expanded={open}>
        {label}
      </NavLink>

      <div className="mega" aria-hidden={!open}>
        <div className="mega-inner">
          <ul className="mega-links">
            {links.map((link) => (
              <li key={link.to}>
                <Link to={link.to} className="mega-link" tabIndex={open ? 0 : -1}>
                  <span className="mega-dot" aria-hidden="true" />
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
          {aside && <div className="mega-aside">{aside(open)}</div>}
        </div>
      </div>
    </div>
  )
}

/** 챌린지 메가 메뉴 오른쪽: 로그인했으면 오늘 인증할 챌린지 (패널이 열릴 때 불러온다) */
export function TodayChallenges({ open }) {
  const { status } = useAuth()
  const [items, setItems] = useState(null)

  useEffect(() => {
    if (!open || status !== 'authed') return
    let cancelled = false
    verificationApi
      .myChallenges()
      .then((list) => !cancelled && setItems(list.filter((c) => c.joined && c.inProgress)))
      .catch(() => !cancelled && setItems([]))
    return () => {
      cancelled = true
    }
  }, [open, status])

  if (status !== 'authed') {
    return (
      <div className="mega-today">
        <p className="mega-today-title">오늘의 인증</p>
        <p className="mega-today-empty">
          <Link to="/login" tabIndex={open ? 0 : -1}>
            로그인
          </Link>
          하면 참여 중인 챌린지를 여기서 바로 인증할 수 있어요.
        </p>
      </div>
    )
  }

  return (
    <div className="mega-today">
      <p className="mega-today-title">오늘 인증할 챌린지</p>
      {items === null ? (
        <p className="mega-today-empty">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="mega-today-empty">진행 중인 챌린지가 없어요.</p>
      ) : (
        <ul>
          {items.slice(0, 3).map((c) => (
            <li key={c.id}>
              <Link to={`/challenges/${c.id}`} className="mega-today-name" tabIndex={open ? 0 : -1}>
                {c.title}
              </Link>
              {c.verifyState === 'OPEN' ? (
                <Link to={`/challenges/${c.id}/verify`} className="mega-today-go" tabIndex={open ? 0 : -1}>
                  인증하기
                </Link>
              ) : c.verifyState === 'DONE_TODAY' ? (
                <span className="mega-today-done">✓ 완료</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
