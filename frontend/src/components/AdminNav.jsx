import { NavLink } from 'react-router-dom'

const LINKS = [
  { to: '/admin/reviews', label: '인증 검토' },
  { to: '/admin/collusion', label: '담합 의심' },
  { to: '/admin/community', label: '커뮤니티 신고' },
  { to: '/admin/shop', label: '포인트 상점' },
  { to: '/admin/inquiries', label: '1:1 문의' },
]

/** 관리자 화면끼리 오가는 탭 */
export function AdminNav() {
  return (
    <nav className="adm-nav" aria-label="관리자 메뉴">
      {LINKS.map((l) => (
        <NavLink key={l.to} to={l.to} className={({ isActive }) => `adm-nav-link${isActive ? ' is-active' : ''}`}>
          {l.label}
        </NavLink>
      ))}
    </nav>
  )
}
