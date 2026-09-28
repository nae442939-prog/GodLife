import { Outlet } from 'react-router-dom'
import { Footer } from './Footer.jsx'
import { Header } from './Header.jsx'

/** 모든 페이지가 공유하는 뼈대: 헤더 + 본문 + 푸터. */
export function Layout() {
  return (
    <div className="app">
      <Header />
      <main className="site-main">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
