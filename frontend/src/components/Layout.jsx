import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { BadgeWatcher } from './Celebrations.jsx'
import { useAuth } from '../auth/useAuth.js'
import { Footer } from './Footer.jsx'
import { Header } from './Header.jsx'
import { PhoneTabBar } from './PhoneTabBar.jsx'
import { ON_PHONE } from './device.js'

/** 모든 페이지가 공유하는 뼈대: 헤더 + 본문 + 푸터. */
export function Layout() {
  const { user, status } = useAuth()
  const { pathname } = useLocation()

  // 소셜로 가입해 아직 휴대폰 인증을 안 한 회원은 인증부터 하게 한다. (계정당 1번호 정책)
  if (status === 'authed' && !user.phoneVerified && pathname !== '/verify-phone') {
    return <Navigate to="/verify-phone" replace />
  }

  return (
    <div className={`app${ON_PHONE ? ' is-phone' : ''}`}>
      <Header />
      <main className="site-main">
        <Outlet />
      </main>
      {ON_PHONE ? <PhoneTabBar /> : <Footer />}
      {status === 'authed' && <BadgeWatcher />}
    </div>
  )
}
