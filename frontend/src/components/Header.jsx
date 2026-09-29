import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { UserMenu } from './UserMenu.jsx'

// 아직 만들지 않은 메뉴는 링크 대신 "준비 중"으로 표시한다. 만들어지면 to 만 채우면 된다.
const MENU = [
  { label: '챌린지', to: '/challenges' },
  { label: '갓생기록', to: null },
  { label: '랭킹', to: null },
  { label: '포인트 상점', to: null },
  { label: '커뮤니티', to: null },
]

export function Header() {
  const { status } = useAuth()

  return (
    <header className="site-header">
      <div className="container header-inner">
        <Link to="/" className="logo" aria-label="갓생살기 홈">
          <span className="logo-mark" aria-hidden="true" />
          갓생살기
        </Link>

        <nav className="menu" aria-label="주 메뉴">
          {MENU.map((item) =>
            item.to ? (
              <NavLink key={item.label} to={item.to}>
                {item.label}
              </NavLink>
            ) : (
              <span key={item.label} className="menu-soon" title="준비 중입니다">
                {item.label}
                <small>준비 중</small>
              </span>
            ),
          )}
        </nav>

        <div className="header-auth">
          {status === 'loading' ? null : status === 'authed' ? (
            <UserMenu />
          ) : (
            // 회원가입은 로그인 화면 아래 링크로 들어간다.
            <Link to="/login" className="btn btn-ghost btn-sm">
              로그인
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
