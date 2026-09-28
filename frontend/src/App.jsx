import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout.jsx'
import { RequireAuth } from './components/RequireAuth.jsx'
import { HomePage } from './pages/HomePage.jsx'
import { LoginPage } from './pages/LoginPage.jsx'
import { MyPage } from './pages/MyPage.jsx'
import { SignupPage } from './pages/SignupPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* 누구나 볼 수 있는 화면 */}
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />

        {/* 로그인해야 볼 수 있는 화면 */}
        <Route element={<RequireAuth />}>
          <Route path="/me" element={<MyPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
