import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout.jsx'
import { RequireAuth } from './components/RequireAuth.jsx'
import { FindIdPage } from './pages/FindIdPage.jsx'
import { FindPasswordPage } from './pages/FindPasswordPage.jsx'
import { ChallengeCreatePage } from './pages/ChallengeCreatePage.jsx'
import { ChallengeDetailPage } from './pages/ChallengeDetailPage.jsx'
import { ChallengeListPage } from './pages/ChallengeListPage.jsx'
import { HomePage } from './pages/HomePage.jsx'
import { LoginPage } from './pages/LoginPage.jsx'
import { MyPage } from './pages/MyPage.jsx'
import { SettingsPage } from './pages/SettingsPage.jsx'
import { SignupPage } from './pages/SignupPage.jsx'
import { VerifyPhonePage } from './pages/VerifyPhonePage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* 누구나 볼 수 있는 화면 */}
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/find-id" element={<FindIdPage />} />
        <Route path="/find-password" element={<FindPasswordPage />} />
        <Route path="/challenges" element={<ChallengeListPage />} />
        <Route path="/challenges/:id" element={<ChallengeDetailPage />} />

        {/* 로그인해야 볼 수 있는 화면 */}
        <Route element={<RequireAuth />}>
          <Route path="/challenges/new" element={<ChallengeCreatePage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/verify-phone" element={<VerifyPhonePage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
