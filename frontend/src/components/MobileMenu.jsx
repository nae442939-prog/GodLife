import { useEffect, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'

/**
 * 좁은 화면(760px 이하)의 주 메뉴. 가로 메뉴가 숨는 대신 헤더에 ☰ 단추가 생기고,
 * 누르면 헤더 아래로 메뉴가 펼쳐진다 — 큰 화면의 메가 메뉴에 있는 하위 항목까지 한 번에 보여 준다.
 * 항목을 누르거나, 바깥을 누르거나, Esc 를 누르면 닫힌다.
 * @param menu  [{ label, to, mega }] (Header 의 MENU)
 * @param links mega 이름 → [{ label, to }] (Header 의 메가 메뉴 항목)
 */
export function MobileMenu({ menu, links }) {
  const [open, setOpen] = useState(false)
  const close = () => setOpen(false)

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <div className="mnav">
      <button
        type="button"
        className={`mnav-toggle${open ? ' is-open' : ''}`}
        aria-expanded={open}
        aria-controls="mnav-panel"
        aria-label={open ? '메뉴 닫기' : '메뉴 열기'}
        onClick={() => setOpen((v) => !v)}
      >
        <span aria-hidden="true" />
        <span aria-hidden="true" />
        <span aria-hidden="true" />
      </button>

      {open && (
        <>
          <div className="mnav-backdrop" onClick={close} aria-hidden="true" />
          <nav id="mnav-panel" className="mnav-panel" aria-label="주 메뉴">
            {menu
              .filter((item) => item.to)
              .map((item) => (
                <div key={item.label} className="mnav-group">
                  <NavLink to={item.to} className="mnav-title" onClick={close}>
                    {item.label}
                  </NavLink>
                  {item.mega && (
                    <ul>
                      {links[item.mega].map((l) => (
                        <li key={l.to}>
                          <Link to={l.to} onClick={close}>
                            {l.label}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
          </nav>
        </>
      )}
    </div>
  )
}
