import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout.jsx'
import { RequireAuth } from './components/RequireAuth.jsx'
import { FindIdPage } from './pages/FindIdPage.jsx'
import { FindPasswordPage } from './pages/FindPasswordPage.jsx'
import { ChallengeChatPage } from './pages/ChallengeChatPage.jsx'
import { ChallengeVerifyPage } from './pages/ChallengeVerifyPage.jsx'
import { ChallengeCreatePage } from './pages/ChallengeCreatePage.jsx'
import { ChallengeDetailPage } from './pages/ChallengeDetailPage.jsx'
import { ChallengeInvitePage } from './pages/ChallengeInvitePage.jsx'
import { ChallengeMinePage } from './pages/ChallengeMinePage.jsx'
import { WalletPage } from './pages/WalletPage.jsx'
import { DiaryPage } from './pages/DiaryPage.jsx'
import { AdminInquiriesPage } from './pages/AdminInquiriesPage.jsx'
import { BadgesPage } from './pages/BadgesPage.jsx'
import { RankingPage } from './pages/RankingPage.jsx'
import { RecordPage } from './pages/RecordPage.jsx'
import { ProfilePage } from './pages/ProfilePage.jsx'
import { MessagesPage } from './pages/MessagesPage.jsx'
import { MessageRoomPage } from './pages/MessageRoomPage.jsx'
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
        <Route path="/rankings" element={<RankingPage />} />
        <Route path="/users/:id" element={<ProfilePage />} />
        <Route path="/challenges/:id" element={<ChallengeDetailPage />} />
        <Route path="/challenges/join/:code" element={<ChallengeInvitePage />} />

        {/* 로그인해야 볼 수 있는 화면 */}
        <Route element={<RequireAuth />}>
          <Route path="/challenges/new" element={<ChallengeCreatePage />} />
          <Route path="/challenges/mine" element={<ChallengeMinePage />} />
          <Route path="/records" element={<DiaryPage />} />
          <Route path="/records/calendar" element={<RecordPage />} />
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/messages/:userId" element={<MessageRoomPage />} />
          <Route path="/challenges/:id/chat" element={<ChallengeChatPage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/me/badges" element={<BadgesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/admin/inquiries" element={<AdminInquiriesPage />} />
          <Route path="/verify-phone" element={<VerifyPhonePage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>

      {/* 인증 화면은 헤더·푸터 없이 전체 화면 (영상통화처럼) */}
      <Route element={<RequireAuth />}>
        <Route path="/challenges/:id/verify" element={<ChallengeVerifyPage />} />
      </Route>
    </Routes>
  )
}
