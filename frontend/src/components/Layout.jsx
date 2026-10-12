import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { Footer } from './Footer.jsx'
import { Header } from './Header.jsx'
import { PhoneTabBar } from './PhoneTabBar.jsx'

// 폰에서는 앱 화면(/app/)이 기본이고, 웹 화면은 앱에 없는 곳(충전 · 상점 · 설정)만 보여 준다.
// 그때는 위 메뉴 · 푸터 대신 앱과 같은 아래 탭을 붙인다. (개발 서버에는 앱 화면이 없어 배포 빌드에서만)
const ON_PHONE = import.meta.env.PROD && /iphone|ipod|android.*mobile|windows phone/i.test(navigator.userAgent)

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
    </div>
  )
}
